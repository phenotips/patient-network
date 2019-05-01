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
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
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
     */
    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    void refreshMatches(
        @FormParam("serverIds") Set<String> serverIds,
        @FormParam("onlyCheckPatientsUpdatedAfterLastRun") @DefaultValue("false") boolean
        onlyCheckPatientsUpdatedAfterLastRun);

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
    Response getMatches(@QueryParam("minScore") @DefaultValue("0.5") double minScore,
        @QueryParam("minPhenScore") @DefaultValue("0") double minPhenScore,
        @QueryParam("minGenScore") @DefaultValue("0.1") double minGenScore,
        @QueryParam("fromDate") @DefaultValue("") String fromDate,
        @QueryParam("toDate") @DefaultValue("") String toDate);

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
     * <dt>ids</dt>
     * <dd>a JSON object (in the format as described above) with a list of match ids that given patients ids
     * should be notified about</dd>
     * </dl>
     *
     * @return result JSON
     */
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/send-admin-local-notifications")
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
    @Path("/send-user-notifications")
    Response sendUserNotification(@FormParam("matchId") String matchId,
        @FormParam("subjectPatientId") String patientId,
        @FormParam("subjectServerId") String serverId,
        @FormParam("emailText") @DefaultValue("") String emailText,
        @FormParam("emailSubject") @DefaultValue("") String emailSubject);

    /**
     * Returns the content of email to be sent when a user hits "contact this match" button.
     *
     * @param matchId the id of the match that should be used to generate the email
     * @param patientId the id of the patient that is the subject of the email (can be either of
     *                  the two patients involved in the specified match)
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
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/preview-user-match-email")
    Response getEmailToBeSent(@FormParam("matchId") String matchId,
        @FormParam("subjectPatientId") String patientId,
        @FormParam("subjectServerId") String serverId);

    /**
     * Update match status, user-contacted state, comment or note.
     *
     * @param matchId the internal ID of the matchId of interest
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
}
