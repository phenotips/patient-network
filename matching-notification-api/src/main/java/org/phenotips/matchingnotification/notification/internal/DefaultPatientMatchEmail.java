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

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.inject.Provider;
import javax.mail.Message.RecipientType;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.xpn.xwiki.XWikiContext;

/**
 * An email that sent to the owner of the reference patient to inform of all the matches found for that patient.
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

    protected DefaultPatientMatchEmail(Collection<PatientMatch> matches, ScriptMimeMessage mimeMessage)
    {
        this.matches = matches;
        this.mimeMessage = mimeMessage;
    }

    /**
     * Build a new email object for a list of matches. {@code matches} is expected to be non empty, and every object in
     * it should return the same value for getReferencePatientId(), the id of the reference patient about whom this
     * email is created.
     *
     * @param matches list of matches that the email notifies of.
     * @return a new instance of PatientMatchEmail if created successfully, or null
     */
    public static DefaultPatientMatchEmail newInstance(Collection<PatientMatch> matches)
    {
        ScriptMimeMessage mimeMessage = createMimeMessage(matches);
        if (mimeMessage == null) {
            LOGGER.error("Error creating email message.");
            return null;
        }

        return new DefaultPatientMatchEmail(matches, mimeMessage);
    }

    private static ScriptMimeMessage createMimeMessage(Collection<PatientMatch> matches)
    {
        Map<String, Object> emailParameters = new HashMap<String, Object>();
        String language = CONTEXT_PROVIDER.get().getLocale().getLanguage();
        emailParameters.put("language", language);

        Iterator<PatientMatch> iterator = matches.iterator();
        if (!iterator.hasNext()) {
            return null;
        }
        String referencePatientId = iterator.next().getReferencePatientId();

        Map<String, Object> velocityVariables = new HashMap<>();
        velocityVariables.put("referencePatientId", referencePatientId);
        velocityVariables.put("matches", matches);
        emailParameters.put("velocityVariables", velocityVariables);

        DocumentReference emailTemplateReference = REFERENCE_RESOLVER.resolve(EMAIL_TEMPLATE, PatientMatch.DATA_SPACE);
        ScriptMimeMessage mimeMessage = MAIL_SERVICE.createMessage("template", emailTemplateReference, emailParameters);
        if (mimeMessage == null) {
            return null;
        }

        if (!setFrom(mimeMessage) || !setTo(mimeMessage, matches)) {
            return null;
        }

        return mimeMessage;
    }

    private static boolean setFrom(ScriptMimeMessage mimeMessage)
    {
        String serverName = CONTEXT_PROVIDER.get().getRequest().getServerName();
        if (StringUtils.isNotEmpty(serverName)) {
            String fromAddress = "noreply@" + serverName;
            try {
                InternetAddress from = new InternetAddress(fromAddress);
                mimeMessage.setFrom(from);
            } catch (AddressException e) {
                LOGGER.error("Error creating from address {} for email", fromAddress, e);
                return false;
            }
        }
        return true;
    }

    private static boolean setTo(ScriptMimeMessage mimeMessage, Collection<PatientMatch> matches)
    {
        Iterator<PatientMatch> iterator = matches.iterator();
        if (!iterator.hasNext()) {
            return false;
        }

        String toAddress = iterator.next().getEmail();
        InternetAddress to;
        try {
            to = new InternetAddress(toAddress);
            mimeMessage.addRecipient(RecipientType.TO, to);
        } catch (AddressException e) {
            LOGGER.error("Error creating receipient {} for email", toAddress, e);
            return false;
        }
        return true;
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
}
