/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.phenotips.messaging.internal;

import org.phenotips.data.permissions.AccessLevel;
import org.phenotips.data.permissions.PermissionsManager;
import org.phenotips.messaging.ActionManager;
import org.phenotips.messaging.Connection;

import org.xwiki.component.annotation.Component;

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
            MailSenderPlugin mailsender = (MailSenderPlugin) xwiki.getPlugin("mailsender", context);
            String to = xwiki.getDocument(connection.getContactedUser(), context).getStringValue("email");
            options.put("recipientName",
                xwiki.getUserName(connection.getContactedUser().toString(), null, false, context));
            options.put("matchCaseId", connection.getReferencePatient().getDocument().getName());
            options.put("matchCaseAccessLink",
                xwiki.getExternalURL("data.GrantMatchAccess", "view", "id=" + connection.getId(), context));
            mailsender.sendMailFromTemplate("PhenoTips.MatchContact", "noreply@phenomecentral.org", to, null, null, "",
                options, context);
            return 0;
        } catch (Exception ex) {
            this.logger.error("Failed to send email: [{}]", ex.getMessage(), ex);
            return 1;
        }
    }

    @Override
    public int grantAccess(Connection connection)
    {
        if (!this.permissionsManager.getPatientAccess(connection.getTargetPatient()).addCollaborator(
            connection.getInitiatingUser(), this.defaultAccess)) {
            return 1;
        }
        if (!this.permissionsManager.getPatientAccess(connection.getReferencePatient()).addCollaborator(
            connection.getContactedUser(), this.defaultAccess)) {
            return 2;
        }
        return 0;
    }
}
