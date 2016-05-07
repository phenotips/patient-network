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

import org.phenotips.matchingnotification.match.PatientMatch;
import org.phenotips.matchingnotification.notification.PatientMatchNotificationResponse;

import org.xwiki.component.annotation.Role;

import java.util.List;

/**
 * @version $Id$
 */
@Role
public interface MatchingNotificationManager
{
    /**
     * Find and save matches to all local patients.
     *
     * @param score save matches with score higher or equals to this value
     * @return true if successful
     */
    List<PatientMatch> findAndSaveMatches(double score);

    /**
     * Sends notification to the owner of every match with id in {@matchesId}, then marks match as notified.
     *
     * @param matchesIds list of ids of matches to be notified
     * @return a list of PatientmatchNotificationResponse
     */
    List<PatientMatchNotificationResponse> sendNotifications(List<Long> matchesIds);
}
