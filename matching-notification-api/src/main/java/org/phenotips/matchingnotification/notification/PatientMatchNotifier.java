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
import java.util.Map;

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
     * patient. Can only be used to create emails for local users.
     *
     * @param matches list of matches to build emails from
     * @param matchesIds map of ids of matches to patients Ids to be notified
     * @return list of emails
     */
    List<PatientMatchEmail> createAdminEmailsToLocalUsers(List<PatientMatch> matches,
        Map<Long, List<String>> matchesIds);

    /**
     * Generates a email to be sent from the current user to the owner of the subjectPatientId on subjectServerId
     * describing the given match.
     *
     * @param match a match to base email text upon
     * @param subjectPatientId the patient who's owner should receive the email
     * @param subjectServerId the server that holds subjectpatientId. it is needed to distinguish the case when match
     *                        is between patients with the same id but on different servers.
     * @param customEmailText (optional) email text to be used
     * @param customEmailSubject (optional) email subject to be used
     * @return the generated email
     */
    PatientMatchEmail createUserEmail(PatientMatch match, String subjectPatientId, String subjectServerId,
        String customEmailText, String customEmailSubject);

    /**
     * Sends notification for an email.
     *
     * @param email an email to send
     * @return list of {@link PatientMatchNotificationResponse} for the matches associated with the email
     */
    List<PatientMatchNotificationResponse> notify(PatientMatchEmail email);
}
