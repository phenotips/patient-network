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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
     * Rebuilds an object from its toJSON() representation. This is used when the data is rebuilt when a row is fetched
     * from the DB.
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

        Collections.sort(predefined, PHENOTYPES_COMPARATOR);
        Collections.sort(freeText, PHENOTYPES_COMPARATOR);

        this.phenotypes = new HashMap<>();
        this.phenotypes.put(PREDEFINED, predefined);
        this.phenotypes.put(FREE_TEXT, freeText);
    }

    @Override
    public Set<java.util.Map.Entry<String, List<Map<String, String>>>> entrySet()
    {
        return this.phenotypes.entrySet();
    }

    /**
     * Reorders predefined phenotypes lists in both lists, for display. After reordering, all common phenotypes appear
     * in the beginning of the list and in the same order. The reordering ignores whether the phenotype is observed or
     * not. Order remains by `name` fields as before. Free text list remains the same. Example:
     *
     * <pre>
     * {@code
     *     list1: [{name=Ambiguous genitalia, id=HP:0000062, observed=yes},            1
     *             {name=Arrhythmia, id=HP:0011675, observed=yes},                     2
     *             {name=Hypopigmentation of the skin, id=HP:0001010, observed=no}]    3
     *     list2: [{name=Ambiguous genitalia, id=HP:0000062, observed=no},             a
     *             {name=Camptodactyly of toe, id=HP:0001836, observed=no},            b
     *             {name=Eunuchoid habitus, id=HP:0003782, observed=yes},              c
     *             {name=Hypopigmentation of the skin, id=HP:0001010, observed=yes}]   d
     *             }
     * </pre>
     *
     * will turn into
     *
     * <pre>
     * {@code
     *     list1: [{name=Ambiguous genitalia, id=HP:0000062, observed=yes},            1
     *             {name=Hypopigmentation of the skin, id=HP:0001010, observed=no},    3
     *             {name=Arrhythmia, id=HP:0011675, observed=yes}]                     2
     *     list2: [{name=Ambiguous genitalia, id=HP:0000062, observed=no},             a
     *             {name=Hypopigmentation of the skin, id=HP:0001010, observed=yes},   d
     *             {name=Camptodactyly of toe, id=HP:0001836, observed=no},            b
     *             {name=Eunuchoid habitus, id=HP:0003782, observed=yes}]              c
     *}
     * </pre>
     *
     * @param predefined1 list of predefined phenotypes for one patient
     * @param predefined2 list of predefined phenotypes for another patient
     */
    public static void reorder(List<Map<String, String>> predefined1, List<Map<String, String>> predefined2)
    {
        List<Map<String, String>> predefined1Copy = new ArrayList<>(predefined1);
        List<Map<String, String>> predefined2Copy = new ArrayList<>(predefined2);

        Map<String, Map<String, String>> predefined1Map = constructMap(predefined1Copy);
        Map<String, Map<String, String>> predefined2Map = constructMap(predefined2Copy);

        // get all common phenotypes names
        Set<String> commonNames = new HashSet<>(predefined1Map.keySet());
        commonNames.retainAll(predefined2Map.keySet());

        predefined1.clear();
        predefined2.clear();

        // first copy the common phenotypes
        for (String key : commonNames) {
            predefined1.add(predefined1Map.remove(key));
            predefined2.add(predefined2Map.remove(key));
        }

        // copy the left over unique phenotypes
        for (Map<String, String> item : predefined1Map.values()) {
            predefined1.add(item);
        }

        for (Map<String, String> item : predefined2Map.values()) {
            predefined2.add(item);
        }
    }

    // Construct the map where keys are phenotype names and objects are phenotypes themselves
    private static Map<String, Map<String, String>> constructMap(List<Map<String, String>> list)
    {
        Map<String, Map<String, String>> map = new LinkedHashMap<>();
        for (Map<String, String> item : list) {
            map.put(item.get(NAME_FIELD), item);
        }
        return map;
    }
}
