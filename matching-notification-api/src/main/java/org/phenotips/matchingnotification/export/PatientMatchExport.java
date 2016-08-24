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
package org.phenotips.matchingnotification.export;

import org.phenotips.matchingnotification.internal.MatchesByPatient;
import org.phenotips.matchingnotification.match.PatientMatch;

import org.xwiki.component.annotation.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.inject.Singleton;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * @version $Id$
 */
@Component(roles = { PatientMatchExport.class })
@Singleton
public class PatientMatchExport
{
    /** Key for matches array. */
    public static final String MATCHES = "matches";

    private static final String SCORE = "score";
    private static final String FOUND_CALENDAR = "foundCalendar";

    /**
     * @param matches list of patient matches
     * @return list of matches in JSON format
     */
    public JSONObject toJSON(List<PatientMatch> matches)
    {
        JSONObject matchesJSON = new JSONObject();

        Set<PatientMatch> usedAsEquivalent = new HashSet<PatientMatch>();

        // Build a list of JSONObject-s representing matches
        MatchesByPatient mbp = new MatchesByPatient(matches);
        List<JSONObject> matchesObjects = new ArrayList<>(mbp.size());
        for (PatientMatch match : mbp) {
            if (usedAsEquivalent.contains(match)) {
                continue;
            }
            if (match.isLocal()) {
                PatientMatch equivalent = mbp.getEquivalentMatch(match);
                if (equivalent != null) {
                    matchesObjects.add(this.createJSONFromMatches(match, equivalent));
                    usedAsEquivalent.add(equivalent);
                } else {
                    matchesObjects.add(match.toJSON());
                }
            } else {
                matchesObjects.add(match.toJSON());
            }
        }

        // Sort by score
        Collections.sort(matchesObjects, new Comparator<JSONObject>() {
            @Override
            public int compare(JSONObject o1, JSONObject o2) {
                double score1 = (Double) o1.get(SCORE);
                double score2 = (Double) o2.get(SCORE);
                return (int) Math.signum(score2 - score1);
            }
        });

        JSONArray matchesJSONArray = new JSONArray();
        for (JSONObject json : matchesObjects) {
            matchesJSONArray.put(json);
        }
        matchesJSON.put(MATCHES, matchesJSONArray);

        return matchesJSON;
    }

    private JSONObject createJSONFromMatches(PatientMatch m1, PatientMatch m2)
    {
        JSONObject json = m1.toJSON();
        JSONArray newId = new JSONArray();
        newId.put(m1.getId());
        newId.put(m2.getId());
        json.put("id", newId);
        return json;
    }
}
