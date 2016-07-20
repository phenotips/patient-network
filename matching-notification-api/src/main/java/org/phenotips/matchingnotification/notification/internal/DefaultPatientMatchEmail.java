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
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.inject.Provider;
import javax.mail.Message.RecipientType;
import javax.mail.internet.InternetAddress;

import org.apache.commons.lang3.StringUtils;
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

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultPatientMatchEmail.class);

    private static final MailSenderScriptService MAIL_SERVICE;

    private static final Provider<XWikiContext> CONTEXT_PROVIDER;

    private static final DocumentReferenceResolver<String> REFERENCE_RESOLVER;

    private ScriptMimeMessage mimeMessage;

    private String subjectPatientId;

    private Collection<PatientMatch> matches;

    private boolean sent;

    private MailStatus mailStatus;

    static {
        MailSenderScriptService mailService = null;
        Provider<XWikiContext> contextProvider = null;
        DocumentReferenceResolver<String> referenceResolver = null;
        try {
            ComponentManager ccm = ComponentManagerRegistry.getContextComponentManager();
            mailService = ccm.getInstance(ScriptService.class, "mailsender");
            contextProvider = ccm.getInstance(XWikiContext.TYPE_PROVIDER);
            referenceResolver = ccm.getInstance(DocumentReferenceResolver.TYPE_STRING, "current");
        } catch (ComponentLookupException e) {
            LOGGER.error("Error initializing mailService", e);
        }
        MAIL_SERVICE = mailService;
        CONTEXT_PROVIDER = contextProvider;
        REFERENCE_RESOLVER = referenceResolver;
    }

    /**
     * Build a new email object for a list of matches. {@code matches} is expected to be non empty, and every object in
     * it should return the same value for getReferencePatientId(), the id of the reference patient about whom this
     * email is created.
     *
     * @param subjectPatientId id of patient who is the subject of this email (always local)
     * @param matches list of matches that the email notifies of.
     */
    public DefaultPatientMatchEmail(String subjectPatientId, Collection<PatientMatch> matches)
    {
        this.subjectPatientId = subjectPatientId;
        this.matches = matches;
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
        Map<String, Object> emailParameters = new HashMap<String, Object>();
        String language = CONTEXT_PROVIDER.get().getLocale().getLanguage();
        emailParameters.put("language", language);

        emailParameters.put("velocityVariables", this.createVelocityVariablesMap());

        return emailParameters;
    }

    private Map<String, Object> createVelocityVariablesMap()
    {
        Map<String, Object> velocityVariables = new HashMap<>();

        this.addSubjectPatientDetails(velocityVariables);

        // Read 'other' patient from each match
        List<Map<String, Object>> matchesForEmail = new ArrayList<>(this.matches.size());
        for (PatientMatch match : this.matches) {
            matchesForEmail.add(this.getMatchMap(match));
        }
        velocityVariables.put("matches", matchesForEmail);

        return velocityVariables;
    }

    private void addSubjectPatientDetails(Map<String, Object> map)
    {
        PatientMatch match = this.matches.iterator().next();
        boolean useRef =
            StringUtils.equals(match.getReferencePatientId(), this.subjectPatientId)
            && StringUtils.equals(match.getReferenceServerId(), null);

        map.put("subjectPatientId", useRef ? match.getReferencePatientId() : match.getMatchedPatientId());
        map.put("subjectEmail", useRef ? match.getEmail() : match.getMatchedEmail());
        map.put("subjectGenes", useRef ? match.getCandidateGenes() : match.getMatchedCandidateGenes());
    }

    private Map<String, Object> getMatchMap(PatientMatch match)
    {
        Map<String, Object> map = new HashMap<>();
        boolean useRef =
            StringUtils.equals(match.getMatchedPatientId(), this.subjectPatientId)
            && StringUtils.equals(match.getMatchedServerId(), null);

        map.put("patientId", useRef ? match.getReferencePatientId() : match.getMatchedPatientId());
        map.put("serverId", useRef ? match.getReferenceServerId() :  match.getMatchedServerId());
        map.put("email", useRef ? match.getEmail() : match.getMatchedEmail());
        map.put("genes", useRef ? match.getCandidateGenes() : match.getMatchedCandidateGenes());

        map.put("score", match.getScore());
        map.put("genotypeScore", match.getGenotypeScore());
        map.put("phenotypeScore", match.getPhenotypeScore());

        return map;
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
        PatientMatch match = this.matches.iterator().next();
        boolean useRef =
            StringUtils.equals(match.getReferencePatientId(), this.subjectPatientId)
            && StringUtils.equals(match.getReferenceServerId(), null);
        String email = useRef ? match.getEmail() : match.getMatchedEmail();

        InternetAddress to = new InternetAddress();
        to.setAddress(email);
        this.mimeMessage.addRecipient(RecipientType.TO, to);
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
    public String getSubjectPatientId() {
        return this.subjectPatientId;
    }
}
