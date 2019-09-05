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

import java.util.Set;

import javax.ws.rs.core.Response;

import org.json.JSONObject;

/**
 * @version $Id$
 */
@Role
public interface MatchFinderManager
{
    /**
     * Finds matches for all local patients on the selected servers.
     * For each patient each matcher used will check if the patient can be matched using the matcher
     * (e.g. patient is "matchable", or a "matchable" consent is granted, etc.).
     *
     * All matches that are found will be stored in the matching notification table.
     *
     * @param serverIds a list of servers to be used for matches search indicated by their ids.
     * @param onlyCheckPatientsUpdatedAfterLastRun if true, the selected matcher(s) will only re-check
     *            patients which have been modified after the last time that matcher was run
     */
    void findMatchesForAllPatients(Set<String> serverIds, boolean onlyCheckPatientsUpdatedAfterLastRun);

    /**
     * Finds matches for a local patient on the selected server.
     *
     * All matches that are found will be stored in the matching notification table.
     *
     * @param patient local reference patient
     * @param serverId server to be used for matches search
     * @return a response containing a success message or an error code if unsuccessful
     */
    Response findMatchesForPatient(Patient patient, String serverId);

    /**
     * Returns a JSON object containing last matches update details for a provided {@code patientId patient}
     * for all servers (local matches or MME matches).
     *
     * @param patientId local patient ID
     * @return JSON containing, for each configured server(including local), dates of the last match
     *         update request (if any) and last successful match update request (if any, can be the same as
     *         the last request), as well as the last error iff the last request generated an error,
     *         in the following format:
     *
     *         {
     *           server_id: { "lastSuccessfulMatchUpdateDate": date (or null if there were no successful requests),
     *                        "lastMatchUpdateDate": date (or null if there were no match requests to this server),
     *                        "lastMatchUpdateErrorCode": HTTP error code (if the last macth update failed, optional),
     *                        "lastMatchUpdateError": a JSON with error details (if the was an error, optional)
     *                      },
     *           ...
     *         }
     */
    JSONObject getLastMatchUpdateStatus(String patientId);
}
