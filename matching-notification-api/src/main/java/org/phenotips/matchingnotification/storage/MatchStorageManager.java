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

/**
 * @version $Id$
 */
@Role
public interface MatchStorageManager
{
    /**
     * Stores match.
     *
     * @param matches to store
     */
    void saveMatches(List<PatientMatch> matches);

    /**
     * Loads and returns all matches in a table.
     *
     * @return a list of all matches in the table
     */
    List<PatientMatch> loadAllMatches();

    /**
     * Load all matches with ids in {@link matchesIds}.
     *
     * @param matchesIds list of ids of matches to load
     * @return list of matches
     */
    List<PatientMatch> loadMatchesByIds(List<Long> matchesIds);

    /**
     * TODO remove, for debug.
     */
    void clearMatches();
}
