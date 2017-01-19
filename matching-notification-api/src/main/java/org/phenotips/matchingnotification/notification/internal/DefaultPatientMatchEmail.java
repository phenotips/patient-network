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

    private PatientInMatch subjectPatient;

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

            PatientInMatch otherPatient;
            if (match.isReference(this.subjectPatient.getPatientId(), null)) {
                otherPatient = match.getMatched();
            } else {
                otherPatient = match.getReference();
            }
            matchMap.put("matchedPatient", otherPatient);
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
}
