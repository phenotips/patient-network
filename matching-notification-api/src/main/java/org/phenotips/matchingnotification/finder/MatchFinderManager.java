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
import java.util.Set;

/**
 * @version $Id$
 */
@Role
public interface MatchFinderManager
{
    /**
     * Finds matches for all local patients. For each patient each matcher used will check if the patient can be
     * matched using the matcher (e.g. patient is "matchable", or a "matchable" consent is granted, etc.).
     *
     * As a side effect, all matches that are found will be stored in the matching notification table.
     *
     * @param matchersToUse a list of matchers to be used indicated by their internal names
     *            (see {@link MatchFinder#getName()}). If null, all matchers will be used
     * @param onlyCheckPatientsUpdatedAfterLastRun if true, the selected matcher(s) will only re-check
     *            patients which have been modified since that matcher was run
     */
    void findMatchesForAllPatients(Set<String> matchersToUse, boolean onlyCheckPatientsUpdatedAfterLastRun);

    /**
     * Finds matches for a given patient using all available matchers. Each matcher will do
     * their own check to make sure patient can be matched using the matcher (e.g. if a required consent is granted).
     *
     * All matchers will be run even if the patient has already been matched using the matcher
     * after it has been updated.
     *
     * As a side effect, the matches that are found will be stored in the matching notification table.
     *
     * @param patient to find matches for
     * @return list of matches
     */
    List<PatientMatch> findMatches(Patient patient);
}
