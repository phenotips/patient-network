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
import org.phenotips.matchingnotification.match.PatientMatch;
import org.phenotips.matchingnotification.notification.PatientMatchNotificationResponse;

import org.xwiki.component.annotation.Role;

import java.security.AccessControlException;
import java.util.List;
import java.util.Map;

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
     * @param customEmailSubject (optional) email subject to be used
     *
     * @return status of the notification as PatientMatchNotificationResponse
     */
    PatientMatchNotificationResponse sendUserNotification(Long matchId,
        String subjectPatientId, String subjectServerId, String customEmailText, String customEmailSubject);

    /**
     * Returns the contents of the email that will be send as a notification for match with the given id,
     * with the recipient being the owner of the given patient on the given server.
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
     * @return null if saving failed, otherwise list of saved PatientMatches
     */
    List<PatientMatch> saveIncomingMatches(List<? extends PatientSimilarityView> similarityViews, String patientId,
        String remoteId);

    /**
     * Saves a list of matches that were found by a remote outgoing request.
     *
     * @param similarityViews list of similarity views
     * @param patientId local patient ID for whom to save matches
     *        (needed only to know which existing matches to remove/replace)
     * @param remoteId id of remote server contacted to find matches for the given local patient
     * @return null if saving failed, otherwise list of saved PatientMatches
     */
    List<PatientMatch> saveOutgoingMatches(List<? extends PatientSimilarityView> similarityViews, String patientId,
        String remoteId);

    /**
     * Sets status for a match that has the given {@code matchId internal ID}.
     *
     * @param matchId the internal ID of the match of interest
     * @param status whether matches should be set as saved, rejected or uncategorized
     * @return updated match
     * @throws AccessControlException if user does not have at least view access to each patient in the match
     */
    PatientMatch setStatus(Long matchId, String status) throws AccessControlException;

    /**
     * Marks all matches with ids in {@code matchesIds} as user-contacted or not.
     *
     * @param matchId the internal ID of the match of interest
     * @param isUserContacted boolean user-contacted status to set for matches
     * @return updated match
     * @throws AccessControlException if user does not have at least view access to each patient in the match
     */
    PatientMatch setUserContacted(Long matchId, boolean isUserContacted) throws AccessControlException;

    /**
     * Saves comment for a match that has the given {@code matchId internal ID}.
     *
     * @param matchId the internal ID of the match of interest
     * @param comment comment text
     * @return updated match
     * @throws AccessControlException if user does not have at least view access to each patient in the match
     */
    PatientMatch saveComment(Long matchId, String comment) throws AccessControlException;

    /**
     * Saves note for a match that has the given {@code matchId internal ID}.
     *
     * @param matchId the internal ID of the match of interest
     * @param note note text
     * @return updated match
     * @throws AccessControlException if user does not have at least view access to each patient in the match
     */
    PatientMatch addNote(Long matchId, String note) throws AccessControlException;

    /**
     * Returns a match that has the given {@code matchId internal ID}.
     *
     * @param matchId the internal ID of the match of interest
     * @return {@code PatientMatch} match object
     */
    PatientMatch getMatch(Long matchId);
}
