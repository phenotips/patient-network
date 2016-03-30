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

import org.phenotips.matchingnotification.match.PatientMatch;

import org.xwiki.component.annotation.Component;

import java.util.List;

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
    /** Key for matches array */
    public static final String MATCHES = "matches";

    /**
     * @param matches list of patient matches
     * @return list of matches in JSON format
     */
    public JSONObject toJSON(List<PatientMatch> matches) {
        JSONObject matchesJSON = new JSONObject();
        JSONArray matchesJSONArray = new JSONArray();
        for (PatientMatch match : matches) {
            matchesJSONArray.put(match.toJSON());
        }
        matchesJSON.put(MATCHES, matchesJSONArray);

        return matchesJSON;
    }
}
