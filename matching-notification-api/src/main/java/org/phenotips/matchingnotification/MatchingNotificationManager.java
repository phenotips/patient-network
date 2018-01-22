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
import org.phenotips.matchingnotification.finder.MatchFinder;
import org.phenotips.matchingnotification.notification.PatientMatchNotificationResponse;

import org.xwiki.component.annotation.Role;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @version $Id$
 */
@Role
public interface MatchingNotificationManager
{
    /**
     * Finds matches for all local patients. For each patient each matcher used will check if the patient can be
     * matched using the matcher (e.g. patient is "matchable", or a "matchable" consent is granted, etc.).
     *
     * All matches that are found will be stored in the matching notification table.
     *
     * @param matchersToUse a list of matchers to be used indicated by their internal names
     *            (see {@link MatchFinder#getName()}). If null, all matchers will be used
     * @param onlyCheckPatientsUpdatedAfterLastRun if true, the selected matcher(s) will only re-check
     *            patients which have been modified since that matcher was run
     */
    void findAndSaveMatches(Set<String> matchersToUse, boolean onlyCheckPatientsUpdatedAfterLastRun);

    /**
     * Sends notification to the owner of every match with id in {@code matchesId}, then marks match as notified.
     *
     * @param idsList map of ids of matches to patients Ids to be notified
     * @return a list of PatientmatchNotificationResponse
     */
    List<PatientMatchNotificationResponse> sendNotifications(Map<Long, List<String>> idsList);

    /**
     * Saves a list of local matches for a patient.
     *
     * @param similarityViews list of similarity views
     * @param patientId local patient ID for whom the matches were foun
     *        (needed only to know which existing matches to remove/replace)
     * @return true if successful
     */
    boolean saveLocalMatchesViews(List<PatientSimilarityView> similarityViews, String patientId);

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
    boolean setStatus(List<Long> matchesIds, String status);
}
