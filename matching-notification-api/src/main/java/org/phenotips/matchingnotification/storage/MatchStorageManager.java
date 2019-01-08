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
package org.phenotips.matchingnotification.storage;

import org.phenotips.data.similarity.PatientSimilarityView;
import org.phenotips.matchingnotification.match.PatientMatch;

import org.xwiki.component.annotation.Role;
import org.xwiki.stability.Unstable;

import java.util.List;
import java.util.Set;

import org.json.JSONObject;

/**
 * @version $Id$
 */
@Role
public interface MatchStorageManager
{
    /**
     * Loads matches filtered by the parameters.
     *
     * @param score threshold for matches
     * @param phenScore only matches with phenotypical score higher or equal to this value are returned
     * @param genScore only matches with genotypical score higher or equal to this value are returned
     * @param onlyCurrentUserAccessible when true only matches that current user has access to are returned
     * @return a list of matches
     */
    List<PatientMatch> loadMatches(double score, double phenScore, double genScore, boolean onlyCurrentUserAccessible);

    /**
     * Load all matches with ids in {@code matchesIds}.
     *
     * @param matchesIds list of ids of matches to load
     * @return list of matches
     */
    List<PatientMatch> loadMatchesByIds(Set<Long> matchesIds);

    /**
     * Load all matches where reference/matched patient ID is same as one of parameters.
     *
     * @param patientId1 id of reference/matched patient to load matches for
     * @param serverId1 id of the server that hosts patientId1
     * @param patientId2 id of reference/matched patient to load matches for
     * @param serverId2 id of the server that hosts patientId2
     * @return list of matches
     */
    List<PatientMatch> loadMatchesBetweenPatients(String patientId1, String serverId1,
        String patientId2, String serverId2);

    /**
     * Marks all matches in {@code matches} as notified or not notified.
     *
     * @param matches list of matches to mark as notified.
     * @param isNotified boolean notified status to set for matches
     * @return true if successful
     */
    boolean setNotifiedStatus(List<PatientMatch> matches, boolean isNotified);

    /**
     * Marks all matches with ids in {@code matchesIds} as user-contacted or not.
     *
     * @param matches list of matches to mark as user-contacted.
     * @param isUserContacted boolean user-contacted status to set for matches
     * @return true if successful
     */
    boolean setUserContacted(List<PatientMatch> matches, boolean isUserContacted);

    /**
     * Sets status to all matches in {@code matches} to a passed status string.
     *
     * @param matches list of matches to mark as notified.
     * @param status whether the matches should be marked as saved, rejected or uncategorized
     * @return true if successful
     */
    boolean setStatus(List<PatientMatch> matches, String status);

    /**
     * Sets comment to all matches in {@code matches} to a passed status string.
     *
     * @param matches list of matches to mark as notified.
     * @param comment comment text
     * @return true if successful
     */
    boolean setComment(List<PatientMatch> matches, String comment);

    /**
     * Saves a note to all matches in {@code matches}.
     *
     * @param matches list of matches to save note to.
     * @param note note text
     * @return true if successful
     */
    boolean saveNote(List<PatientMatch> matches, String note);

    /**
     * Deletes all matches (including those that users have been notified about, and MME matches)
     * for the given local patient.
     *
     * @param patientId local patient ID for whom to delete matches.
     * @return true if successful
     */
    boolean deleteMatchesForLocalPatient(String patientId);

    /**
     * Converts a list of local  SimilarityViews into a list of PatientMatches, keeping only those matches
     * which should be saved into the notification table (i.e. filters out self-matches).
     *
     * FIXME: this method should be part of saveLocalMatches(), however a larger refactoring is needed
     *        for that, since one of the two codepaths that use saveLocalMatches() needs a filtered list of
     *        matches (while another one does not). We should unify both codepaths.
     *
     * @param matches a list of matches assumed ot be matches between local patients
     * @return a list of PatientMatches
     */
    @Unstable
    List<PatientMatch> getMatchesToBePlacedIntoNotificationTable(List<PatientSimilarityView> matches);

    /**
     * Saves a list of local matches.
     *
     * @param matches list of similarity views
     * @param patientId local patient ID for whom to save matches
     * @return true if successful
     */
    boolean saveLocalMatches(List<PatientMatch> matches, String patientId);

    /**
     * Saves a list of matches that were found by a remote outgoing/incoming request.
     *
     * @param similarityViews list of similarity views
     * @param patientId remote patient ID for whom to save matches
     * @param serverId id of remote server
     * @param isIncoming whether we are saving results of incoming (then true) or outgoing request
     * @return true if successful
     */
    boolean saveRemoteMatches(List<? extends PatientSimilarityView> similarityViews, String patientId, String serverId,
        boolean isIncoming);

    /**
     * Calculates the number of matches that were found by all remote outgoing/incoming requests where at least one
     *  patient in the match is remote.
     *
     * @return the total number of remote matches in matches database
     */
    Long getNumberOfRemoteMatches();

    /**
     * Updates a notification history JSON log string with new notification record.
     *
     * @param match subject match
     * @param notificationRecord the new notification record JSON
     * @return true if successful
     */
    boolean updateNotificationHistory(PatientMatch match, JSONObject notificationRecord);

    /**
     * Updates match notes string property with new note record.
     *
     * @param match subject match
     * @param note the new note record JSON string
     * @return true if successful
     */
    boolean updateNotes(PatientMatch match, String note);
}
