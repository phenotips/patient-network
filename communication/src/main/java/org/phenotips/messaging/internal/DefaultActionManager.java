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
package org.phenotips.messaging.internal;

import org.phenotips.data.permissions.AccessLevel;
import org.phenotips.data.permissions.EntityPermissionsManager;
import org.phenotips.matchingnotification.match.PatientMatch;
import org.phenotips.matchingnotification.storage.MatchStorageManager;
import org.phenotips.messaging.ActionManager;
import org.phenotips.messaging.Connection;

import org.xwiki.bridge.DocumentAccessBridge;
import org.xwiki.component.annotation.Component;
import org.xwiki.model.reference.DocumentReference;

import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.mail.MessagingException;

import org.hibernate.Session;
import org.slf4j.Logger;

import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.plugin.mailsender.Mail;
import com.xpn.xwiki.plugin.mailsender.MailSenderPlugin;
import com.xpn.xwiki.web.Utils;

/**
 * Default implementation for the {@code AcctionManager} role.
 *
 * @version $Id$
 * @since 1.0M1
 */
@Component
@Singleton
public class DefaultActionManager implements ActionManager
{
    private static final String MAIL_SENDER = "mailsender";

    private static final String GROUP_EMAIL = "contact";

    private static final String EMAIL = "email";

    private static final String RECIPIENT_NAME = "recipientName";

    private static final String CONTACTED_USER_NAME = "contactedUserName";

    private static final String MATCH_CASE_ID = "matchCaseId";

    private static final String MATCH_CASE_LINK = "matchCaseAccessLink";

    private static final String PHENOMECENTRAL_EMAIL = "PhenomeCentral <noreply@phenomecentral.org>";

    private static final String FAILED_MAIL_MSG = "Failed to send email: [{}]";

    private static final String FAILED_GET_MAIL_MSG = "Failed to get email: [{}]";

    private static final String EXTERNAL_LINK_MODE = "view";

    private static final String PLATFORM = "PhenomeCentral";

    private static final String SUBJECT = "Access to patient record granted";

    private static final String SUBJECT_FIELD_STRING = "subject";

    private static final String OPTIONS_MESSAGE_FIELD = "message";

    @Inject
    private EntityPermissionsManager permissionsManager;

    @Inject
    @Named("view")
    private AccessLevel defaultAccess;

    @Inject
    private DocumentAccessBridge bridge;

    @Inject
    @Named("manage")
    private AccessLevel manageAccessLevel;

    @Inject
    private MatchStorageManager matchStorageManager;

    @Inject
    private Logger logger;

    @Override
    public int sendInitialMails(Connection connection, Map<String, Object> options)
    {
        try {
            XWikiContext context = Utils.getContext();
            XWiki xwiki = context.getWiki();
            MailSenderPlugin mailsender = (MailSenderPlugin) xwiki.getPlugin(MAIL_SENDER, context);
            String to = getEmail(connection.getContactedUser());
            String replyTo = getEmail(connection.getInitiatingUser());
            Mail mail = new Mail();
            mail.setTo(to);
            mail.setHeader("Reply-To", replyTo);
            mail.setHeader("Return-Path", replyTo);
            mail.setFrom(PHENOMECENTRAL_EMAIL);
            mail.setCc(getEmail(connection.getInitiatingUser()));
            mail.setBcc("qc@phenomecentral.org");
            mail.setTextPart((String) options.get(OPTIONS_MESSAGE_FIELD));
            mail.setSubject((String) options.get(SUBJECT_FIELD_STRING));
            mailsender.sendMail(mail, context);
            setNotified((String) options.get("patientId"), (String) options.get("matchId"));
            return 0;
        } catch (MessagingException e) {
            this.logger.error(FAILED_MAIL_MSG, e.getMessage(), e);
            return 500;
        } catch (UnsupportedEncodingException e) {
            this.logger.error(FAILED_MAIL_MSG, e.getMessage(), e);
            return 400;
        }
    }

    @Override
    public int grantAccess(Connection connection)
    {
        if (!this.permissionsManager.getEntityAccess(connection.getTargetPatient()).addCollaborator(
            connection.getInitiatingUser(), this.defaultAccess)) {
            return 1;
        }
        if (!this.permissionsManager.getEntityAccess(connection.getReferencePatient()).addCollaborator(
            connection.getContactedUser(), this.defaultAccess)) {
            return 2;
        }
        return 0;
    }

    @Override
    public int sendSuccessMail(Connection connection)
    {
        try {
            Map<String, Object> options = new HashMap<>();
            XWikiContext context = Utils.getContext();
            XWiki xwiki = context.getWiki();
            MailSenderPlugin mailsender = (MailSenderPlugin) xwiki.getPlugin(MAIL_SENDER, context);
            String to = getEmail(connection.getInitiatingUser());
            options.put("platformName", PLATFORM);
            options.put(SUBJECT_FIELD_STRING, SUBJECT);
            options.put(RECIPIENT_NAME,
                xwiki.getUserName(connection.getInitiatingUser().toString(), null, false, context));
            options.put(CONTACTED_USER_NAME,
                xwiki.getUserName(connection.getContactedUser().toString(), null, false, context));
            DocumentReference ref = connection.getReferencePatient().getDocumentReference();
            options.put(MATCH_CASE_ID, ref.getName());
            options.put("matchCaseReferenceId", ref.getName());
            String url = xwiki.getDocument(ref, context).getExternalURL(EXTERNAL_LINK_MODE, context);
            options.put(MATCH_CASE_LINK, url);
            options.put("matchCaseReferenceLink", url);
            mailsender.sendMailFromTemplate("PhenoTips.MatchSuccessContact", PHENOMECENTRAL_EMAIL,
                to, null, null, "", options, context);
            return 0;
        } catch (Exception ex) {
            this.logger.error(FAILED_MAIL_MSG, ex.getMessage(), ex);
            return 1;
        }
    }

    private String getEmail(DocumentReference userDocument)
    {
        String email = "";
        try {
            XWikiDocument doc = (XWikiDocument) this.bridge.getDocument(userDocument);

            // TODO after projects is merged: UsersAndGroups.getType(userDocument).equals(UsersAndGroups.GROUP)
            if ("Groups".equals(userDocument.getLastSpaceReference().getName())) {
                email = doc.getStringValue(GROUP_EMAIL);
            } else {
                email = doc.getStringValue(EMAIL);
            }
        } catch (Exception e) {
            this.logger.error(FAILED_GET_MAIL_MSG, e.getMessage(), e);
        }

        return email;
    }

    /**
     * When a user contacts another user regarding a match using the Similar Patients UI, mark that match as 'notified'
     * in the match table in the administration.
     *
     * @param patientId reference patientID
     * @param matchId matched patient ID
     */
    private void setNotified(String patientId, String matchId)
    {
        List<PatientMatch> successfulMatches = new LinkedList<>();
        List<PatientMatch> matchesForPatient = this.matchStorageManager.loadMatchesByReferencePatientId(patientId);
        for (PatientMatch match : matchesForPatient) {
            if (match.getMatchedPatientId().equals(matchId)) {
                successfulMatches.add(match);
                break;
            }
        }

        Session session = this.matchStorageManager.beginNotificationMarkingTransaction();
        this.matchStorageManager.markNotified(session, successfulMatches);
        this.matchStorageManager.endNotificationMarkingTransaction(session);
    }
}
