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
package org.phenotips.matchingnotification.notification;

import org.phenotips.matchingnotification.match.PatientMatch;

import org.xwiki.component.annotation.Role;

import java.util.List;

/**
 * Send a notification per patient.
 *
 * @version $Id$
 */
@Role
public interface PatientMatchNotifier
{
    /**
     * Build a list of emails based on the given matches. One email is created for all matches with same reference
     * patient.
     *
     * @param matches list of matches to build emails from
     * @return list of emails
     */
    List<PatientMatchEmail> createEmails(List<PatientMatch> matches);

    /**
     * Sends notification for an email.
     *
     * @param email an email to send
     * @return list of {@PatientMatchNotificationResponse} for the matches associated with the email
     */
    List<PatientMatchNotificationResponse> notify(PatientMatchEmail email);

}
