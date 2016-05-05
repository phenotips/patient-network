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
     * @return true if successful
     */
    public boolean findAndSaveMatches(double score)
    {
        boolean result = this.matchingNotificationManager.findAndSaveMatches(score);
        return result;
    }

    /**
     * Returns a JSON object containing all matches from database with score higher or equal to {@code score}.
     *
     * @param score only matches with score higher or equal to this value are returned
     * @return a JSON object with a list of matches
     */
    public String getMatches(double score) {
        List<PatientMatch> allMatches = matchStorageManager.loadAllMatches();
        JSONObject json = patientMatchExport.toJSON(allMatches);
        return json.toString();
    }

    /**
     * Sends email notifications for each match.
     *
     * @param idsForNotification JSON list of ids of matching that should be notified. For example,
     *        {'ids': ['1,'2','3']}.
     * @return true if successful
     */
    public boolean sendNotifications(String idsForNotification) {
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
            return false;
        }

        List<PatientMatch> matches = matchStorageManager.loadMatchesByIds(ids);
        notifier.notify(matches);

        return true;
    }
}
