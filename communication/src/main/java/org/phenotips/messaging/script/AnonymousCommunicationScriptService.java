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
package org.phenotips.messaging.script;

import org.phenotips.messaging.ActionManager;
import org.phenotips.messaging.Connection;
import org.phenotips.messaging.ConnectionManager;

import org.xwiki.component.annotation.Component;
import org.xwiki.script.service.ScriptService;

import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

/**
 * Stores a connection between the owners of two matched patients, anonymously, to be used for email communication. The
 * identities of the two parties are kept private, since mails are sent behind the scenes, while the users only see the
 * {@link Connection#getToken() identifier of the connection}.
 *
 * @version $Id$
 * @since 1.0M1
 */
@Component
@Named("anonymousCommunication")
@Singleton
public class AnonymousCommunicationScriptService implements ScriptService
{
    @Inject
    private ConnectionManager connectionManager;

    @Inject
    private ActionManager actionManager;

    /**
     * Send the initial email to the owner of the matched patient.
     *
     * @param connectionId the id of the anonymous communication linking the two patients and their owners that are
     *            involved in this connection
     * @param options the mail content options selected by the user
     * @return {@code 0} if the mail was successfully sent, http response codes in case of errors
     */
    public int sendInitialMail(String connectionId, Map<String, Object> options)
    {
        Connection c;
        c = this.connectionManager.getConnectionByToken(connectionId);
        // FIXME! Add rights check: only the initiating user can do this
        return this.actionManager.sendInitialMails(c, options);
    }

    /**
     * Grant mutual view access on the two patients to the owners.
     *
     * @param connectionId the id of the anonymous communication linking the two patients and their owners that are
     *            involved in this connection
     * @return {@code 0} if access was successfully granted, other numbers in case of errors
     */
    public Connection grantAccess(String connectionId)
    {
        try {
            Connection c;
            c = this.connectionManager.getConnectionByToken(connectionId);
            // FIXME! Add rights check: only the contacted user can do this
            if (this.actionManager.grantAccess(c) == 0) {
                try {
                    this.actionManager.sendSuccessMail(c);
                } catch (Exception ex) {
                    // Do nothing
                }
                return c;
            } else {
                return null;
            }
        } catch (Exception ex) {
            return null;
        }
    }
}
