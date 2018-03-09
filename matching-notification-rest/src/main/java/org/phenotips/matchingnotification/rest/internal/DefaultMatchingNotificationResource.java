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

import org.phenotips.data.Patient;
import org.phenotips.data.permissions.AccessLevel;
import org.phenotips.data.permissions.Collaborator;
import org.phenotips.data.permissions.internal.PatientAccessHelper;
import org.phenotips.groups.Group;
import org.phenotips.groups.GroupManager;
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
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReferenceResolver;
import org.xwiki.rest.XWikiResource;
import org.xwiki.security.authorization.Right;
import org.xwiki.users.User;
import org.xwiki.users.UserManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;

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
    private static final String REQ_NO = "reqNo";

    private static final String IDS_STRING = "ids";

    private static final String RESULTS_LABEL = "results";

    private static final String RETURNED_SIZE_LABEL = "returnedCount";

    private static final String PATIENTID_STRING = "patientId";

    private static final String MATCHID_STRING = "matchId";

    @Inject
    private Logger logger;

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

    @Inject
    private PatientAccessHelper accessManager;

    /** Used for getting the list of groups a user belongs to. */
    @Inject
    private GroupManager groupManager;

    /** Needed for checking if a given access level provides read access to patients. */
    @Inject
    @Named("view")
    private AccessLevel viewAccess;

    @Override
    public void refreshMatches(Set<String> serverIds, boolean onlyCheckPatientsUpdatedAfterLastRun)
    {
        this.matchFinderManager.findMatchesForAllPatients(serverIds, onlyCheckPatientsUpdatedAfterLastRun);
    }

    @Override
    public Response getMatches(@Nullable final double score, @Nullable final double phenScore,
        @Nullable final double genScore, final boolean notified)
    {
        final Request request = this.container.getRequest();
        final int reqNo = NumberUtils.toInt((String) request.getProperty(REQ_NO), 1);

        try {
            return getMatchesResponse(score, phenScore, genScore, notified, reqNo);
        } catch (final SecurityException e) {
            this.logger.error("Failed to retrieve matches: {}", e.getMessage());
            return Response.status(Response.Status.UNAUTHORIZED).build();
        } catch (final Exception e) {
            this.logger.error("Unexpected exception while generating matches JSON: {}", e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Override
    public Response sendNotifications()
    {
        final Request request = this.container.getRequest();
        final String ids = (String) request.getProperty(IDS_STRING);

        if (StringUtils.isBlank(ids)) {
            this.logger.error("The requested ids list is blank");
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        Map<Long, List<String>> idsList = this.jsonToIdsMap(ids);
        List<PatientMatchNotificationResponse> notificationResults =
            this.matchingNotificationManager.sendNotifications(idsList);

        // create result JSON. The successfullyNotified list is used to take care of a case
        // where there is match that was supposed to be notified but no response was received on it.
        List<Long> successfullyNotified = new LinkedList<>();
        for (PatientMatchNotificationResponse response : notificationResults) {
            if (response.isSuccessul()) {
                successfullyNotified.add(response.getPatientMatch().getId());
            }
        }

        JSONObject result = this.successfulIdsToJSON(new ArrayList<>(idsList.keySet()),
            successfullyNotified);
        return Response.ok(result, MediaType.APPLICATION_JSON_TYPE).build();
    }

    @Override
    public Response setStatus(final Set<Long> matchesIds, final String status)
    {
        if (matchesIds.isEmpty()) {
            this.logger.error("The requested ids list is blank");
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        if (StringUtils.isBlank(status)) {
            this.logger.error("The 'status' request parameter is blank");
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        List<Long> idsList = new ArrayList<Long>(matchesIds);
        boolean success = this.matchingNotificationManager.setStatus(idsList, status);
        JSONObject result = this.successfulIdsToJSON(idsList, success ? idsList
            : Collections.<Long>emptyList());
        return Response.ok(result, MediaType.APPLICATION_JSON_TYPE).build();
    }

    private Response getMatchesResponse(@Nullable final double score, @Nullable final double phenScore,
        @Nullable final double genScore, final boolean notified, final int reqNo)
    {
        List<PatientMatch> matches = this.matchStorageManager.loadMatches(score, phenScore, genScore, notified);

        filterIrrelevantMatches(matches);

        JSONObject matchesJson = new JSONObject();

        if (!matches.isEmpty()) {
            matchesJson = buildMatchesJSONArray(matches);
            matchesJson.put(REQ_NO, reqNo);
        }
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

    private void filterIrrelevantMatches(List<PatientMatch> matches)
    {
        ListIterator<PatientMatch> iterator = matches.listIterator();
        while (iterator.hasNext()) {
            PatientMatch match = iterator.next();

            if (isNonexistingOrSelfMatch(match)) {
                iterator.remove();
            }

            if (!isAdmin() && isNonUsersMatch(match)) {
                iterator.remove();
            }
        }
    }

    private JSONObject successfulIdsToJSON(List<Long> allIds, List<Long> successfulIds)
    {
        JSONObject result = new JSONObject();
        result.put("success", successfulIds);
        allIds.removeAll(successfulIds);
        result.put("failed", allIds);
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
            this.logger.error("Error converting a JSON map {} to a map: {}", idsJson, ex);
        }

        return ids;
    }

    private boolean isAdmin()
    {
        return this.auth.hasAccess(this.users.getCurrentUser(), Right.ADMIN,
            this.resolver.resolve("", EntityType.WIKI));
    }

    /**
     * Checks if user is owner of one of the local patients in match.
     */
    private boolean isMatchOwner(User user, Patient matched, Patient ref)
    {
        DocumentReference userRef = user.getProfileDocument();
        if ((matched != null && userRef.equals(this.accessManager.getOwner(matched).getUser()))
            || (ref != null && userRef.equals(this.accessManager.getOwner(ref).getUser()))) {
            return true;
        }
        return false;
    }

    /**
     * Checks if user is a collaborator to one of the local patients in match.
     */
    private boolean isMatchCollaborator(User user, Patient matched, Patient ref)
    {
        // check if the user is a collaborator to one of the matched patients
        Collection<Collaborator> unitedCollaborators = this.accessManager.getCollaborators(matched);
        unitedCollaborators.addAll(this.accessManager.getCollaborators(ref));

        if (unitedCollaborators.isEmpty()) {
            return false;
        }

        for (Collaborator c : unitedCollaborators) {
            if (user.equals(c.getUser())) {
                return true;
            }
        }

        // check if any of the groups that user is member of is a collaborator to one of the matched patients
        Set<Group> userGroups = this.groupManager.getGroupsForUser(user);
        for (Group g : userGroups) {
            for (Collaborator c : unitedCollaborators) {
                if (c.isGroup() && g.getReference().equals(c.getUser())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Filters out matches that user does not own or is not a collaborator to any of matched patients.
     */
    private boolean isNonUsersMatch(PatientMatch match)
    {
        Patient matched = match.getMatched().getPatient();
        Patient ref = match.getReference().getPatient();
        User user = this.users.getCurrentUser();

        // just in case
        if (matched == null && ref == null) {
            return true;
        }

        // if currently logged user is owner of one of the local patients in match, do not remove the match
        if (isMatchOwner(user, matched, ref)) {
            return false;
        }

        // if currently logged user is a collaborator to one of the local patients in match, do not remove the match
        if (isMatchCollaborator(user, matched, ref)) {
            return false;
        }

        return true;
    }

    /**
     * Filters out matches that contain non-existing local patients or self-matches. TODO: - After the introduction of
     * "on-delete" listeners which remove all matches for the deleted patient there should be no more "null matches". -
     * After latest updates to LocalMatchFinder the only way a "self-match" may end up in matching notification is
     * through match finding on the patient page. => once storage manager is updated to ignore those matches and we
     * verify that everything works as expected and this filter never filters out any matches this method should be
     * removed.
     */
    private boolean isNonexistingOrSelfMatch(PatientMatch match)
    {
        if ((match.getReference().isLocal() && match.getReference().getPatient() == null)
            || (match.getMatched().isLocal() && match.getMatched().getPatient() == null)) {
            this.logger.error("Filtered out match for reference=[{}], match=[{}] due to null patient",
                match.getReferencePatientId(), match.getMatchedPatientId());
            return true;
        }

        if (CollectionUtils.isEqualCollection(match.getReference().getEmails(),
            match.getMatched().getEmails())) {
            this.logger.debug("Filtered out match for reference=[{}], match=[{}] due to same owner(s)",
                match.getReferencePatientId(), match.getMatchedPatientId());
            return true;
        }

        return false;
    }
}
