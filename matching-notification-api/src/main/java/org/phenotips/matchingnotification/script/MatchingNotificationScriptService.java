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
package org.phenotips.matchingnotification.script;

import org.phenotips.matchingnotification.MatchingNotificationManager;
import org.phenotips.matchingnotification.export.PatientMatchExport;
import org.phenotips.matchingnotification.match.PatientMatch;
import org.phenotips.matchingnotification.notification.PatientMatchNotificationResponse;
import org.phenotips.matchingnotification.storage.MatchStorageManager;

import org.xwiki.component.annotation.Component;
import org.xwiki.script.service.ScriptService;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @version $Id$
 */
@Component
@Named("matchingNotification")
@Singleton
public class MatchingNotificationScriptService implements ScriptService
{
    /** key for matches ids array in JSON. */
    public static final String IDS_STRING = "ids";

    @Inject
    private PatientMatchExport patientMatchExport;

    @Inject
    private MatchingNotificationManager matchingNotificationManager;

    @Inject
    private MatchStorageManager matchStorageManager;

    private Logger logger = LoggerFactory.getLogger(MatchingNotificationScriptService.class);

    /**
     * Find patient matches and saves only those with score higher or equals to {@code score}.
     *
     * @param score only matches with score higher or equal to this value are saved
     * @return JSON with the saved matches (after filtering)
     */
    public String findAndSaveMatches(double score)
    {
        List<PatientMatch> matches = this.matchingNotificationManager.findAndSaveMatches(score);
        JSONObject json = this.patientMatchExport.toJSON(matches);
        return json.toString();
    }

    /**
     * Returns a JSON object containing all matches from database filtered by parameters.
     *
     * @param score only matches with score higher or equal to this value are returned
     * @param notified whether the matches were notified of
     * @return a JSON object with a list of matches
     */
    public String getMatches(double score, boolean notified)
    {
        List<PatientMatch> matches = this.matchStorageManager.loadMatches(score, notified);
        filterPatientsFromMatches(matches);
        JSONObject json = this.patientMatchExport.toJSON(matches);
        return json.toString();
    }

    /**
     * Marks matches, with ids given in parameter, as rejected.
     * Example:
     *    Input: {'ids': ['1,'2','3']}
     *    Output:{'results': [{id: '1', success: 'true'}, {id: '2', success: 'false'}, {id: '3', success: 'true'}]}
     *
     * @param ids JSON with list of ids of matching that should be marked as rejected
     * @param rejected whether the matches should be rejected or unrejected
     * @return result JSON
     */
    public String rejectMatches(String ids, boolean rejected)
    {
        List<Long> idsList = this.jsonToIdsList(ids);
        this.matchingNotificationManager.markRejected(idsList, rejected);
        return this.successfulIdsToJSON(idsList, idsList).toString();
    }

    /**
     * Sends email notifications for each match.
     * Example:
     *    Input: {'ids': ['1,'2','3']}
     *    Output: {'results': [{id: '1', success: 'true'}, {id: '2', success: 'false'}, {id: '3', success: 'true'}]}
     *
     * @param ids JSON list of ids of matching that should be notified
     * @return result JSON
     */
    public String sendNotifications(String ids)
    {
        List<Long> idsList = this.jsonToIdsList(ids);
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

        return this.successfulIdsToJSON(idsList, successfullyNotified).toString();
    }

    private List<Long> jsonToIdsList(String idsJson)
    {
        List<Long> ids = new ArrayList<>();
        try {
            if (StringUtils.isNotEmpty(idsJson)) {
                JSONObject idsObject = new JSONObject(idsJson);
                if (idsObject.has(IDS_STRING)) {
                    JSONArray idsJSONArray = idsObject.getJSONArray(IDS_STRING);
                    int idsNum = idsJSONArray.length();
                    for (int i = 0; i < idsNum; i++) {
                        ids.add(idsJSONArray.getLong(i));
                    }
                }
            }
        } catch (JSONException ex) {
            this.logger.error("Error on converting input {} to JSON: {}", idsJson, ex);
        }

        return ids;
    }

    private JSONObject successfulIdsToJSON(List<Long> allIds, List<Long> successfulIds)
    {
        JSONArray results = new JSONArray();
        for (Long id : allIds) {
            JSONObject result = new JSONObject();
            result.accumulate("id", id);
            result.accumulate("success", successfulIds.contains(id));
            results.put(result);
        }
        JSONObject reply = new JSONObject();
        reply.put("results", results);

        return reply;
    }

    /**
     * Filters out matches that contain non-existing local patients or self-matches.
     */
    private void filterPatientsFromMatches(List<PatientMatch> matches) {
        ListIterator<PatientMatch> iterator = matches.listIterator();
        while (iterator.hasNext()) {
            PatientMatch match = iterator.next();
            boolean hasNullPatient = (match.getReference().isLocal() && match.getReference().getPatient() == null)
                    || (match.getMatched().isLocal() && match.getMatched().getPatient() == null);
            boolean isSelfMatch = CollectionUtils.isEqualCollection(match.getReference().getEmails(),
                    match.getMatched().getEmails());
            if (hasNullPatient || isSelfMatch) {
                iterator.remove();
            }
        }
    }
}
