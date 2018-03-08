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
package org.phenotips.matchingnotification.rest;

import org.phenotips.data.rest.PatientResource;
import org.phenotips.rest.ParentResource;
import org.phenotips.rest.Relation;

import org.xwiki.stability.Unstable;

import java.util.Set;

import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.FormParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * Resource for working with patients matches and notifications.
 *
 * @version $Id$
 * @since 1.1
 */
@Unstable("New API introduced in 1.1")
@Path("/patients/matching-notification")
@Relation("https://phenotips.org/rel/matchingNotification")
@ParentResource(PatientResource.class)
public interface MatchingNotificationResource
{
    /**
     * Finds all matches between all (matchable) local patients or for selected remote servers. Matches will be stored
     * in the matching notification table.
     *
     * @param serverIds list or server IDs selected for matches search
     * @param onlyCheckPatientsUpdatedAfterLastRun if true, only patients which have been modified after the last time
     *            local matcher was run will be tested for matches
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/find-matches")
    void findMatches(
        @FormParam("serverIds") Set<String> serverIds,
        @FormParam("onlyCheckPatientsUpdatedAfterLastRun")
        @DefaultValue("false") boolean onlyCheckPatientsUpdatedAfterLastRun);

    /**
     * Returns a JSON object containing all matches or matches owned by logged user (if not admin), filtered by
     * parameters. The following additional parameters may be specified:
     * <dl>
     * <dt>reqNo</dt>
     * <dd>the request number, must be an integer; default value is set to 1</dd>
     * </dl>
     *
     * @param score only matches with general score higher or equal to this value are returned
     * @param phenScore only matches with phenotypic score higher or equal to this value are returned
     * @param genScore only matches with genotypic score higher or equal to this value are returned
     * @param notified whether the matches were notified of
     * @return a response containing a JSON object with a list of matches
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/show-matches")
    Response getMatches(@FormParam("score") @DefaultValue("0.5") double score,
        @FormParam("phenScore") @DefaultValue("0") double phenScore,
        @FormParam("genScore") @DefaultValue("0") double genScore,
        @FormParam("notified") @DefaultValue("false") boolean notified);

    /**
     * Sends email notifications for each match. Example:
     *
     * <pre>
     * Input: [{'matchId' : 58, 'patientId' : P000067}, {'matchId' : 7, 'patientId' : P000035}]
     * Output: {"results": [{"id": "58", "success": true}, {"id": "7", "success": false}]}
     * </pre>
     * <dl>
     * <dt>ids</dt>
     * <dd>map of ids of matches to patients ids to be notified</dd>
     * </dl>
     *
     * @return result JSON
     */
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/send-notifications")
    Response sendNotifications();

    /**
     * Marks matches, with ids given in parameter, as saved, rejected or uncategorized. Example:
     *
     * <pre>
     * Input: ["1", "2", "3"], "saved"
     * Output: {"results": [{"id": "1", "success": true}, {"id": "2", "success": false}, {"id": "3", "success": true}]}
     * </pre>
     *
     * @param matchesIds List of matches IDs to change the status for
     * @param status whether matches status should be set as 'saved', 'rejected' or 'uncategorized'
     * @return result JSON
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/set-status")
    Response setStatus(@FormParam("matchesIds") Set<Long> matchesIds, @FormParam("status") String status);
}