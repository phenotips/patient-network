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
package org.phenotips.matchingnotification.rest.internal;

import org.phenotips.matchingnotification.MatchingNotificationManager;
import org.phenotips.matchingnotification.export.PatientMatchExport;
import org.phenotips.matchingnotification.finder.MatchFinderManager;
import org.phenotips.matchingnotification.match.PatientMatch;
import org.phenotips.matchingnotification.notification.PatientMatchNotificationResponse;
import org.phenotips.matchingnotification.rest.MatchingNotificationResource;
import org.phenotips.matchingnotification.storage.MatchStorageManager;
import org.phenotips.security.authorization.AuthorizationService;

import org.xwiki.component.annotation.Component;
import org.xwiki.container.Container;
import org.xwiki.container.Request;
import org.xwiki.model.EntityType;
import org.xwiki.model.reference.EntityReferenceResolver;
import org.xwiki.rest.XWikiResource;
import org.xwiki.security.authorization.Right;
import org.xwiki.users.UserManager;

import java.security.AccessControlException;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Default implementation of the {@link MatchingNotificationResource}.
 *
 * @version $Id$
 * @since 1.1
 */
@Component
@Named("org.phenotips.matchingnotification.rest.internal.DefaultMatchingNotificationResource")
@Singleton
public class DefaultMatchingNotificationResource extends XWikiResource implements MatchingNotificationResource
{
    private static final Double MIN_MATCH_SCORE_FOR_NONADMIN_USERS = 0.3;

    private static final String REQ_NO = "reqNo";

    private static final String IDS_STRING = "ids";

    private static final String RESULTS_LABEL = "results";

    private static final String RETURNED_SIZE_LABEL = "returnedCount";

    private static final String PATIENTID_STRING = "patientId";

    private static final String MATCHID_STRING = "matchId";

    private static final SimpleDateFormat DATE_TIME_SDF = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");

    private static final SimpleDateFormat DATE_SDF = new SimpleDateFormat("yyyy/MM/dd");

    private static final int ONE_DAY_IN_MILLISECONDS = 1000 * 60 * 60 * 24 - 1;

    @Inject
    private PatientMatchExport patientMatchExport;

    @Inject
    private MatchingNotificationManager matchingNotificationManager;

    @Inject
    private MatchStorageManager matchStorageManager;

    @Inject
    private MatchFinderManager matchFinderManager;

    @Inject
    private Container container;

    @Inject
    private AuthorizationService auth;

    @Inject
    private UserManager users;

    @Inject
    private EntityReferenceResolver<String> resolver;

    @Override
    public void refreshMatches(Set<String> serverIds, boolean onlyCheckPatientsUpdatedAfterLastRun)
    {
        this.matchFinderManager.findMatchesForAllPatients(serverIds, onlyCheckPatientsUpdatedAfterLastRun);
    }

    @Override
    public Response getMatches(@Nullable final double score, @Nullable final double phenScore,
        @Nullable final double genScore, final String fromDate, final String toDate)
    {
        Date from = null;
        if (StringUtils.isNotBlank(fromDate)) {
            try {
                from = DATE_SDF.parse(fromDate);
            } catch (ParseException e) {
                this.slf4Jlogger.error("Failed to parse fromDate parameter from: " + fromDate);
                return Response.status(Response.Status.BAD_REQUEST).build();
            }
        }

        Date to = null;
        if (StringUtils.isNotBlank(toDate)) {
            try {
                to = DATE_SDF.parse(toDate);
            } catch (ParseException e) {
                this.slf4Jlogger.error("Failed to parse toDate parameter from: " + toDate);
                return Response.status(Response.Status.BAD_REQUEST).build();
            }
        }

        final Request request = this.container.getRequest();
        final int reqNo = NumberUtils.toInt((String) request.getProperty(REQ_NO), 1);

        double useScore = this.isCurrentUserAdmin() ? score : Math.max(score, MIN_MATCH_SCORE_FOR_NONADMIN_USERS);

        try {
            return getMatchesResponse(useScore, phenScore, genScore, reqNo, from, to);
        } catch (final SecurityException e) {
            this.slf4Jlogger.error("Failed to retrieve matches: {}", e.getMessage(), e);
            return Response.status(Response.Status.UNAUTHORIZED).build();
        } catch (final Exception e) {
            this.slf4Jlogger.error("Unexpected exception while generating matches JSON: {}", e.getMessage(), e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Override
    public Response sendAdminNotificationsToLocalUsers()
    {
        if (!isCurrentUserAdmin()) {
            this.slf4Jlogger.error("Non-admins cant use admin notification API");
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        final Request request = this.container.getRequest();
        final String ids = (String) request.getProperty(IDS_STRING);

        if (StringUtils.isBlank(ids)) {
            this.slf4Jlogger.error("The requested ids parameter is blank and thus not a valid input JSON");
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        Map<Long, List<String>> idsList = this.jsonToIdsMap(ids);
        List<PatientMatchNotificationResponse> notificationResults =
            this.matchingNotificationManager.sendAdminNotificationsToLocalUsers(idsList);

        // create result JSON. The successfullyNotified list is used to take care of a case
        // where there is match that was supposed to be notified but no response was received on it.
        JSONObject notificationHistory = new JSONObject();
        List<Long> successfullyNotified = new LinkedList<>();
        for (PatientMatchNotificationResponse response : notificationResults) {
            if (response.isSuccessul()) {
                PatientMatch match = response.getPatientMatch();
                Long matchId = match.getId();
                successfullyNotified.add(matchId);
                notificationHistory.put(matchId.toString(), match.getNotificationHistory());
            }
        }

        JSONObject result = this.successfulIdsToJSON(new ArrayList<>(idsList.keySet()),
            successfullyNotified);
        result.put("successNotificationHistories", notificationHistory);
        return Response.ok(result, MediaType.APPLICATION_JSON_TYPE).build();
    }

    @Override
    public Response sendUserNotification(String matchId, String patientId, String serverId,
        String emailText, String emailSubject)
    {
        try {
            Long numericMatchId = Long.parseLong(matchId);

            PatientMatchNotificationResponse notificationResult =
                this.matchingNotificationManager.sendUserNotification(numericMatchId, patientId, serverId,
                    StringUtils.isBlank(emailText) ? null : emailText,
                    StringUtils.isBlank(emailSubject) ? null : emailSubject);

            if (notificationResult == null) {
                // unable to send mail, but not because input is wrong
                this.slf4Jlogger.error("Could not send user notifications for match with id=[{}]", matchId);
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
            }

            JSONObject result;
            if (notificationResult.isSuccessul()) {
                result = this.successfulIdsToJSON(Collections.singletonList(numericMatchId),
                    Collections.singletonList(numericMatchId));
                PatientMatch match = notificationResult.getPatientMatch();
                JSONObject notificationHistory = new JSONObject();
                notificationHistory.put(match.getId().toString(), match.getNotificationHistory());
                result.put("successNotificationHistories", notificationHistory);
            } else {
                result = this.successfulIdsToJSON(Collections.singletonList(numericMatchId),
                    Collections.<Long>emptyList());
            }
            return Response.ok(result, MediaType.APPLICATION_JSON_TYPE).build();
        } catch (AccessControlException ex) {
            this.slf4Jlogger.error("No rights to notify match id [{}]", matchId);
            return Response.status(Response.Status.FORBIDDEN).build();
        } catch (IllegalArgumentException ex) {
            // note: also catches NumberFormatException generated by Long.parseLong()
            this.slf4Jlogger.error("Match id is not valid: [{}]", matchId);
            return Response.status(Response.Status.BAD_REQUEST).build();
        } catch (Exception ex) {
            this.slf4Jlogger.error("Could not send user notifications for match with id=[{}]: [{}]",
                matchId, ex.getMessage(), ex);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Override
    public Response getEmailToBeSent(String matchId, String patientId, String serverId)
    {
        try {
            Long numericMatchId = Long.parseLong(matchId);

            JSONObject result = this.matchingNotificationManager.getUserEmailContent(
                numericMatchId, patientId, serverId);

            return Response.ok(result, MediaType.APPLICATION_JSON_TYPE).build();
        } catch (NumberFormatException ex) {
            this.slf4Jlogger.error("Match id is not valid: [{}]", matchId, ex);
            return Response.status(Response.Status.BAD_REQUEST).build();
        } catch (Exception ex) {
            this.slf4Jlogger.error("Could not get email content for match with id=[{}]: [{}]",
                matchId, ex.getMessage(), ex);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Override
    public Response setStatus(final Set<Long> matchesIds, final String status)
    {
        if (matchesIds.isEmpty()) {
            this.slf4Jlogger.error("The requested ids list is blank");
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        if (StringUtils.isBlank(status)) {
            this.slf4Jlogger.error("The 'status' request parameter is blank");
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        boolean success = this.matchingNotificationManager.setStatus(matchesIds, status);
        JSONObject result = this.successfulIdsToJSON(matchesIds, success ? matchesIds
            : Collections.<Long>emptyList());
        return Response.ok(result, MediaType.APPLICATION_JSON_TYPE).build();
    }

    @Override
    public Response setUserContacted(final Set<Long> matchesIds, boolean isUserContacted)
    {
        if (matchesIds.isEmpty()) {
            this.slf4Jlogger.error("The requested ids list is blank");
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        boolean success = this.matchingNotificationManager.setUserContacted(matchesIds, isUserContacted);
        JSONObject result = this.successfulIdsToJSON(matchesIds, success ? matchesIds
            : Collections.<Long>emptyList());
        return Response.ok(result, MediaType.APPLICATION_JSON_TYPE).build();
    }

    @Override
    public Response saveComment(final Set<Long> matchesIds, final String comment)
    {
        if (matchesIds.isEmpty()) {
            this.slf4Jlogger.error("The requested ids list is blank");
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        if (StringUtils.isBlank(comment)) {
            this.slf4Jlogger.error("The 'comment' request parameter is blank");
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        List<PatientMatch> successfulMatches = this.matchingNotificationManager.saveComment(matchesIds, comment);
        JSONObject result = this.successfulMatchesCommentsToJSON(new LinkedList<>(matchesIds), successfulMatches);
        return Response.ok(result, MediaType.APPLICATION_JSON_TYPE).build();
    }

    @Override
    public Response addNote(Set<Long> matchesIds, String note)
    {
        if (matchesIds.isEmpty()) {
            this.slf4Jlogger.error("The requested ids list is blank");
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        if (StringUtils.isBlank(note)) {
            this.slf4Jlogger.error("The 'note' request parameter is blank");
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        boolean success = this.matchingNotificationManager.addNote(matchesIds, note);
        JSONObject result = this.successfulIdsToJSON(matchesIds, success ? matchesIds
            : Collections.<Long>emptyList());
        return Response.ok(result, MediaType.APPLICATION_JSON_TYPE).build();
    }

    private Response getMatchesResponse(@Nullable final double score, @Nullable final double phenScore,
        @Nullable final double genScore, final int reqNo, final Date fromDate, final Date toDate)
    {
        boolean loadOnlyUserMatches = !isCurrentUserAdmin();

        Timestamp timestampFrom = null;
        if (fromDate != null) {
            timestampFrom = new Timestamp(fromDate.getTime());
        }

        Timestamp timestampTo = null;
        if (toDate != null) {
            // need to add 1 day without the last millisecond in ms
            // to let user know that we include all matches found that day
            timestampTo = new Timestamp(toDate.getTime() + ONE_DAY_IN_MILLISECONDS);
        }

        List<PatientMatch> matches = this.matchStorageManager.loadMatches(
            score, phenScore, genScore, loadOnlyUserMatches, timestampFrom, timestampTo);

        JSONObject matchesJson = new JSONObject();

        if (!matches.isEmpty()) {
            matchesJson = buildMatchesJSONArray(matches);
            matchesJson.put(REQ_NO, reqNo);
        }

        JSONObject params = new JSONObject();
        params.put("minScore", score);
        params.put("minPhenScore", phenScore);
        params.put("minGenScore", genScore);

        if (fromDate != null) {
            params.put("fromDate", DATE_TIME_SDF.format(timestampFrom));
        }

        if (toDate != null) {
            params.put("toDate", DATE_TIME_SDF.format(timestampTo));
        }
        matchesJson.put("parameters", params);
        matchesJson.put("dateGenerated", DATE_TIME_SDF.format(new Date()));
        return Response.ok(matchesJson, MediaType.APPLICATION_JSON_TYPE).build();
    }

    /**
     * Builds a JSON representation of {@link #getMatches()}.
     *
     * @param matches list of found {@link PatientMatch} objects
     * @return a {@link JSONArray} of {@link #getMatches()}
     */
    private JSONObject buildMatchesJSONArray(List<PatientMatch> matches)
        throws IndexOutOfBoundsException, IllegalArgumentException
    {
        final JSONObject matchesJson = this.patientMatchExport.toJSON(matches);
        return new JSONObject()
            .put(RESULTS_LABEL, matchesJson.optJSONArray("matches"))
            .put(RETURNED_SIZE_LABEL, matchesJson.optJSONArray("matches").length());
    }

    private JSONObject successfulIdsToJSON(Collection<Long> allIds, Collection<Long> successfulIds)
    {
        JSONObject result = new JSONObject();
        result.put("success", successfulIds);
        Collection<Long> failedIds = new LinkedList<>(allIds);
        failedIds.removeAll(successfulIds);
        result.put("failed", failedIds);
        JSONObject reply = new JSONObject();
        reply.put("results", result);
        return reply;
    }

    private JSONObject successfulMatchesCommentsToJSON(Collection<Long> allIds, List<PatientMatch> successfulMatches)
    {
        JSONObject result = new JSONObject();
        Collection<Long> failedIds = new LinkedList<>(allIds);
        JSONObject comments = new JSONObject();
        for (PatientMatch match : successfulMatches) {
            failedIds.remove(match.getId());
            comments.put(match.getId().toString(), match.getComments());
        }
        allIds.removeAll(failedIds);
        result.put("success", allIds);
        result.put("failed", failedIds);
        result.put("comments", comments);
        JSONObject reply = new JSONObject();
        reply.put("results", result);
        return reply;
    }

    private Map<Long, List<String>> jsonToIdsMap(String idsJson)
    {
        Map<Long, List<String>> ids = new HashMap<>();
        try {
            if (StringUtils.isNotEmpty(idsJson)) {
                JSONObject idsObject = new JSONObject(idsJson);
                if (idsObject.has(IDS_STRING)) {
                    JSONArray idsJSONArray = idsObject.getJSONArray(IDS_STRING);
                    for (Object item : idsJSONArray) {
                        JSONObject obj = (JSONObject) item;
                        List<String> patientIds = ids.get(obj.optLong(MATCHID_STRING));
                        if (patientIds == null) {
                            patientIds = new LinkedList<String>();
                        }
                        patientIds.add(obj.optString(PATIENTID_STRING));
                        ids.put(obj.optLong(MATCHID_STRING), patientIds);
                    }
                }
            }
        } catch (JSONException ex) {
            this.slf4Jlogger.error("Error converting a JSON map {} to a map: {}", idsJson, ex);
        }

        return ids;
    }

    private boolean isCurrentUserAdmin()
    {
        return this.auth.hasAccess(this.users.getCurrentUser(), Right.ADMIN,
            this.resolver.resolve("", EntityType.WIKI));
    }

    @Override
    public Response getLastOutgoingMatchId(String referencePatientId, String referenceServerId,
        String matchedPatientId, String matchedServerId)
    {
        if (StringUtils.isBlank(referencePatientId) || StringUtils.isBlank(matchedPatientId)) {
            this.slf4Jlogger
                .error("One of the requested patient ids parameter is blank and thus not a valid input JSON");
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        List<PatientMatch> matches = this.matchStorageManager.loadMatchesBetweenPatients(
            referencePatientId, referenceServerId, matchedPatientId, matchedServerId);

        JSONObject matchIDJson = new JSONObject();

        if (!matches.isEmpty()) {
            matchIDJson.put(MATCHID_STRING, getLastOutgoingMatchId(matches));
        }
        return Response.ok(matchIDJson, MediaType.APPLICATION_JSON_TYPE).build();
    }

    private Long getLastOutgoingMatchId(List<PatientMatch> matches)
    {
        Long id = null;
        Timestamp lastTime = null;
        for (PatientMatch match : matches) {
            if ((match.isOutgoing() || match.isLocal())
                && (lastTime == null || lastTime.before(match.getFoundTimestamp()))) {
                id = match.getId();
                lastTime = match.getFoundTimestamp();
            }
        }
        return id;
    }
}
