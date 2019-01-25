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
package org.phenotips.matchingnotification;

import org.phenotips.data.similarity.PatientSimilarityView;
import org.phenotips.matchingnotification.notification.PatientMatchNotificationResponse;

import org.xwiki.component.annotation.Role;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.json.JSONObject;

/**
 * @version $Id$
 */
@Role
public interface MatchingNotificationManager
{
    /**
     * Sends notification to the owner of every match with id in {@code matchesId}, then marks match as notified.
     *
     * @param idsList map of ids of matches to patients Ids to be notified
     * @return a list of PatientmatchNotificationResponse
     */
    List<PatientMatchNotificationResponse> sendAdminNotificationsToLocalUsers(Map<Long, List<String>> idsList);

    /**
     * Sends notification to the owner of the suvbjectpatientId about the given match.
     *
     * @param matchId match id
     * @param subjectPatientId the id of the patient that is the subject of the email (can be either of
     *                         the two patients involved in the specified match)
     * @param subjectServerId the id of the server that holds the given patient. Is only needed to distinguish two
     *                        patients with the same ID
     * @param customEmailText (optional) email text to be used
     * @param customEmailSubject (optional) emial subject to be used
     *
     * @return status of the notification as PatientMatchNotificationResponse
     */
    PatientMatchNotificationResponse sendUserNotification(Long matchId,
        String subjectPatientId, String subjectServerId, String customEmailText, String customEmailSubject);

    /**
     * Returns the contents of the email that will be send as a notification for match with the given id,
     * with the recepient being the owner of the given patient on the given server.
     *
     * @param matchId match id
     * @param subjectPatientId the id of the patient that is the subject of the email (can be either of
     *                         the two patients involved in the specified match)
     * @param subjectServerId the id of the server that holds the given patient. Is only needed to distinguish two
     *                        patients with the same ID
     *
     * @return a response containing a JSON object, in the following format:
     *     <pre>
     *      { "emailContent": text,
     *        "recipients": { "to": list_of_email_addresses_as_strings, "from": list, "cc": list },
     *        "contentType": type,
     *        "subject": text }
     *     </pre>
     *     where text is a string, and type the type of content as string (e.g. "text/plain")
     */
    JSONObject getUserEmailContent(Long matchId, String subjectPatientId, String subjectServerId);

    /**
     * Saves a list of matches that were found by a remote incoming request.
     *
     * @param similarityViews list of similarity views
     * @param patientId remote patient ID for whom to save matches
     *        (needed only to know which existing matches to remove/replace)
     * @param remoteId id of remote server which sent the incoming request and houses the given patient
     * @return true if successful
     */
    boolean saveIncomingMatches(List<? extends PatientSimilarityView> similarityViews, String patientId,
        String remoteId);

    /**
     * Saves a list of matches that were found by a remote outgoing request.
     *
     * @param similarityViews list of similarity views
     * @param patientId local patient ID for whom to save matches
     *        (needed only to know which existing matches to remove/replace)
     * @param remoteId id of remote server contacted to find matches for the given local patient
     * @return true if successful
     */
    boolean saveOutgoingMatches(List<? extends PatientSimilarityView> similarityViews, String patientId,
        String remoteId);

    /**
     * Sets status to all matches with ids in {@code matchesIds} to a passed status string.
     *
     * @param matchesIds list of ids of matches to set status
     * @param status whether matches should be set as saved, rejected or uncategorized
     * @return true if successful
     */
    boolean setStatus(Set<Long> matchesIds, String status);

    /**
     * Marks all matches with ids in {@code matchesIds} as notified or not notified.
     *
     * @param matchesIds list of ids of matches to mark as notified.
     * @param isNotified boolean notified status to set for matches
     * @return true if successful
     */
    boolean setNotifiedStatus(Set<Long> matchesIds, boolean isNotified);

    /**
     * Marks all matches with ids in {@code matchesIds} as user-contacted or not.
     *
     * @param matchesIds list of ids of matches to mark as user-contacted.
     * @param isUserContacted boolean user-contacted status to set for matches
     * @return true if successful
     */
    boolean setUserContacted(Set<Long> matchesIds, boolean isUserContacted);

    /**
     * Sets comment to all matches with ids in {@code matchesIds} to a passed comment string.
     *
     * @param matchesIds list of ids of matches to set status
     * @param comment comment text
     * @return true if successful
     */
    boolean setComment(Set<Long> matchesIds, String comment);

    /**
     * Saves note to all matches with ids in {@code matchesIds}.
     *
     * @param matchesIds list of ids of matches to save note to
     * @param note note text
     * @return true if successful
     */
    boolean addNote(Set<Long> matchesIds, String note);
}
