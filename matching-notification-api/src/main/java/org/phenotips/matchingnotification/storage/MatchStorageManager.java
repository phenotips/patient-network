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
     * @param onlyNotified when true only matches that has been notified will be returned
     *        FIXME: this parameter is now deprecated, to be removed when new UI that doe snot need it is accepted
     * @return a list of matches
     */
    List<PatientMatch> loadMatches(double score, double phenScore, double genScore, boolean onlyNotified);

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
     * Marks all matches in {@code matches} as notified.
     *
     * @param matches list of matches to mark as notified.
     * @return true if successful
     */
    boolean markNotified(List<PatientMatch> matches);

    /**
     * Sets status to all matches in {@code matches} to a passed status string.
     *
     * @param matches list of matches to mark as notified.
     * @param status whether the matches should be marked as saved, rejected or uncategorized
     * @return true if successful
     */
    boolean setStatus(List<PatientMatch> matches, String status);

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
}
