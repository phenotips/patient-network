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

import org.phenotips.data.rest.PatientsResource;
import org.phenotips.rest.PATCH;
import org.phenotips.rest.ParentResource;
import org.phenotips.rest.Relation;

import org.xwiki.stability.Unstable;

import java.util.Set;

import javax.annotation.Nullable;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
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
@Path("/matches")
@Relation("https://phenotips.org/rel/matchingNotification")
@ParentResource(PatientsResource.class)
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
     * @return HTTP OK if successful, HTTP FORBIDDEN if current user is not admin.
     *         Note that refreshing all matches may take a very long time and the HTTP request may time out,
     *         even though matches are still being refreshed with - this is expected, match update should
     *         still finish with no problems.
     */
    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    Response refreshMatches(
        @FormParam("serverIds") Set<String> serverIds,
        @FormParam("onlyCheckPatientsUpdatedAfterLastRun") @DefaultValue("false") boolean
        onlyCheckPatientsUpdatedAfterLastRun);

    /**
     * Finds matches for a provided {@code patientId patient} for the selected server (local matches or MME matches).
     *
     * All matches will be stored in the matching notification table (if a match between the same two patients
     * is already in the table, it will be replaced by the new match, in effect "refreshing" the match).
     *
     * @param patientId the internal identifier for a local reference patient; must be specified
     * @param serverId the remote or local server to be queried for matching patients; must be specified
     * @return a response containing a success message or an error code if unsuccessful
     */
    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/patients/{patientId}")
    Response refreshMatchesForPatient(@PathParam("patientId") String patientId,
        @FormParam("serverId") String serverId);

    /**
     * Returns a JSON object containing all matches or matches owned by logged user (if not admin), filtered by
     * parameters. The following additional parameters may be specified:
     *
     * @param minScore only matches with general score higher or equal to this value are returned
     * @param minPhenScore only matches with phenotypic score higher or equal to this value are returned
     * @param minGenScore only matches with genotypic score higher or equal to this value are returned
     * @param fromDate if passed a date in the {@code yyyy-MM-dd} format, then only matches found on or after this date
     *        will be returned; if {@code null} or an empty string, then no lower limit on the match date is considered
     * @param toDate if passed a date in the {@code yyyy-MM-dd} format, then only matches found on or before this date
     *        will be returned; if {@code null} or an empty string, then no upper limit on the match date is considered
     * @return a response containing a JSON object with a list of matches
     */
    @GET
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    Response getMatches(@FormParam("minScore") @DefaultValue("0.5") double minScore,
        @FormParam("minPhenScore") @DefaultValue("0") double minPhenScore,
        @FormParam("minGenScore") @DefaultValue("0.1") double minGenScore,
        @FormParam("fromDate") @DefaultValue("") String fromDate,
        @FormParam("toDate") @DefaultValue("") String toDate);

    /**
     * Returns a JSON object containing all matches for a provided {@code reference patient}, filtered by
     * parameters. The following additional parameters may be specified:
     *
     * @param patientId the patient identifier whose matches we want to load
     * @param minScore only matches with general score higher or equal to this value are returned
     * @param minPhenScore only matches with phenotypic score higher or equal to this value are returned
     * @param minGenScore only matches with genotypic score higher or equal to this value are returned
     * @param fromDate if passed a date in the {@code yyyy-MM-dd} format, then only matches found on or after this date
     *        will be returned; if {@code null} or an empty string, then no lower limit on the match date is considered
     * @param toDate if passed a date in the {@code yyyy-MM-dd} format, then only matches found on or before this date
     *        will be returned; if {@code null} or an empty string, then no upper limit on the match date is considered
     * @return a response containing a JSON object with a list of matches
     */
    @GET
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/patients/{patientId}")
    Response getMatchesForPatient(@PathParam("patientId") String patientId,
        @FormParam("minScore") @DefaultValue("0.1") double minScore,
        @FormParam("minPhenScore") @DefaultValue("0") double minPhenScore,
        @FormParam("minGenScore") @DefaultValue("0") double minGenScore,
        @FormParam("fromDate") @DefaultValue("") String fromDate,
        @FormParam("toDate") @DefaultValue("") String toDate);

    /**
     * Sends email notifications for each match using the "admin" email template.
     * All patient IDs are assumed to be local IDs, and all recipients must/will be local users.
     *
     * Example:
     * <pre>
     *  Input JSON: {"ids": [{'matchId' : 58, 'patientId' : P000067}, {'matchId' : 7, 'patientId' : P000035}]}
     *  Output JSON: {"results": {"success":[a list of match ids], "failed": [a list of match ids]}}
     * </pre>
     * <dl>
     * <dt>matchesToNotify</dt>
     * <dd>a JSON object (in the format as described above) with a list of match ids that given patients ids
     * should be notified about</dd>
     * </dl>
     *
     * @return result JSON
     */
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/email")
    Response sendAdminNotificationsToLocalUsers();

    /**
     * Sends a email notification for a given match using the "user" email template.
     * The target patient/user may be either local or remote (MME).
     *
     * @param matchId the id of the match that the other user should be notified about
     * @param patientId the id of the patient that is the subject of the email
     * @param serverId the server that hosts the patient
     * @param emailText (optional) custom text edited/created by the user
     * @param emailSubject (optional) custom subject edited/created by the user
     * @return result JSON
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{matchId}/email")
    Response sendUserNotification(@PathParam("matchId") Long matchId,
        @FormParam("subjectPatientId") String patientId,
        @FormParam("subjectServerId") String serverId,
        @FormParam("emailText") @DefaultValue("") String emailText,
        @FormParam("emailSubject") @DefaultValue("") String emailSubject);

    /**
     * Returns the content of email to be sent when a user hits "contact this match" button.
     *
     * @param matchId the id of the match that should be used to generate the email
     * @param patientId the id of the patient that is the subject of the email (can be either of
     *        the two patients involved in the specified match)
     * @param serverId the server that hosts the patient
     * @return a response containing a JSON object, in the following format:
     *     <pre>
     *      { "emailContent": text,
     *        "recipients": { "to": list_of_email_addresses_as_strings, "from": list, "cc": list },
     *        "contentType": type,
     *        "subject": text }
     *     </pre>
     *     where text is a string, and type is the type of content as string (for now only
     *     "text/plain" is supported) TODO: support text/html as well
     */
    @GET
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.TEXT_HTML)
    @Path("/{matchId}/email")
    Response getEmailToBeSent(@PathParam("matchId") Long matchId,
        @FormParam("subjectPatientId") String patientId,
        @FormParam("subjectServerId") String serverId);

    /**
     * Update match status, user-contacted state, comment or note.
     *
     * @param matchId the internal ID of the match of interest
     * @param status whether match status should be set as 'saved', 'rejected' or 'uncategorized'
     * @param isUserContacted boolean user-contacted status to set for the match
     * @param comment comment text
     * @param note note text
     * @return updated match JSON
     */
    @PATCH
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{matchId}")
    Response updateMatch(@PathParam("matchId") Long matchId,
        @Nullable @FormParam("status") String status,
        @Nullable @FormParam("isUserContacted") Boolean isUserContacted,
        @Nullable @FormParam("comment") String comment,
        @Nullable @FormParam("note") String note);

    /**
     * Returns a JSON object containing match details.
     *
     * @param matchId the internal ID of the match of interest
     * @return match JSON
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{matchId}")
    Response getMatch(@PathParam("matchId") Long matchId);

    /**
     * Returns a JSON object containing last matches update details for a provided {@code patientId patient}
     * for all servers (local matches or MME matches).
     *
     * @param patientId the internal ID of the local patient of interest
     * @return JSON containing, for each configured server(including local), dates of the last match
     *         update request (if any) and last successful match update request (if any, can be the same as
     *         the last request), as well as the last error iff the last request generated an error,
     *         in the following format:
     *
     *         {
     *           server_id: { "lastSuccessfulMatchUpdateDate": date (or null if there were no successful requests),
     *                        "lastMatchUpdateDate": date (or null if there were no match requests to this server),
     *                        "lastMatchUpdateErrorCode": HTTP error code (if the last macth update failed, optional),
     *                        "lastMatchUpdateError": a JSON with error details (if there was an error, optional)
     *                      },
     *           ...
     *         }
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/patients/{patientId}/updated")
    Response getLastMatchUpdateStatus(@PathParam("patientId") String patientId);
}
