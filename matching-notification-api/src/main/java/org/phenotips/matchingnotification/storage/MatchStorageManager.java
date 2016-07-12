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

import org.phenotips.matchingnotification.match.PatientMatch;

import org.xwiki.component.annotation.Role;

import java.util.List;

import org.hibernate.Session;

/**
 * @version $Id$
 */
@Role
public interface MatchStorageManager
{
    /**
     * Stores matches.
     *
     * @param matches to store
     */
    void saveMatches(List<PatientMatch> matches);

    /**
     * Loads matches filtered by the parameters.
     *
     * @param score threshold for matches
     * @param notified whether the matches were notified of
     * @return a list of matches
     */
    List<PatientMatch> loadMatches(double score, boolean notified);

    /**
     * Load all matches with ids in {@link matchesIds}.
     *
     * @param matchesIds list of ids of matches to load
     * @return list of matches
     */
    List<PatientMatch> loadMatchesByIds(List<Long> matchesIds);

    /**
     * Load all matches where reference patient is same as parameter.
     *
     * @param patientId id of reference patient to load matches for
     * @return list of matches
     */
    List<PatientMatch> loadMatchesByReferencePatientId(String patientId);

    /**
     * Initialize the notification marking transaction. See also {@code markNotified} and
     * {@code endNotificationMarkingTransaction}.
     *
     * @return the session. Null if initialization failed.
     */
    Session beginNotificationMarkingTransaction();

    /**
     * Marks all matches in {@code matches} as notified. The method required a session created by
     * {@code startNotificationMarkingTransaction}.
     *
     * @param session the transaction session created for marking.
     * @param matches list of matches to mark as notified.
     * @return true if successful
     */
    boolean markNotified(Session session, List<PatientMatch> matches);

    /**
     * Marks all matches in {@code matches} as rejected. The method required a session created by
     * {@code startNotificationMarkingTransaction}.
     *
     * @param session the transaction session created for marking.
     * @param matches list of matches to mark as notified.
     * @param rejected whether the matches should be marked as rejected or unrejected
     * @return true if successful
     */
    boolean markRejected(Session session, List<PatientMatch> matches, boolean rejected);

    /**
     * Commits the notification marking transaction. See also {@code startNotificationMarkingTransaction} and
     * {@code markNotified}.
     *
     * @param session the transaction session created for marking.
     * @return true if successful
     */
    boolean endNotificationMarkingTransaction(Session session);
}
