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

import org.phenotips.components.ComponentManagerRegistry;
import org.phenotips.data.Feature;
import org.phenotips.data.similarity.AccessType;
import org.phenotips.data.similarity.FeatureClusterView;
import org.phenotips.data.similarity.PatientPhenotypeSimilarityView;
import org.phenotips.vocabulary.Vocabulary;
import org.phenotips.vocabulary.VocabularyManager;
import org.phenotips.vocabulary.VocabularyTerm;

import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.component.manager.ComponentManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.SpellingParams;
import org.json.JSONArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of {@link PatientPhenotypeSimilarityView} that always reveals the full patient information; for use in trusted
 * code.
 *
 * @version $Id$
 * @since 1.1
 */
public class DefaultPatientPhenotypeSimilarityView implements PatientPhenotypeSimilarityView
{
    private static final String UNCATEGORIZED = "uncategorized";

    /** Provides access to the term vocabulary. */
    private static VocabularyManager vocabularyManager;

    /** Provides access to top abnormality terms in the HPO vocabulary. */
    private static Collection<VocabularyTerm> topAbnormalityTerms;

    /** Logging helper object. */
    private static Logger logger = LoggerFactory.getLogger(DefaultPatientPhenotypeSimilarityView.class);

    /** The features in the matched patient. */
    protected final Set<? extends Feature> matchFeatures;

    /** The features in the reference patient. */
    protected final Set<? extends Feature> referenceFeatures;

    /** The access level the user has to the patient. */
    protected final AccessType access;

    /** The collection of feature cluster views for the match of patients. */
    protected final Collection<FeatureClusterView> featureClusters;

    /**
     * Constructor passing the {@link #matchFeatures matched features}
     * and the {@link #referenceFeatures reference features}.
     *
     * @param matchFeatures the features in the matched patient, can be empty
     * @param referenceFeatures the features in the reference patient, can be empty
     * @param access the access level of the match
     * @throws IllegalArgumentException if match or reference features are null
     */
    public DefaultPatientPhenotypeSimilarityView(Set<? extends Feature> matchFeatures,
        Set<? extends Feature> referenceFeatures, AccessType access)
    {
        if (matchFeatures == null || referenceFeatures == null) {
            throw new IllegalArgumentException("match and reference feture sets must not be null");
        }

        if (!isInitialized()) {
            VocabularyManager vm = null;
            try {
                ComponentManager ccm = ComponentManagerRegistry.getContextComponentManager();
                vm = ccm.getInstance(VocabularyManager.class);
            } catch (ComponentLookupException e) {
                logger.error("Error loading static components: {}", e.getMessage(), e);
            }
            vocabularyManager = vm;
            Vocabulary hpo = vm.getVocabulary("HPO");
            topAbnormalityTerms = queryTopAbnormalityTerms(hpo);
        }

        this.matchFeatures = matchFeatures;
        this.referenceFeatures = referenceFeatures;
        this.access = access;
        this.featureClusters = constructFeatureClusters();
    }

    /**
     * Return whether the class has been initialized with static data.
     *
     * @return true iff the class has been initialized with static data
     */
    public static boolean isInitialized()
    {
        return topAbnormalityTerms != null;
    }

    /**
     * Set the static information for the class. Must be run before creating instances of this class.
     *
     * @param vocabularyManager the vocabulary manager
     */
    public static void initializeStaticData(VocabularyManager vocabularyManager)
    {
        DefaultPatientPhenotypeSimilarityView.vocabularyManager = vocabularyManager;
    }

    /**
     * Create an instance of the FeatureClusterView for this PatientSimilarityView.
     *
     * @param matchFeatures the features in the matched patient
     * @param reference the features in the reference patient
     * @param access the access level of the match
     * @param root the root/shared ancestor for the cluster
     * @return returns a feature view container of matched and reference features with shared root
     */
    protected FeatureClusterView createFeatureClusterView(Collection<Feature> matchFeatures,
        Collection<Feature> reference, AccessType access, VocabularyTerm root)
    {
        return new DefaultFeatureClusterView(matchFeatures, reference, access, root);
    }

    private Collection<FeatureClusterView> constructFeatureClusters()
    {
        Collection<FeatureClusterView> clusters = new LinkedList<>();

        if (this.matchFeatures.size() == 0 && this.referenceFeatures.size() == 0) {
            return clusters;
        }

        // Get term -> feature lookups for creating cluster views
        Map<String, Feature> matchFeatureLookup = getTermLookup(this.matchFeatures);
        Map<String, Feature> refFeatureLookup = getTermLookup(this.referenceFeatures);

        // Get the present vocabulary terms
        Collection<VocabularyTerm> matchTerms = getPresentPatientTerms(this.matchFeatures);
        Collection<VocabularyTerm> refTerms = getPresentPatientTerms(this.referenceFeatures);

        // if one of feature sets is empty - then we create and return a single cluster view of uncategorized features
        if (this.matchFeatures.size() == 0 || this.referenceFeatures.size() == 0) {
            Collection<Feature> abnormalMatchFeatures = termsToFeatures(matchTerms, matchFeatureLookup);
            Collection<Feature> abnormalRefFeatures = termsToFeatures(refTerms, refFeatureLookup);

            FeatureClusterView cluster = createFeatureClusterView(abnormalMatchFeatures, abnormalRefFeatures,
                this.access, null);
            clusters.add(cluster);
            return clusters;
        }

        // Get sorted maps <topAbnormalityId -> Collection of related terms>
        Map<String, Collection<VocabularyTerm>> matchSortedTerms = getSortedTerms(matchTerms);
        Map<String, Collection<VocabularyTerm>> refSortedTerms = getSortedTerms(refTerms);

        // create cluster views for each of topAbnormalityTerms if there are related terms in match or ref terms
        for (VocabularyTerm term : topAbnormalityTerms) {

            String abnormalityId = term.getId();

            // if both have features from the abnormality category - create cluster view
            if (matchSortedTerms.containsKey(abnormalityId) && refSortedTerms.containsKey(abnormalityId)) {
                Collection<Feature> abnormalMatchFeatures =
                    termsToFeatures(matchSortedTerms.get(abnormalityId), matchFeatureLookup);
                Collection<Feature> abnormalRefFeatures =
                    termsToFeatures(refSortedTerms.get(abnormalityId), refFeatureLookup);

                // reorder so the exact matches are listed first
                DefaultPhenotypesMap.reorder(abnormalMatchFeatures, abnormalRefFeatures);

                FeatureClusterView cluster = createFeatureClusterView(abnormalMatchFeatures, abnormalRefFeatures,
                    this.access, term);
                clusters.add(cluster);
                continue;
            } else {
                // we add all matched/ref terms related to this abnormality to "uncategorized"
                // because there are no terms in ref/matched from this category
                dumpFeaturesToUncategorized(matchSortedTerms, refSortedTerms, abnormalityId);
            }
        }

        // add uncategorized cluster view for terms whose abnormality categories unmatched
        if (matchSortedTerms.containsKey(UNCATEGORIZED) || refSortedTerms.containsKey(UNCATEGORIZED)) {
            Collection<Feature> abnormalMatchFeatures =
                termsToFeatures(matchSortedTerms.get(UNCATEGORIZED), matchFeatureLookup);
            Collection<Feature> abnormalRefFeatures =
                termsToFeatures(refSortedTerms.get(UNCATEGORIZED), refFeatureLookup);

            FeatureClusterView cluster = createFeatureClusterView(abnormalMatchFeatures, abnormalRefFeatures,
                this.access, null);
            clusters.add(cluster);
        }

        return clusters;
    }

    private void dumpFeaturesToUncategorized(Map<String, Collection<VocabularyTerm>> matchSortedTerms,
        Map<String, Collection<VocabularyTerm>> refSortedTerms, String abnormalityId)
    {
        if (!matchSortedTerms.containsKey(abnormalityId) && !refSortedTerms.containsKey(abnormalityId)) {
            return;
        }

        if (matchSortedTerms.containsKey(abnormalityId)) {
            Collection<VocabularyTerm> uncategorizedTerms = matchSortedTerms.getOrDefault(UNCATEGORIZED,
                new ArrayList<VocabularyTerm>());
            uncategorizedTerms.addAll(matchSortedTerms.get(abnormalityId));
            matchSortedTerms.put(UNCATEGORIZED, uncategorizedTerms);
        }

        if (refSortedTerms.containsKey(abnormalityId)) {
            Collection<VocabularyTerm> uncategorizedTerms = refSortedTerms.getOrDefault(UNCATEGORIZED,
                new ArrayList<VocabularyTerm>());
            uncategorizedTerms.addAll(refSortedTerms.get(abnormalityId));
            refSortedTerms.put(UNCATEGORIZED, uncategorizedTerms);
        }
    }

    private Map<String, Collection<VocabularyTerm>> getSortedTerms(Collection<VocabularyTerm> terms)
    {
        Map<String, Collection<VocabularyTerm>> sortedTerms = new HashMap<>();
        for (VocabularyTerm term : terms) {
            Set<VocabularyTerm> ancestors = new HashSet<>(term.getAncestorsAndSelf());
            // Intersecting ancestors with topAbnormalityTerms
            ancestors.retainAll(topAbnormalityTerms);
            String categoryId = UNCATEGORIZED;
            if (ancestors.size() > 0) {
                // term can be associated with more than one top abnormalities,
                // to resolve ambiguity we get the first in the intersection term set
                categoryId = ancestors.iterator().next().getId();
            }
            Collection<VocabularyTerm> categorizedTerms = sortedTerms.getOrDefault(categoryId,
                new ArrayList<VocabularyTerm>());
            categorizedTerms.add(term);
            sortedTerms.put(categoryId, categorizedTerms);
        }
        return sortedTerms;
    }

    /**
     * Return the original patient features for a set of VocabularyTerms.
     *
     * @param terms the terms to look up features for
     * @param termLookup a mapping from term IDs to features in the patient
     * @return a Collection of features in the patients corresponding to the given terms
     */
    private Collection<Feature> termsToFeatures(Collection<VocabularyTerm> terms, Map<String, Feature> termLookup)
    {
        Collection<Feature> features = new ArrayList<>();
        if (terms == null || terms.size() == 0) {
            return features;
        }
        for (VocabularyTerm term : terms) {
            String id = term.getId();
            if (id != null) {
                Feature feature = termLookup.get(id);
                if (feature != null) {
                    features.add(feature);
                }
            }
        }
        return features;
    }

    /**
     * Return a (potentially empty) mapping from VocabularyTerm IDs back to features in the patient. Un-mappable
     * features are not included.
     *
     * @param features the patient features to process
     * @return a mapping from term IDs to features in the patient
     */
    private Map<String, Feature> getTermLookup(Set<? extends Feature> features)
    {
        Map<String, Feature> lookup = new HashMap<>();
        for (Feature feature : features) {
            String id = feature.getId();
            if (!id.isEmpty()) {
                lookup.put(id, feature);
            }
        }
        return lookup;
    }

    /**
     * Return a (potentially empty) collection of terms present in the patient.
     *
     * @param features the patient features to process
     * @return a collection of terms present in the patient
     */
    private Collection<VocabularyTerm> getPresentPatientTerms(Set<? extends Feature> features)
    {
        Set<VocabularyTerm> terms = new HashSet<>();
        for (Feature feature : features) {
            if (!feature.isPresent()) {
                continue;
            }

            VocabularyTerm term = vocabularyManager.resolveTerm(feature.getId());
            if (term != null) {
                // Only add resolvable terms
                terms.add(term);
            }
        }
        return terms;
    }

    /**
     * Return all top abnormality terms in the HPO vocabulary.
     *
     * @param vocabulary the HPO vocabulary to query
     * @return a Collection of top abnormality VocabularyTerms
     */
    private Collection<VocabularyTerm> queryTopAbnormalityTerms(Vocabulary vocabulary)
    {
        Map<String, String> query = new HashMap<>();
        query.put("id", "*");
        Map<String, String> queryParams = new HashMap<>();
        queryParams.put(CommonParams.FQ, "is_a:HP\\:0000118");
        queryParams.put(CommonParams.ROWS, "100");
        queryParams.put(CommonParams.SORT, "nameSort asc");
        queryParams.put(CommonParams.START, "0");
        queryParams.put("lowercaseOperators", Boolean.toString(false));
        queryParams.put("spellcheck", Boolean.toString(true));
        queryParams.put(SpellingParams.SPELLCHECK_COLLATE, Boolean.toString(true));
        queryParams.put("defType", "edismax");
        Collection<VocabularyTerm> results = vocabulary.search(query, queryParams);
        return Collections.unmodifiableCollection(results);
    }

    /**
     * Returns JSON representation of phenotype cluster view.
     *
     * @see org.phenotips.data.Feature#toJSON()
     */
    @Override
    public JSONArray toJSON()
    {
        // Get list of clusters and convert to JSON
        JSONArray matchesJSON = new JSONArray();
        for (FeatureClusterView cluster : this.featureClusters) {
            matchesJSON.put(cluster.toJSON());
        }
        return matchesJSON;
    }
}
