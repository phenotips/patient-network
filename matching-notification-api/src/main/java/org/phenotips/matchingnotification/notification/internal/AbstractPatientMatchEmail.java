/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses/
 */
package org.phenotips.matchingnotification.notification.internal;

import org.phenotips.components.ComponentManagerRegistry;
import org.phenotips.data.Feature;
import org.phenotips.data.internal.PhenoTipsFeature;
import org.phenotips.data.similarity.PatientPhenotypeSimilarityView;
import org.phenotips.data.similarity.PatientPhenotypeSimilarityViewFactory;
import org.phenotips.data.similarity.phenotype.PhenotypesMap;
import org.phenotips.matchingnotification.match.PatientInMatch;
import org.phenotips.matchingnotification.match.PatientMatch;
import org.phenotips.matchingnotification.notification.PatientMatchEmail;

import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.mail.MailListener;
import org.xwiki.mail.MailSender;
import org.xwiki.mail.MailStatus;
import org.xwiki.mail.internal.SessionFactory;
import org.xwiki.mail.script.MailSenderScriptService;
import org.xwiki.mail.script.ScriptMimeMessage;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.script.service.ScriptService;
import org.xwiki.users.UserManager;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.inject.Provider;
import javax.mail.Address;
import javax.mail.BodyPart;
import javax.mail.Message;
import javax.mail.Message.RecipientType;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMultipart;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.xpn.xwiki.XWikiContext;

/**
 * An email that notifies about found matches.
 *
 * @version $Id$
 */
public abstract class AbstractPatientMatchEmail implements PatientMatchEmail
{
    protected static final Provider<XWikiContext> CONTEXT_PROVIDER;

    protected static final Logger LOGGER = LoggerFactory.getLogger(DefaultAdminPatientMatchEmail.class);

    protected static final UserManager USERMANAGER;

    private static final String EMAIL_CONTENT_KEY = "emailContent";

    private static final String EMAIL_CONTENT_TYPE_KEY = "contentType";

    private static final String EMAIL_RECIPIENTS_KEY = "recipients";

    private static final String EMAIL_RECIPIENTS_TO = "to";

    private static final String EMAIL_RECIPIENTS_FROM = "from";

    private static final String EMAIL_RECIPIENTS_CC = "cc";

    private static final String EMAIL_SUBJECT_KEY = "subject";

    private static final String EMAIL_PREFERRED_CONTENT_TYPE = "text/plain";

    /** This service is used for generating email content from an XML template. */
    private static final MailSenderScriptService MAIL_GENERATOR_SERVICE;

    /**
     * This service is used for sending emails. The service above can do that as well,
     * but it does permission checks and non-admin users can not send mail using that service.
     */
    private static final MailSender MAIL_SENDER_SERVICE;

    /** Needed for use by MAIL_SENDER_SERVICE. */
    private static final SessionFactory MAIL_SESSION_FACTORY;

    private static final DocumentReferenceResolver<String> REFERENCE_RESOLVER;

    private static final PatientPhenotypeSimilarityViewFactory PHENOTYPE_SIMILARITY_VIEW_FACTORY;

    protected ScriptMimeMessage mimeMessage;

    protected PatientInMatch subjectPatient;

    protected Collection<PatientMatch> matches;

    protected boolean sent;

    protected String customEmailText;

    protected String customEmailSubject;

    protected MailStatus mailStatus;

    static {
        MailSender mailSenderService = null;
        SessionFactory mailSessionFactory = null;
        MailSenderScriptService mailGeneratorService = null;
        Provider<XWikiContext> contextProvider = null;
        DocumentReferenceResolver<String> referenceResolver = null;
        PatientPhenotypeSimilarityViewFactory patientPhenotypeSimilarityViewFactory = null;
        UserManager userManager = null;
        try {
            ComponentManager ccm = ComponentManagerRegistry.getContextComponentManager();
            mailGeneratorService = ccm.getInstance(ScriptService.class, "mailsender");
            mailSenderService = ccm.getInstance(org.xwiki.mail.MailSender.class);
            contextProvider = ccm.getInstance(XWikiContext.TYPE_PROVIDER);
            referenceResolver = ccm.getInstance(DocumentReferenceResolver.TYPE_STRING, "current");
            patientPhenotypeSimilarityViewFactory = ccm.getInstance(PatientPhenotypeSimilarityViewFactory.class,
                "restricted");
            userManager = ccm.getInstance(UserManager.class);
            mailSessionFactory = ccm.getInstance(SessionFactory.class);
        } catch (ComponentLookupException e) {
            LOGGER.error("Error initializing mailService", e);
        }
        MAIL_SENDER_SERVICE = mailSenderService;
        MAIL_SESSION_FACTORY = mailSessionFactory;
        MAIL_GENERATOR_SERVICE = mailGeneratorService;
        CONTEXT_PROVIDER = contextProvider;
        REFERENCE_RESOLVER = referenceResolver;
        PHENOTYPE_SIMILARITY_VIEW_FACTORY = patientPhenotypeSimilarityViewFactory;
        USERMANAGER = userManager;
    }

    /**
     * Build a new email object for a list of matches. {@code matches} is expected to be non empty, and one of the
     * patients in every match should have same id and server id as {@code subjectPatientId}.
     *
     * @param subjectPatientId id of patient who is the subject of this email
     * @param subjectServerId id of the server that holds the subjectPatientId
     * @param matches list of matches that the email notifies of.
     * @param customEmailText (optional) custom text to be used for the email
     * @param customEmailSubject (optional) custom subject to be used for the email
     */
    public AbstractPatientMatchEmail(String subjectPatientId, String subjectServerId, Collection<PatientMatch> matches,
            String customEmailText, String customEmailSubject)
    {
        this.matches = matches;
        this.customEmailText = customEmailText;
        this.customEmailSubject = customEmailSubject;

        this.init(subjectPatientId, subjectServerId);

        this.createMimeMessage();
    }

    protected abstract String getEmailTemplate();

    /**
     * Creates a variables map specific to the selected email template.
     *
     * @return a map of key-value pairs to be used by the selected template
     */
    protected abstract Map<String, Object> createVelocityVariablesMap();

    /**
     * Having a separate init method allows derived classes to initialize their own variables
     * which are used in createMimeMessage().
     * @param subjectPatientId same as cobstructor
     * @param subjectServerId same as cobstructor
     */
    protected void init(String subjectPatientId, String subjectServerId)
    {
        String useServerId = StringUtils.isBlank(subjectServerId) ? null : subjectServerId;

        PatientMatch anyMatch = this.matches.iterator().next();

        if (anyMatch.isReference(subjectPatientId, useServerId)) {
            this.subjectPatient = anyMatch.getReference();
        } else if (anyMatch.isMatched(subjectPatientId, useServerId)) {
            this.subjectPatient = anyMatch.getMatched();
        } else {
            throw new IllegalArgumentException("A match does not contain subject patient");
        }
    }

    protected void createMimeMessage()
    {
        if (this.customEmailText == null) {
            DocumentReference templateReference = REFERENCE_RESOLVER.resolve(
                    getEmailTemplate(), PatientMatch.DATA_SPACE);

            this.mimeMessage = MAIL_GENERATOR_SERVICE
                    .createMessage("template", templateReference, this.createEmailParameters());

            if (this.mimeMessage == null) {
                LOGGER.error("Error while populating email template: [{}]",
                        MAIL_GENERATOR_SERVICE.getLastError().getMessage(), MAIL_GENERATOR_SERVICE.getLastError());
            }
        } else {
            this.mimeMessage = MAIL_GENERATOR_SERVICE.createMessage();
            try {
                this.mimeMessage.setContent(this.customEmailText, "text/plain");
            } catch (Exception ex) {
                LOGGER.error("Error while populating email with custom text [{}]: [{}]", this.customEmailText,
                        MAIL_GENERATOR_SERVICE.getLastError().getMessage(), MAIL_GENERATOR_SERVICE.getLastError());
            }
        }

        if (this.customEmailSubject != null) {
            this.mimeMessage.setSubject(this.customEmailSubject);
        }

        this.setFrom();
        this.setTo();
    }

    private Map<String, Object> createEmailParameters()
    {
        Map<String, Object> emailParameters = new HashMap<>();
        String language = CONTEXT_PROVIDER.get().getLocale().getLanguage();
        emailParameters.put("language", language);

        emailParameters.put("velocityVariables", this.createVelocityVariablesMap());

        return emailParameters;
    }

    protected void setFrom()
    {
        String serverName = CONTEXT_PROVIDER.get().getRequest().getServerName();
        if (StringUtils.isNotEmpty(serverName)) {
            InternetAddress from = new InternetAddress();
            from.setAddress("noreply@" + serverName);
            this.mimeMessage.setFrom(from);
        }
    }

    protected void setTo()
    {
        Collection<String> emails = this.subjectPatient.getEmails();
        for (String emailAddress : emails) {
            InternetAddress to = new InternetAddress();
            to.setAddress(emailAddress);
            this.mimeMessage.addRecipient(RecipientType.TO, to);
        }
        InternetAddress bcc = new InternetAddress();
        bcc.setAddress("qc@phenomecentral.org");
        this.mimeMessage.addRecipient(RecipientType.BCC, bcc);
    }

    @Override
    public Collection<PatientMatch> getMatches()
    {
        return this.matches;
    }

    @Override
    public boolean wasSent()
    {
        return this.sent;
    }

    @Override
    public MailStatus getStatus()
    {
        if (!wasSent()) {
            return null;
        } else {
            return this.mailStatus;
        }
    }

    @Override
    public void send()
    {
        try {
            this.sent = false;

            // parts of the code below are copied from MailSenderScriptService
            // including the "memory" value for MailListener (taken from MailSenderScriptService.getListener())
            ComponentManager ccm = ComponentManagerRegistry.getContextComponentManager();
            MailListener listener = ccm.getInstance(MailListener.class, "memory");
            Session session = MAIL_SESSION_FACTORY.create(Collections.<String, String>emptyMap());

            MAIL_SENDER_SERVICE.sendAsynchronously(Collections.singleton(this.mimeMessage), session, listener);

            listener.getMailStatusResult().waitTillProcessed(10000);

            if (listener.getMailStatusResult().getAllErrors().hasNext()) {
                this.mailStatus = listener.getMailStatusResult().getAllErrors().next();
                LOGGER.error("Error sending email: [{}] [{}]",
                        this.mailStatus.getErrorDescription(), this.mailStatus.getErrorSummary());
                return;
            }

            this.sent = true;
            this.mailStatus = listener.getMailStatusResult().getAll().next();
        } catch (Exception ex) {
            LOGGER.error("Error sending email: [{}]", ex.getMessage(), ex);
        }

    }

    @Override
    public String getSubjectPatientId()
    {
        return this.subjectPatient.getPatientId();
    }

    protected JSONArray getFeatureMatchesJSON(PatientMatch match)
    {
        // we reconstruct features from corresponding details saved with match
        // because there features are already Reordered via DefaultPhenotypesMap
        Set<? extends Feature> matchFeatures = readFeatures(match.getMatchedDetails());
        Set<? extends Feature> refFeatures = readFeatures(match.getReferenceDetails());

        // passing null in place of access because the user is admin,
        // access not used in DefaultPatientPhenotypeSimilarityView
        PatientPhenotypeSimilarityView featuresView =
            PHENOTYPE_SIMILARITY_VIEW_FACTORY.createPatientPhenotypeSimilarityView(matchFeatures, refFeatures, null);

        return featuresView.toJSON();
    }

    // read patient features from match "details" string saved in db
    private Set<Feature> readFeatures(String patientDetails)
    {
        Set<Feature> features = new HashSet<>();

        JSONObject json = new JSONObject(patientDetails);
        JSONObject phenotypesJson = json.optJSONObject("phenotypes");
        if (phenotypesJson == null) {
            return features;
        }
        JSONArray array = phenotypesJson.optJSONArray(PhenotypesMap.PREDEFINED);
        if (array == null) {
            return features;
        }

        for (Object object : array) {
            JSONObject item = (JSONObject) object;
            Feature phenotipsFeature = new PhenoTipsFeature(item);
            features.add(phenotipsFeature);
        }

        return features;
    }

    private String getMultipartContent(Object content)
    {
        try {
            if (content instanceof MimeMultipart) {
                MimeMultipart mmEmail = (MimeMultipart) content;
                int bodyParts = mmEmail.getCount();
                for (int i = 0; i < bodyParts; i++) {
                    BodyPart next = mmEmail.getBodyPart(i);
                    if (next.getContentType().contains(EMAIL_PREFERRED_CONTENT_TYPE)) {
                        return next.getContent().toString();
                    } else {
                        String text = this.getMultipartContent(next.getContent());
                        if (StringUtils.isNotBlank(text)) {
                            return text;
                        }
                    }
                }
            }
        } catch (Exception ex) {
            LOGGER.error("Error getting email contents: [{}]", ex.getMessage(), ex);
        }
        return "";
    }

    @Override
    public JSONObject getEmail()
    {
        JSONObject result = new JSONObject();

        try {
            result.put(EMAIL_CONTENT_KEY, getMultipartContent(this.mimeMessage.getContent()));
        } catch (Exception ex) {
            LOGGER.error("Error getting email contents: [{}]", ex.getMessage(), ex);
            result.put(EMAIL_CONTENT_KEY, "");
        }

        result.put(EMAIL_CONTENT_TYPE_KEY, EMAIL_PREFERRED_CONTENT_TYPE);

        JSONArray to = new JSONArray();
        JSONArray cc = new JSONArray();
        String from = null;
        try {
            for (Address address : this.mimeMessage.getRecipients(Message.RecipientType.TO)) {
                to.put(address.toString());
            }
        } catch (Exception ex) {
            LOGGER.error("Error getting email TO recipients: [{}]", ex.getMessage());
        }
        try {
            for (Address address : this.mimeMessage.getRecipients(Message.RecipientType.CC)) {
                cc.put(address.toString());
            }
        } catch (Exception ex) {
            LOGGER.error("Error getting email CC recipients: [{}]", ex.getMessage());
        }
        try {
            for (Address address : this.mimeMessage.getReplyTo()) {
                from = (from == null) ? address.toString() : (from + ", " + address.toString());
            }
        } catch (Exception ex) {
            LOGGER.error("Error getting email FROM recipients: [{}]", ex.getMessage());
            from = "";
        }

        JSONObject recipients = new JSONObject();
        recipients.put(EMAIL_RECIPIENTS_TO, to);
        recipients.put(EMAIL_RECIPIENTS_CC, cc);
        recipients.put(EMAIL_RECIPIENTS_FROM, from);
        result.put(EMAIL_RECIPIENTS_KEY, recipients);

        try {
            result.put(EMAIL_SUBJECT_KEY, this.mimeMessage.getSubject());
        } catch (Exception ex) {
            LOGGER.error("Error getting email subject: [{}]", ex.getMessage(), ex);
            result.put(EMAIL_SUBJECT_KEY, "");
        }

        return result;
    }
}
