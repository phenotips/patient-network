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

import java.util.AbstractMap;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * @version $Id$
 */
public class DefaultPhenotypesMap extends AbstractMap<String, List<Map<String, String>>> implements PhenotypesMap
{
    private static final String NAME_FIELD = "name";

    private static final String ID_FIELD = "id";

    private static final String OBSERVED_FIELD = "observed";

    private static final String OBSERVED = "yes";

    private static final String NOT_OBSERVED = "no";

    // Sorts alphabetically by the value of {@link NAME_FIELD} in map
    private static final Comparator<Map<String, String>> PHENOTYPES_COMPARATOR = new Comparator<Map<String, String>>()
    {
        @Override
        public int compare(Map<String, String> phenotype0, Map<String, String> phenotype1)
        {
            return phenotype0.get(NAME_FIELD).compareTo(phenotype1.get(NAME_FIELD));
        }
    };

    // Example:
    // freeText -> [{"name":"freetext1", "observed":"no"}, {"name":"freetext2", "observed":"yes"}]
    // predefined -> [{"id":"HP:004323", "name":"Abnormality of body weight", "observed":"yes"}]
    private Map<String, List<Map<String, String>>> phenotypes;

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
        this.phenotypes = new HashMap<>();
        this.phenotypes.put(FREE_TEXT, this.readJSONArray(jsonObject, FREE_TEXT));
        this.phenotypes.put(PREDEFINED, this.readJSONArray(jsonObject, PREDEFINED));
    }

    private List<Map<String, String>> readJSONArray(JSONObject jsonObject, String arrayKey)
    {
        List<Map<String, String>> result = new LinkedList<>();

        JSONArray array = jsonObject.getJSONArray(arrayKey);
        for (Object object : array) {
            Map<String, String> m = new HashMap<>();
            JSONObject item = (JSONObject) object;
            for (String key : item.keySet()) {
                m.put(key, item.getString(key));
            }
            result.add(m);
        }

        return result;
    }

    @Override
    public String toString()
    {
        return this.toJSON().toString();
    }

    @Override
    public JSONObject toJSON()
    {
        return new JSONObject(this.phenotypes);
    }

    private void readPhenotypes(Patient patient)
    {
        List<Map<String, String>> predefined = new LinkedList<>();
        List<Map<String, String>> freeText = new LinkedList<>();

        Set<? extends Feature> features = patient.getFeatures();
        for (Feature feature : features) {
            Map<String, String> item = new HashMap<>();
            item.put(NAME_FIELD, feature.getName());
            item.put(OBSERVED_FIELD, feature.isPresent() ? OBSERVED : NOT_OBSERVED);

            String id = feature.getId();
            if (StringUtils.isEmpty(id)) {
                freeText.add(item);
            } else {
                item.put(ID_FIELD, id);
                predefined.add(item);
            }
        }

        predefined.sort(PHENOTYPES_COMPARATOR);
        freeText.sort(PHENOTYPES_COMPARATOR);

        this.phenotypes = new HashMap<>();
        this.phenotypes.put(PREDEFINED, predefined);
        this.phenotypes.put(FREE_TEXT, freeText);
    }

    @Override
    public Set<java.util.Map.Entry<String, List<Map<String, String>>>> entrySet()
    {
        return this.phenotypes.entrySet();
    }
}
