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
package org.phenotips.data.similarity.phenotype;

import org.phenotips.data.Feature;
import org.phenotips.data.similarity.FeatureClusterView;
import org.phenotips.vocabulary.VocabularyTerm;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Implementation of {@link FeatureClusterView} that always reveals the full patient information; for use in trusted
 * code.
 *
 * @version $Id$
 * @since 1.0M1
 */
public class DefaultFeatureClusterView implements FeatureClusterView
{
    /** The features in the matched patient. */
    protected final Collection<Feature> matchFeatures;

    /** The features in the reference patient. */
    protected final Collection<Feature> referenceFeatures;

    protected final VocabularyTerm ancestor;

    /**
     * Constructor passing the {@link #matchFeatures matched feature} and the {@link #referenceFeatures reference feature}.
     *
     * @param matchFeatures the features in the matched patient, can be empty
     * @param referenceFeatures the features in the reference patient, can be empty
     * @param root the root/shared ancestor for the cluster, can be {@code null} to represent unmatched features
     * @throws IllegalArgumentException if match or reference are null
     */
    public DefaultFeatureClusterView(Collection<Feature> matchFeatures, Collection<Feature> referenceFeatures,
        VocabularyTerm root)
    {
        if (matchFeatures == null || referenceFeatures == null) {
            throw new IllegalArgumentException("match and reference must not be null");
        }

        reorder(matchFeatures, referenceFeatures);

        this.matchFeatures = matchFeatures;
        this.referenceFeatures = referenceFeatures;
        this.ancestor = root;
    }

    @Override
    public Collection<Feature> getReference()
    {
        return Collections.unmodifiableCollection(this.referenceFeatures);
    }

    @Override
    public Collection<Feature> getMatch()
    {
        return Collections.unmodifiableCollection(this.matchFeatures);
    }

    @Override
    public VocabularyTerm getRoot()
    {
        return this.ancestor;
    }

    @Override
    public String getId()
    {
        VocabularyTerm term = getRoot();
        return term == null ? "" : term.getId();
    }

    @Override
    public String getName()
    {
        VocabularyTerm term = getRoot();
        return term == null ? "Unmatched" : term.getName();
    }

    @Override
    public JSONObject toJSON()
    {
        // Add ancestor info
        JSONObject featureMatchJSON = new JSONObject();

        JSONObject sharedParentJSON = new JSONObject();
        sharedParentJSON.put("id", getId());
        sharedParentJSON.put("name", getName());
        featureMatchJSON.put("category", sharedParentJSON);

        // Add reference features
        JSONArray referenceJSON = new JSONArray();
        for (Feature term : getReference()) {
            String termId = "";
            if (term != null) {
                termId = term.getId();
            }
            referenceJSON.put(termId);
        }
        featureMatchJSON.put("reference", referenceJSON);

        // Add match features
        JSONArray matchJSON = new JSONArray();
        for (Feature term : getMatch()) {
            String termId = "";
            if (term != null) {
                termId = term.getId();
            }
            matchJSON.put(termId);
        }
        featureMatchJSON.put("match", matchJSON);

        return featureMatchJSON;
    }

    /**
     * Reorders predefined phenotypes lists in both maps, for display.
     *
     * @param predefined1 list of predefined phenotypes for one patient
     * @param predefined2 list of predefined phenotypes for another patient
     **/
    private void reorder(Collection<Feature> predefined1, Collection<Feature> predefined2)
    {
        List<Feature> predefined1Copy = new ArrayList<>(predefined1);
        List<Feature> predefined2Copy = new ArrayList<>(predefined2);

        Map<String, Feature> predefined1Map = constructMap(predefined1Copy);
        Map<String, Feature> predefined2Map = constructMap(predefined2Copy);

        Set<String> names1 = new HashSet<String>(predefined1Map.keySet());
        Set<String> names2 = new HashSet<String>(predefined2Map.keySet());

        // get all common phenotypes names in names1 list
        names1.retainAll(names2);

        predefined1.clear();
        predefined2.clear();

        // first copy the common phenotypes
        for (String key : names1) {
            predefined1.add(predefined1Map.remove(key));
            predefined2.add(predefined2Map.remove(key));
        }

        // copy the left over unique phenotypes
        for (String key : predefined1Map.keySet()) {
            predefined1.add(predefined1Map.get(key));
        }

        for (String key : predefined2Map.keySet()) {
            predefined2.add(predefined2Map.get(key));
        }
    }

    // Construct the map where keys are phenotype names and objects are phenotypes themself
    private static Map<String, Feature> constructMap(List<Feature> list)
    {
        Map<String, Feature> map = new HashMap<String, Feature>();
        for (Feature item : list) {
            map.put(item.getName(), item);
        }
        return map;
    }
}
