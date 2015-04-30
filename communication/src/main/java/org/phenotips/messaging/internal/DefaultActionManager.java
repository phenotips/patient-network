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
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.phenotips.messaging.internal;

import org.phenotips.data.permissions.AccessLevel;
import org.phenotips.data.permissions.PermissionsManager;
import org.phenotips.messaging.ActionManager;
import org.phenotips.messaging.Connection;

import org.xwiki.component.annotation.Component;

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.slf4j.Logger;

import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.plugin.mailsender.MailSenderPlugin;
import com.xpn.xwiki.web.Utils;

/**
 * Default implementation for the {@code AcctionManager} role.
 *
 * @version $Id$
 */
@Component
@Singleton
public class DefaultActionManager implements ActionManager
{
    private static final String MAIL_SENDER = "mailsender";

    private static final String EMAIL = "email";

    private static final String RECIPIENT_NAME = "recipientName";

    private static final String CONTACTED_USER_NAME = "contactedUserName";

    private static final String MATCH_CASE_ID = "matchCaseId";

    private static final String MATCH_CASE_LINK = "matchCaseAccessLink";

    private static final String PHENOMECENTRAL_EMAIL = "PhenomeCentral <noreply@phenomecentral.org>";

    private static final String FAILED_MAIL_MSG = "Failed to send email: [{}]";

    private static final String EXTERNAL_LINK_MODE = "view";

    private static final String PLATFORM = "PhenomeCentral";

    private static final String SUBJECT = "Access to patient record granted";

    @Inject
    private PermissionsManager permissionsManager;

    @Inject
    @Named("view")
    private AccessLevel defaultAccess;

    @Inject
    private Logger logger;

    @Override
    public int sendInitialMails(Connection connection, Map<String, Object> options)
    {
        try {
            XWikiContext context = Utils.getContext();
            XWiki xwiki = context.getWiki();
            MailSenderPlugin mailsender = (MailSenderPlugin) xwiki.getPlugin(MAIL_SENDER, context);
            String to = xwiki.getDocument(connection.getContactedUser(), context).getStringValue(EMAIL);
            options.put(RECIPIENT_NAME,
                xwiki.getUserName(connection.getContactedUser().toString(), null, false, context));
            options.put(MATCH_CASE_ID, connection.getTargetPatient().getDocument().getName());
            options.put(MATCH_CASE_LINK,
                xwiki.getExternalURL("data.GrantMatchAccess", EXTERNAL_LINK_MODE, "id=" + connection.getId(), context));
            mailsender.sendMailFromTemplate("PhenoTips.MatchContact", PHENOMECENTRAL_EMAIL, to,
                "qc@phenomecentral.org", null, "", options, context);
            return 0;
        } catch (Exception ex) {
            this.logger.error(FAILED_MAIL_MSG, ex.getMessage(), ex);
            return 1;
        }
    }

    @Override
    public int grantAccess(Connection connection)
    {
        if (!this.permissionsManager.getPatientAccess(connection.getTargetPatient()).addCollaborator(
            connection.getInitiatingUser(), this.defaultAccess))
        {
            return 1;
        }
        if (!this.permissionsManager.getPatientAccess(connection.getReferencePatient()).addCollaborator(
            connection.getContactedUser(), this.defaultAccess))
        {
            return 2;
        }
        return 0;
    }

    @Override
    public int sendSuccessMail(Connection connection)
    {
        try {
            Map<String, Object> options = new HashMap<String, Object>();
            XWikiContext context = Utils.getContext();
            XWiki xwiki = context.getWiki();
            MailSenderPlugin mailsender = (MailSenderPlugin) xwiki.getPlugin(MAIL_SENDER, context);
            String to = xwiki.getDocument(connection.getInitiatingUser(), context).getStringValue(EMAIL);
            options.put("platformName", PLATFORM);
            options.put("subject", SUBJECT);
            options.put(RECIPIENT_NAME,
                xwiki.getUserName(connection.getInitiatingUser().toString(), null, false, context));
            options.put(CONTACTED_USER_NAME,
                xwiki.getUserName(connection.getContactedUser().toString(), null, false, context));
            options.put(MATCH_CASE_ID, connection.getTargetPatient().getDocument().getName());
            options.put("matchCaseReferenceId", connection.getReferencePatient().getDocument().getName());
            options.put(MATCH_CASE_LINK, xwiki.getDocument(connection.getTargetPatient().getDocument(), context)
                .getExternalURL(EXTERNAL_LINK_MODE, context));
            options.put("matchCaseReferenceLink",
                xwiki.getDocument(connection.getReferencePatient().getDocument(), context)
                    .getExternalURL(EXTERNAL_LINK_MODE, context));
            mailsender.sendMailFromTemplate("PhenoTips.MatchSuccessContact", PHENOMECENTRAL_EMAIL,
                to, null, null, "", options, context);
            return 0;
        } catch (Exception ex) {
            this.logger.error(FAILED_MAIL_MSG, ex.getMessage(), ex);
            return 1;
        }
    }
}
