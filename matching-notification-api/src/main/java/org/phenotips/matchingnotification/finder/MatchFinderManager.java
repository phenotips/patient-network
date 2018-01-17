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
package org.phenotips.matchingnotification.finder;

import org.phenotips.data.Patient;
import org.phenotips.matchingnotification.match.PatientMatch;

import org.xwiki.component.annotation.Role;

import java.util.List;

/**
 * @version $Id$
 */
@Role
public interface MatchFinderManager
{
    /**
     * Finds matches for all local patients which are matchable (by querying both local instance
     * and all enabled MME servers - the latter only for patients which have MME consent enabled).
     *
     * The matches will be stored in matching notification table.
     */
    void findMatchesForAllPatients();

    /**
     * Finds matches for a given patient (by querying both local instance
     * and all enabled MME servers - the latter only if MME consent is enabled for the patient).
     *
     * As a side effect, the matches will be stored in matching notification table.
     *
     * @param patient to find matches for. No check is made to make sure the patient is matchable.
     * @return list of matches
     */
    List<PatientMatch> findMatches(Patient patient);
}
