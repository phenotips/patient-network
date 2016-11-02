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
package org.phenotips.matchingnotification.match.internal;

import org.phenotips.data.Feature;
import org.phenotips.data.Patient;
import org.phenotips.matchingnotification.match.PhenotypesMap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * @version $Id$
 */
public class DefaultPhenotypesMap implements PhenotypesMap
{
    private static final String FREE_TEXT = "freeText";
    private static final String PREDEFINED = "predefined";

    private Set<String> freeText;
    private Map<String, String> predefined;

    /**
     * Builds a new PhenotypesMap object for a patient.
     *
     * @param patient to build a PhenotypesMap from
     */
    public DefaultPhenotypesMap(Patient patient)
    {
        this.readPhenotypes(patient);
    }

    /**
     * Rebuilds an object from its toJSON() representation. This is used when the data is rebuilt when a row is
     * fetched from the DB.
     *
     * @param jsonObject a result of a previous call to toJSON().
     */
    public DefaultPhenotypesMap(JSONObject jsonObject)
    {
        // Free text
        JSONArray freeTextArray = jsonObject.getJSONArray(FREE_TEXT);
        Iterator<Object> freeTextIterator = freeTextArray.iterator();
        freeText = new HashSet<>();
        while (freeTextIterator.hasNext()) {
            freeText.add((String) freeTextIterator.next());
        }

        // Predefined
        JSONObject predefinedObject = jsonObject.getJSONObject(PREDEFINED);
        predefined = new HashMap<>();
        for (String key : predefinedObject.keySet()) {
            predefined.put(key, predefinedObject.getString(key));
        }
    }

    @Override
    public String toString()
    {
        JSONObject o = new JSONObject();
        o.put(PREDEFINED, this.predefined.keySet());
        o.put(FREE_TEXT, this.freeText);
        return o.toString();
    }

    @Override
    public JSONObject toJSON()
    {
        JSONObject toJSON = new JSONObject();
        toJSON.put(PREDEFINED, this.predefined);
        toJSON.put(FREE_TEXT, this.freeText);
        return toJSON;

    }

    @Override
    public Collection<String> getNames()
    {
        List<String> names = new ArrayList<String>(this.predefined.size() + this.freeText.size());
        names.addAll(predefined.values());
        names.addAll(freeText);
        return names;
    }

    private void readPhenotypes(Patient patient)
    {
        this.predefined = new HashMap<>();
        this.freeText = new HashSet<>();

        Set<? extends Feature> features = patient.getFeatures();
        for (Feature feature : features) {
            if (!feature.isPresent()) {
                continue;
            }

            String id = feature.getId();
            String name = feature.getName();
            if (StringUtils.isEmpty(id)) {
                freeText.add(name);
            } else {
                predefined.put(id, name);
            }
        }
    }
}
