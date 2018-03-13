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
import org.xwiki.mail.MailStatus;
import org.xwiki.mail.MailStatusResult;
import org.xwiki.mail.script.MailSenderScriptService;
import org.xwiki.mail.script.ScriptMailResult;
import org.xwiki.mail.script.ScriptMimeMessage;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.script.service.ScriptService;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Provider;
import javax.mail.Address;
import javax.mail.BodyPart;
import javax.mail.Message;
import javax.mail.Message.RecipientType;
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
public class DefaultPatientMatchEmail implements PatientMatchEmail
{
    /** Name of document containing template for email notification. */
    public static final String EMAIL_TEMPLATE = "PatientMatchNotificationEmailTemplate";

    private static final String EMAIL_CONTENT_KEY = "emailContent";

    private static final String EMAIL_CONTENT_TYPE_KEY = "contentType";

    private static final String EMAIL_RECIPIENTS_KEY = "recipients";

    private static final String EMAIL_SUBJECT_KEY = "subject";

    private static final String EMAIL_PREFERRED_CONTENT_TYPE = "text/plain";

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultPatientMatchEmail.class);

    private static final MailSenderScriptService MAIL_SERVICE;

    private static final Provider<XWikiContext> CONTEXT_PROVIDER;

    private static final DocumentReferenceResolver<String> REFERENCE_RESOLVER;

    private static final PatientPhenotypeSimilarityViewFactory PHENOTYPE_SIMILARITY_VIEW_FACTORY;

    private ScriptMimeMessage mimeMessage;

    private PatientInMatch subjectPatient;

    private Collection<PatientMatch> matches;

    private boolean sent;

    private MailStatus mailStatus;

    static {
        MailSenderScriptService mailService = null;
        Provider<XWikiContext> contextProvider = null;
        DocumentReferenceResolver<String> referenceResolver = null;
        PatientPhenotypeSimilarityViewFactory patientPhenotypeSimilarityViewFactory = null;
        try {
            ComponentManager ccm = ComponentManagerRegistry.getContextComponentManager();
            mailService = ccm.getInstance(ScriptService.class, "mailsender");
            contextProvider = ccm.getInstance(XWikiContext.TYPE_PROVIDER);
            referenceResolver = ccm.getInstance(DocumentReferenceResolver.TYPE_STRING, "current");
            patientPhenotypeSimilarityViewFactory = ccm.getInstance(PatientPhenotypeSimilarityViewFactory.class,
                "restricted");
        } catch (ComponentLookupException e) {
            LOGGER.error("Error initializing mailService", e);
        }
        MAIL_SERVICE = mailService;
        CONTEXT_PROVIDER = contextProvider;
        REFERENCE_RESOLVER = referenceResolver;
        PHENOTYPE_SIMILARITY_VIEW_FACTORY = patientPhenotypeSimilarityViewFactory;
    }

    /**
     * Build a new email object for a list of matches. {@code matches} is expected to be non empty, and one of the
     * patients in every match should have same id as {@code subjectPatientId}.
     *
     * @param subjectPatientId id of patient who is the subject of this email (always local)
     * @param matches list of matches that the email notifies of.
     */
    public DefaultPatientMatchEmail(String subjectPatientId, Collection<PatientMatch> matches)
    {
        this.matches = matches;

        PatientMatch anyMatch = this.matches.iterator().next();
        if (anyMatch.isReference(subjectPatientId, null)) {
            this.subjectPatient = anyMatch.getReference();
        } else {
            this.subjectPatient = anyMatch.getMatched();
        }
        this.createMimeMessage();
    }

    private void createMimeMessage()
    {
        DocumentReference templateReference = REFERENCE_RESOLVER.resolve(EMAIL_TEMPLATE, PatientMatch.DATA_SPACE);
        this.mimeMessage = MAIL_SERVICE.createMessage("template", templateReference, this.createEmailParameters());

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

    private Map<String, Object> createVelocityVariablesMap()
    {
        Map<String, Object> velocityVariables = new HashMap<>();
        velocityVariables.put("subjectPatient", this.subjectPatient);

        List<Map<String, Object>> matchesForEmail = new ArrayList<>(this.matches.size());
        for (PatientMatch match : this.matches) {
            Map<String, Object> matchMap = new HashMap<>();

            // Feature matching
            JSONArray featureMatchesJSON = getFeatureMatchesJSON(match);
            if (featureMatchesJSON.length() > 0) {
                matchMap.put("featureMatches", featureMatchesJSON);
            }
            PatientInMatch otherPatient;
            if (match.isReference(this.subjectPatient.getPatientId(), null)) {
                otherPatient = match.getMatched();
            } else {
                otherPatient = match.getReference();
            }
            // NOTE: "subjectMatchedPatient" can be reference or match patient inside the match!
            // Here  "subjectPatient" means the patient this email will be about,
            //       "subjectMatchedPatient" is one of found matches to the "subjectPatient"
            matchMap.put("subjectMatchedPatient", otherPatient);
            matchMap.put("subjectMatchedPatientEmails", otherPatient.getEmails());
            matchMap.put("match", match);

            matchesForEmail.add(matchMap);
        }
        velocityVariables.put("matches", matchesForEmail);

        return velocityVariables;
    }

    private void setFrom()
    {
        String serverName = CONTEXT_PROVIDER.get().getRequest().getServerName();
        if (StringUtils.isNotEmpty(serverName)) {
            InternetAddress from = new InternetAddress();
            from.setAddress("noreply@" + serverName);
            this.mimeMessage.setFrom(from);
        }
    }

    private void setTo()
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
        ScriptMailResult mailResult = MAIL_SERVICE.send(this.mimeMessage);
        this.sent = true;

        this.mailStatus = null;
        MailStatusResult mailStatusResult = mailResult.getStatusResult();
        Iterator<MailStatus> allResults = mailStatusResult.getAll();
        if (allResults.hasNext()) {
            this.mailStatus = allResults.next();
        }
    }

    @Override
    public String getSubjectPatientId()
    {
        return this.subjectPatient.getPatientId();
    }

    private JSONArray getFeatureMatchesJSON(PatientMatch match)
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

        JSONArray recipients = new JSONArray();
        try {
            for (Address address : this.mimeMessage.getRecipients(Message.RecipientType.TO)) {
                recipients.put(address.toString());
            }
        } catch (Exception ex) {
            LOGGER.error("Error getting email recipients: [{}]", ex.getMessage(), ex);
            recipients.put("");
        }
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
