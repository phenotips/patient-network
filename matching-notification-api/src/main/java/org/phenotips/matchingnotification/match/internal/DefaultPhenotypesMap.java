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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
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
     * Reorders predefined phenotypes lists in both maps, for display. After reordering, all common phenotypes appear
     * in the beginning of the list and in the same order. The reordering ignores whether the phenotype is observed
     * or not. Order remains by `name` fields as before. Free text list remains the same.
     *
     * Example:
     *     list1: [{name=Ambiguous genitalia, id=HP:0000062, observed=yes},            1
     *             {name=Arrhythmia, id=HP:0011675, observed=yes},                     2
     *             {name=Hypopigmentation of the skin, id=HP:0001010, observed=no}]    3
     *     list2: [{name=Ambiguous genitalia, id=HP:0000062, observed=no},             a
     *             {name=Camptodactyly of toe, id=HP:0001836, observed=no},            b
     *             {name=Eunuchoid habitus, id=HP:0003782, observed=yes},              c
     *             {name=Hypopigmentation of the skin, id=HP:0001010, observed=yes}]   d
     * will turn into
     *     list1: [{name=Ambiguous genitalia, id=HP:0000062, observed=yes},            1
     *             {name=Hypopigmentation of the skin, id=HP:0001010, observed=no},    3
     *             {name=Arrhythmia, id=HP:0011675, observed=yes}]                     2
     *     list2: [{name=Ambiguous genitalia, id=HP:0000062, observed=no},             a
     *             {name=Hypopigmentation of the skin, id=HP:0001010, observed=yes},   d
     *             {name=Camptodactyly of toe, id=HP:0001836, observed=no},            b
     *             {name=Eunuchoid habitus, id=HP:0003782, observed=yes}]              c
     *
     * @param predefined1 list of predefined phenotypes for one patient
     * @param predefined2 list of predefined phenotypes for another patient
     */
    public static void reorder(List<Map<String, String>> predefined1, List<Map<String, String>> predefined2)
    {
        // Break lists to common predefined phenotypes, and those unique to each list. There are two common lists
        // because the same phenotype can have different properties in each patient (for example, observed in one
        // but not in the other).
        List<Map<String, String>> common1 = new LinkedList<Map<String, String>>();
        List<Map<String, String>> common2 = new LinkedList<Map<String, String>>();
        List<Map<String, String>> unique1 = new LinkedList<Map<String, String>>();
        List<Map<String, String>> unique2 = new LinkedList<Map<String, String>>();

        Iterator<Map<String, String>> iterator1 = predefined1.iterator();
        Iterator<Map<String, String>> iterator2 = predefined2.iterator();
        Map<String, String> item1 = null;
        Map<String, String> item2 = null;
        String name1 = null;
        String name2 = null;
        boolean next1 = true;
        boolean next2 = true;

        while (iterator1.hasNext() && iterator2.hasNext()) {
            if (next1) {
                item1 = iterator1.next();
                name1 = item1.get(NAME_FIELD);
                next1 = false;
            }
            if (next2) {
                item2 = iterator2.next();
                name2 = item2.get(NAME_FIELD);
                next2 = false;
            }

            if (StringUtils.equals(name1, name2)) {
                common1.add(item1);
                common2.add(item2);
                next1 = true;
                next2 = true;
            } else if (name1.compareTo(name2) < 0) {
                unique1.add(item1);
                next1 = true;
            } else {
                unique2.add(item2);
                next2 = true;
            }
        }

        while (iterator1.hasNext()) {
            unique1.add(iterator1.next());
        }
        while (iterator2.hasNext()) {
            unique2.add(iterator2.next());
        }

        // Rebuild predefined
        predefined1.clear();
        predefined1.addAll(common1);
        predefined1.addAll(unique1);

        predefined2.clear();
        predefined2.addAll(common2);
        predefined2.addAll(unique2);
    }
}
