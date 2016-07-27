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

import org.phenotips.components.ComponentManagerRegistry;
import org.phenotips.data.Feature;
import org.phenotips.data.Patient;
import org.phenotips.matchingnotification.match.PhenotypesMap;
import org.phenotips.vocabulary.VocabularyManager;
import org.phenotips.vocabulary.VocabularyTerm;

import org.xwiki.component.manager.ComponentLookupException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @version $Id$
 */
public class DefaultPhenotypesMap implements PhenotypesMap
{
    private static final String FREE_TEXT = "freeText";
    private static final String PREDEFINED = "predefined";

    private static final VocabularyManager VOCABULARY_MANAGER;

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultPhenotypesMap.class);

    private Map<String, String> predefined;
    private Set<String> freeText;

    static {
        VocabularyManager vocabularyManager = null;
        try {
            vocabularyManager = ComponentManagerRegistry.getContextComponentManager().getInstance(
                VocabularyManager.class);
        } catch (ComponentLookupException e) {
            LOGGER.error("Error loading static components: {}", e.getMessage(), e);
        }
        VOCABULARY_MANAGER = vocabularyManager;
    }

    /**
     * Builds a new PhenotypesMap object for a patient.
     *
     * @param patient to build a PhenotypesMap from
     */
    public DefaultPhenotypesMap(Patient patient)
    {
        this();
        this.readPhenotypes(patient);
    }

    /**
     * Builds an empty PhenotypesMap, for debug.
     */
    public DefaultPhenotypesMap()
    {
        this.predefined = new HashMap<String, String>();
        this.freeText = new HashSet<String>();
    }

    /**
     * Returns a new PhenotypesMap from its string representation.
     *
     * @param string that was previously created by PhenotypesMap.toString().
     * @return a new PhenotypesMap object
     */
    public static PhenotypesMap getInstance(String string)
    {
        JSONArray predefinedArray = null;
        JSONArray freeTextArray = null;
        try {
            JSONObject obj = new JSONObject(string);
            predefinedArray = (JSONArray) obj.get(PREDEFINED);
            freeTextArray = (JSONArray) obj.get(FREE_TEXT);
        } catch (JSONException e) {
            DefaultPhenotypesMap.LOGGER.error("Error creating a PhenotypesMap from {}.", string, e);
        }

        DefaultPhenotypesMap map = new DefaultPhenotypesMap();
        map.freeText = new HashSet<String>();
        for (Object o : freeTextArray) {
            String id = (String) o;
            map.freeText.add(id);
        }

        map.predefined = new HashMap<String, String>();
        for (Object o : predefinedArray) {
            String id = (String) o;
            VocabularyTerm term = DefaultPhenotypesMap.VOCABULARY_MANAGER.resolveTerm(id);
            if (term != null) {
                map.predefined.put(id, term.getName());
            }
        }

        return map;
    }

    @Override
    public String toString()
    {
        JSONObject o = new JSONObject();
        o.put(PREDEFINED, this.predefined.keySet());
        o.put(FREE_TEXT, freeText);
        return o.toString();
    }

    /**
     * @return JSON representation of the object
     */
    @Override
    public JSONObject toJSON()
    {
        JSONObject toJSON = new JSONObject();

        JSONArray array = new JSONArray();
        for (String id : this.predefined.keySet()) {
            JSONObject o = new JSONObject();
            o.put("id", id);
            o.put("name", this.predefined.get(id));
            array.put(o);
        }
        toJSON.put(PREDEFINED, array);

        toJSON.put(FREE_TEXT, this.freeText);
        return toJSON;

    }

    @Override
    public Collection<String> getNames() {
        List<String> names = new ArrayList<String>(this.predefined.size() + this.freeText.size());
        names.addAll(this.predefined.values());
        names.addAll(this.freeText);
        return names;
    }

    private void readPhenotypes(Patient patient)
    {
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
