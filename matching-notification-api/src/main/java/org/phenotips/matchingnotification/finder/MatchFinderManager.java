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

import org.xwiki.component.annotation.Role;

import java.util.Set;

/**
 * @version $Id$
 */
@Role
public interface MatchFinderManager
{
    /**
     * Finds matches for all local patients for selected servers. For each patient each matcher used will check if the
     * patient can be matched using the matcher
     * (e.g. patient is "matchable", or a "matchable" consent is granted, etc.).
     *
     * As a side effect, all matches that are found will be stored in the matching notification table.
     *
     * @param serverIds a list of servers to be used for matches search indicated by their ids.
     * @param onlyCheckPatientsUpdatedAfterLastRun if true, the selected matcher(s) will only re-check
     *            patients which have been modified since that matcher was run
     */
    void findMatchesForAllPatients(Set<String> serverIds, boolean onlyCheckPatientsUpdatedAfterLastRun);
}
