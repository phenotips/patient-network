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

import org.phenotips.matchingnotification.export.PatientMatchExport;
import org.phenotips.matchingnotification.finder.MatchFinderManager;
import org.phenotips.matchingnotification.match.PatientMatch;
import org.phenotips.matchingnotification.storage.MatchStorageManager;

import org.xwiki.component.annotation.Component;
import org.xwiki.script.service.ScriptService;

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
    private MatchFinderManager matchFinderManager;

    @Inject
    private MatchStorageManager matchStorageManager;

    @Inject
    private PatientMatchExport patientMatchExport;

    @Inject
    private Logger logger;

    /**
     * Find patient matches and populate matches table.
     *
     * @return true if successful
     */
    public boolean findMatches() {
        List<PatientMatch> matches = matchFinderManager.findMatches();
        matchStorageManager.saveMatches(matches);
        return true;
    }

    /**
     * @return a JSON object with a list of all matches
     */
    public String getAllMatches() {
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
        long[] ids = null;
        try {
            JSONArray idsJSONArray = new JSONObject(idsForNotification).getJSONArray("ids");
            int idsNum = idsJSONArray.length();
            ids = new long[idsNum];
            for (int i = 0; i < idsNum; i++) {
                ids[i] = idsJSONArray.getLong(i);
            }
        } catch (JSONException ex) {
            this.logger.error("Error on converting input {} to JSON in sendNotification: {}", idsForNotification, ex);
            return false;
        }

        return true;
    }
}
