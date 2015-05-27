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
package org.phenotips.messaging;

import org.xwiki.component.annotation.Role;

import java.util.Map;

/**
 * Performs actions on {@link Connection}s, such as sending emails and handling access granting.
 *
 * @version $Id$
 * @since 1.0M1
 */
@Role
public interface ActionManager
{
    /**
     * Send the initial email to the owner of the matched patient.
     *
     * @param connection the anonymous communication linking the two patients and their owners that are involved in this
     *            connection
     * @param options the mail content options selected by the user
     * @return {@code 0} if the mail was successfully sent, other numbers in case of errors
     */
    int sendInitialMails(Connection connection, Map<String, Object> options);

    /**
     * Grant mutual view access on the two patients to the owners.
     *
     * @param connection the anonymous communication linking the two patients and their owners that are involved in this
     *            connection
     * @return {@code 0} if access was successfully granted, other numbers in case of errors
     */
    int grantAccess(Connection connection);

    /**
     * Send the followup email to the user requesting access.
     *
     * @param connection the anonymous communication linking the two patients and their owners that are involved in this
     *            connection
     * @return {@code 0} if the mail was successfully sent, other numbers in case of errors
     */
    int sendSuccessMail(Connection connection);
}
