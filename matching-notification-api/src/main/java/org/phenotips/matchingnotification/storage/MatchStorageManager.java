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

import java.sql.Timestamp;
import java.util.Collection;
import java.util.List;
import java.util.Map;
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
     * @param fromDate if passed a date, then only matches found on or after this date will be returned;
     *        if {@code null}, then no lower limit on the match date is considered
     * @param toDate if passed a date, then only matches found on or before this date will be returned;
     *        if {@code null}, then no upper limit on the match date is considered
     * @return a list of matches
     */
    List<PatientMatch> loadMatches(double score, double phenScore, double genScore, boolean onlyCurrentUserAccessible,
        Timestamp fromDate, Timestamp toDate);

    /**
     * Load all matches with ids in {@code matchesIds}.
     *
     * @param matchesIds list of ids of matches to load
     * @return list of matches
     */
    List<PatientMatch> loadMatchesByIds(Set<Long> matchesIds);

    /**
     * Marks a {@code match} as user-contacted or not.
     *
     * @param match match to mark as user-contacted.
     * @param isUserContacted boolean user-contacted status to set for a match
     * @return true if successful
     */
    boolean setUserContacted(PatientMatch match, boolean isUserContacted);

    /**
     * Sets {@code status} to a {@code match}.
     *
     * @param match match to mark as notified.
     * @param status whether a match should be marked as saved, rejected or uncategorized
     * @return true if successful
     */
    boolean setStatus(PatientMatch match, String status);

    /**
     * Saves {@code comment} to a {@code match}.
     *
     * @param match match to save comment.
     * @param comment comment text
     * @return true if successful
     */
    boolean saveComment(PatientMatch match, String comment);

    /**
     * Saves a {@code note} to a {@code match}.
     *
     * @param match match to save note to.
     * @param note note text
     * @return true if successful
     */
    boolean addNote(PatientMatch match, String note);

    /**
     * Deletes all matches (including those that users have been notified about, and MME matches)
     * for the given local patient (e.g. when a patient is deleted).
     *
     * @param patientId local patient ID for whom to delete matches.
     * @return true if successful
     */
    boolean deleteMatchesForLocalPatient(String patientId);

    /**
     * Saves a list of local matches.
     *
     * @param similarityViews list of matches as "similarity views" between two local patients
     * @param patientId local patient ID for whom to save matches
     * @return null if saved failed, otherwise a mapping from similarity views to saved PatientMatches
     */
    Map<PatientSimilarityView, PatientMatch>
        saveLocalMatches(Collection<? extends PatientSimilarityView> similarityViews, String patientId);

    /**
     * Saves a list of matches that were found by a remote outgoing/incoming request.
     *
     * @param similarityViews list of similarity views between a local patient and a remote patient
     * @param patientId remote patient ID for whom to save matches
     * @param serverId id of remote server
     * @param isIncoming whether we are saving results of incoming (then true) or outgoing request
     * @return null if saved failed, otherwise a mapping from similarity views to saved PatientMatches
     */
    Map<PatientSimilarityView, PatientMatch>
        saveRemoteMatches(Collection<? extends PatientSimilarityView> similarityViews, String patientId,
                String serverId, boolean isIncoming);

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
     * @return collection of successfully updated matches
     */
    Collection<PatientMatch> updateNotificationHistory(PatientMatch match, JSONObject notificationRecord);

    /**
     * Updates match notes string property with new note record.
     *
     * @param match subject match
     * @param note the new note record JSON string
     * @return true if successful
     */
    boolean updateNotes(PatientMatch match, String note);
}
