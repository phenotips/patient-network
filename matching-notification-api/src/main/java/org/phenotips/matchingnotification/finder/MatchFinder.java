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

import org.xwiki.component.annotation.Role;

import java.util.List;
import java.util.Set;

import javax.ws.rs.core.Response;

import org.json.JSONObject;

/**
 * @version $Id$
 */
@Role
public interface MatchFinder
{
    /**
     * @return a positive number representing the priority of current match finder
     */
    int getPriority();

    /**
     * @return list of supported servers for matches search
     */
    Set<String> getSupportedServerIdList();

    /**
     * Finds matches for all patients.
     *
     * @param patientIds List of local patients IDs
     * @param serverIds a list of servers to be used for matches search indicated by their ids. Servers which
     *                  are not supported by the given matcher will be silently ignored.
     * @param onlyUpdatedAfterLastRun if true, only considers patients updated after the last time matcher was run
     * @return number of matches found
     */
    int findMatches(List<String> patientIds, Set<String> serverIds, boolean onlyUpdatedAfterLastRun);

    /**
     * Finds matches for a given patient.
     *
     * @param patient local reference patient
     * @param serverId the remote or local server to be queried for matching patients
     * @return a response containing a success message or an error code if unsuccessful
     */
    Response findMatches(Patient patient, String serverId);

    /**
     * Finds last matches update status for a provided {@code patientId patient} for the selected server.
     *
     * @param patientId local patient ID
     * @param serverId the remote or local server
     *
     * @return JSON containing dates of the last match update request (if any) and last successful
     *         match update request (if any, can be the same as the last request), as well as the
     *         last error iff the last request generated an error, in the following format:
     *
     *         {
     *           "lastSuccessfulMatchUpdateDate": date (or null if there were no successful requests),
     *           "lastMatchUpdateDate": date (or null if there were no match requests to this server),
     *           "lastMatchUpdateErrorCode": HTTP error code (if the last macth update failed, optional),
     *           "lastMatchUpdateError": a JSON with error details (if the was an error, optional)
     *         }
     */
    JSONObject getLastUpdatedDateForServerForPatient(String patientId, String serverId);
}
