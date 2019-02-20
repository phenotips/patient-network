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
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/refresh-matches")
    void refreshMatches(
        @FormParam("serverIds") Set<String> serverIds,
        @FormParam("onlyCheckPatientsUpdatedAfterLastRun") @DefaultValue("false") boolean
        onlyCheckPatientsUpdatedAfterLastRun);

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
     * @param fromDate if passed a date in the {@code yyyy/MM/dd} format, then only matches found on or after this date
     *        will be returned; if {@code null} or an empty string, then no lower limit on the match date is considered
     * @param toDate if passed a date in the {@code yyyy/MM/dd} format, then only matches found on or before this date
     *        will be returned; if {@code null} or an empty string, then no upper limit on the match date is considered
     * @return a response containing a JSON object with a list of matches
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/show-matches")
    Response getMatches(@FormParam("score") @DefaultValue("0.5") double score,
        @FormParam("phenScore") @DefaultValue("0") double phenScore,
        @FormParam("genScore") @DefaultValue("0.1") double genScore,
        @FormParam("fromDate") @DefaultValue("") String fromDate,
        @FormParam("toDate") @DefaultValue("") String toDate);

    /**
     * Sends email notifications for each match using the "admin" email emplate.
     * All patient IDs are assumed ot be local IDs, and all recepients must/will be local users.
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
     * Sends a email notification for a given match using the "user" email emplate.
     * The target patient/user may be either local or remote (MME).
     *
     * @param matchId the id of the match that the other user shoulbe notified about
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

    /**
     * Returns a JSON object containing a single match ID found by 4 parameters: reference patient ID,
     * matched patient ID, reference server ID, matched server ID.
     *
     * @param referencePatientId id of reference patient to load matches for
     * @param referenceServerId id of the server that hosts reference patient
     * @param matchedPatientId id of matched patient to load matches for
     * @param matchedServerId id of the server that hosts matched patient
     * @return a single matchID
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/get-matchid")
    Response getLastOutgoingMatchId(@FormParam("referencePatientId") String referencePatientId,
        @FormParam("referenceServerId") String referenceServerId,
        @FormParam("matchedPatientId") String matchedPatientId,
        @FormParam("matchedServerId") String matchedServerId);

    /**
     * Marks matches, with ids given in parameter, as user-contacted or not user-contacted. Example:
     *
     * <pre>
     * Input: ["1", "2"], "true"
     * Output: {"results": {"success": [1,2]}}
     * </pre>
     *
     * @param matchesIds List of matches IDs to change the status for
     * @param isUserContacted boolean user-contacted status to set for matches
     * @return result JSON
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/mark-user-contacted")
    Response setUserContacted(@FormParam("matchesIds") Set<Long> matchesIds,
        @FormParam("isUserContacted") @DefaultValue("false") boolean isUserContacted);

    /**
     * Saves comment for matches, with ids given in parameter.
     *
     * @param matchesIds List of matches IDs to change the status for
     * @param comment comment text
     * @return result JSON
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/save-comment")
    Response saveComment(@FormParam("matchesIds") Set<Long> matchesIds, @FormParam("comment") String comment);

    /**
     * Saves note for matches, with ids given in parameter.
     *
     * @param matchesIds List of matches IDs to save note for
     * @param note note text
     * @return result JSON
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/save-note")
    Response addNote(@FormParam("matchesIds") Set<Long> matchesIds, @FormParam("note") String note);
}
