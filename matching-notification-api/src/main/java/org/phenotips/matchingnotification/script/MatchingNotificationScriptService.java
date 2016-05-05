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
import org.phenotips.matchingnotification.notification.PatientMatchNotifier;
import org.phenotips.matchingnotification.storage.MatchStorageManager;

import org.xwiki.component.annotation.Component;
import org.xwiki.script.service.ScriptService;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;

/**
 * @version $Id$
 */
@Component
@Named("matchingNotification")
@Singleton
public class MatchingNotificationScriptService implements ScriptService
{
    @Inject
    private PatientMatchExport patientMatchExport;

    @Inject
    private PatientMatchNotifier notifier;

    @Inject
    private MatchingNotificationManager matchingNotificationManager;

    @Inject
    private MatchStorageManager matchStorageManager;

    @Inject
    private Logger logger;

    /**
     * Find patient matches and saves only those with score higher or equals to {@code score}.
     *
     * @param score only matches with score higher or equal to this value are saved
     * @return JSON with the saved matches (after filtering)
     */
    public String findAndSaveMatches(double score)
    {
        List<PatientMatch> matches = this.matchingNotificationManager.findAndSaveMatches(score);
        JSONObject json = patientMatchExport.toJSON(matches);
        return json.toString();
    }

    /**
     * Returns a JSON object containing all matches from database with score higher or equal to {@code score}.
     *
     * @param score only matches with score higher or equal to this value are returned
     * @return a JSON object with a list of matches
     */
    public String getMatches(double score) {
        List<PatientMatch> matches = matchStorageManager.loadMatches(score);
        JSONObject json = patientMatchExport.toJSON(matches);
        return json.toString();
    }

    /**
     * Sends email notifications for each match.
     * Example:
     *    Input:  {'ids': ['1,'2','3']}
     *    Output: {'results': [{id: '1', success: 'true'}, {id: '2', success: 'false'}, {id: '3', success: 'true'}]}
     * @param idsForNotification JSON list of ids of matching that should be notified
     * @return result JSON
     */
    public String sendNotifications(String idsForNotification) {
        List<Long> ids = null;
        try {
            JSONArray idsJSONArray = new JSONObject(idsForNotification).getJSONArray("ids");
            int idsNum = idsJSONArray.length();
            ids = new ArrayList<Long>(idsNum);
            for (int i = 0; i < idsNum; i++) {
                ids.add(idsJSONArray.getLong(i));
            }
        } catch (JSONException ex) {
            this.logger.error("Error on converting input {} to JSON in sendNotification: {}", idsForNotification, ex);
            return JSONObject.NULL.toString();
        }

        List<PatientMatch> matches = matchStorageManager.loadMatchesByIds(ids);
        List<PatientMatchNotificationResponse> notificationResults = notifier.notify(matches);

        // create result JSON
        JSONArray results = new JSONArray();
        for (PatientMatchNotificationResponse response : notificationResults) {
            JSONObject result = new JSONObject();
            result.accumulate("id", response.getPatientMatch().getId());
            result.accumulate("success", response.isSuccessul());
            results.put(result);
        }
        JSONObject reply = new JSONObject();
        reply.put("results", results);

        return reply.toString();
    }
}
