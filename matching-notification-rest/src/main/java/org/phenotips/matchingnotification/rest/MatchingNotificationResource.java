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
     * Finds matches for all (eligible) local patients on the selected servers (local matches or MME matches).
     *
     * Different servers may have different "matching eligibility" criteria, e.g. a patient may have to be "matchable",
     * and/or a "matchable" consent should be granted, etc.
     *
     * All matches will be stored in the matching notification table (if a match between the same two patients
     * is already in the table, it will be replaced by the new match, in effect "refreshing" the match).
     *
     * @param serverIds list or server IDs selected for matches search
     * @param onlyCheckPatientsUpdatedAfterLastRun if true, for each server only local patients which have been
     *            modified after the last time this refresh was run for that server will be tested for matches
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/refresh-matches")
    void refreshMatches(
        @FormParam("serverIds") Set<String> serverIds,
        @FormParam("onlyCheckPatientsUpdatedAfterLastRun") @DefaultValue("false")
        boolean onlyCheckPatientsUpdatedAfterLastRun);

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
     * @param onlyNotified when true only loads matches that have been notified
     * @return a response containing a JSON object with a list of matches
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/show-matches")
    Response getMatches(@FormParam("score") @DefaultValue("0.5") double score,
        @FormParam("phenScore") @DefaultValue("0") double phenScore,
        @FormParam("genScore") @DefaultValue("0.1") double genScore,
        @FormParam("onlyNotified") @DefaultValue("false") boolean onlyNotified);

    /**
     * Sends email notifications for each match. Example:
     *
     * <pre>
     *  Input JSON: {"ids": [{'matchId' : 58, 'patientId' : P000067}, {'matchId' : 7, 'patientId' : P000035}]}
     *  Output JSON: {"results": {"success":[a list of match ids], "failed": [a list of match ids]}}
     * </pre>
     * <dl>
     * <dt>ids</dt>
     * <dd>a JSON object (in the format as described above) with a list of match ids that given patients ids
     * should be notified about</dd>
     * </dl>
     *
     * @return result JSON
     */
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/send-notifications")
    Response sendNotifications();

    /**
     * Returns the content of email to be sent when a user hits "contact this match" button.
     *
     * @param matchId the id of the match that should be used to generate the email
     * @param patientId the id of the patient that is the subject of the email (can be either of
     *                  the two patients involved in the specified match)
     * @return a response containing a JSON object, in the following format:
     *     <pre>
     *      { "emailContent": text,
     *        "recipients": [listof email addresses as strings],
     *        "contentType": type,
     *        "subject": text }
     *     </pre>
     *     where text is a string, and type is the type of content as string (e.g. "text/plain")
     *     TODO: check what other types we may be returning
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/preview-match-email")
    Response getEmailToBeSent(@FormParam("matchId") String matchId,
            @FormParam("subjectPatientId") String patientId);

    /**
     * Marks matches, with ids given in parameter, as saved, rejected or uncategorized. Example:
     *
     * <pre>
     * Input: ["1", "2"], "saved"
     * Output: {"results": {"success": [1,2]}}
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
