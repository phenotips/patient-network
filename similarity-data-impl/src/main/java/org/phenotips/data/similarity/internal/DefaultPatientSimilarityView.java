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
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.phenotips.data.similarity.internal;

import org.phenotips.data.Disorder;
import org.phenotips.data.Feature;
import org.phenotips.data.Patient;
import org.phenotips.data.similarity.AccessType;
import org.phenotips.data.similarity.DisorderSimilarityView;
import org.phenotips.data.similarity.FeatureClusterView;
import org.phenotips.data.similarity.PatientGenotypeSimilarityView;
import org.phenotips.vocabulary.VocabularyManager;
import org.phenotips.vocabulary.VocabularyTerm;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import net.sf.json.JSONArray;

/**
 * Implementation of {@link org.phenotips.data.similarity.PatientSimilarityView} that uses a mutual information metric
 * to score similar patients.
 *
 * @version $Id$
 * @since 1.0M1
 */
public class DefaultPatientSimilarityView extends AbstractPatientSimilarityView
{
    /** The overall root of the HPO. */
    private static final String HP_ROOT = "HP:0000001";

    /** The root of the phenotypic abnormality portion of HPO. */
    private static final String PHENOTYPE_ROOT = "HP:0000118";

    /** Pre-computed term information content (-logp), for each node t (i.e. t.inf). */
    private static Map<VocabularyTerm, Double> termICs;

    /** The largest IC found, for normalizing. */
    private static Double maxIC;

    /** Provides access to the term vocabulary. */
    private static VocabularyManager vocabularyManager;

    /** Memoized match score. */
    private Double score;

    /** Links disorder values from this patient to the reference. */
    private Set<DisorderSimilarityView> matchedDisorders;

    /** Memoized genotype match, retrieved through getGenotypeSimilarity. */
    private PatientGenotypeSimilarityView matchedGenes;

    /**
     * Simple constructor passing both {@link #match the patient} and the {@link #reference reference patient}.
     *
     * @param match the matched patient to represent, must not be {@code null}
     * @param reference the reference patient against which to compare, must not be {@code null}
     * @param access the access level the current user has on the matched patient
     * @throws IllegalArgumentException if one of the patients is {@code null}
     * @throws NullPointerException if the class was not statically initialized with {#initializeStaticData(Map, Map,
     *             VocabularyManager, Logger)} before use
     */
    public DefaultPatientSimilarityView(Patient match, Patient reference, AccessType access)
        throws IllegalArgumentException
    {
        super(match, reference, access);
        if (!isInitialized()) {
            String error =
                "Static data of MutualInformationPatientSimilarityView was not initilized before instantiation";
            throw new NullPointerException(error);
        }
    }

    /**
     * Return whether the class has been initialized with static data.
     *
     * @return true iff the class has been initialized with static data
     */
    public static boolean isInitialized()
    {
        return termICs != null && vocabularyManager != null;
    }

    /**
     * Set the static information for the class. Must be run before creating instances of this class.
     *
     * @param termICs the information content of each term
     * @param vocabularyManager the vocabulary manager
     */
    public static void initializeStaticData(Map<VocabularyTerm, Double> termICs, VocabularyManager vocabularyManager)
    {
        DefaultPatientSimilarityView.termICs = termICs;
        DefaultPatientSimilarityView.vocabularyManager = vocabularyManager;
        DefaultPatientSimilarityView.maxIC = Collections.max(termICs.values());
    }

    /**
     * Create an instance of the FeatureClusterView for this PatientSimilarityView.
     *
     * @param match the features in the matched patient
     * @param reference the features in the reference patient
     * @param access the access level of the match
     * @param root the root/shared ancestor for the cluster
     * @param score the score of the feature matching
     */
    protected FeatureClusterView createFeatureClusterView(Collection<Feature> match, Collection<Feature> reference,
        AccessType access, VocabularyTerm root, double score)
    {
        return new DefaultFeatureClusterView(match, reference, access, root, score);
    }

    /**
     * Create an instance of the DisorderSimilarityView for this PatientSimilarityView.
     *
     * @param match the disorder in the match patient
     * @param reference the disorder in the reference patient
     * @param access the access level
     * @return the DisorderSimilarityView for the pair of disorders
     */
    protected DisorderSimilarityView createDisorderSimilarityView(Disorder match, Disorder reference, AccessType access)
    {
        return new DefaultDisorderSimilarityView(match, reference);
    }

    /**
     * Searches for a similar disorder in the reference patient, matching one of the matched patient's disorders, or
     * vice-versa.
     *
     * @param toMatch the disorder to match
     * @param lookIn the list of disorders to look in, either the reference patient or the matched patient diseases
     * @return one of the disorders from the list, if it matches the target disorder, or {@code null} otherwise
     */
    protected Disorder findMatchingDisorder(Disorder toMatch, Set<? extends Disorder> lookIn)
    {
        for (Disorder candidate : lookIn) {
            if (StringUtils.equals(candidate.getId(), toMatch.getId())) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * Return the displayable set of matched disorders, retrieved from {@link #getMatchedDisorders()}. {@inheritDoc}
     *
     * @see org.phenotips.data.Patient#getDisorders()
     */
    @Override
    public Set<? extends Disorder> getDisorders()
    {
        Set<Disorder> result = new HashSet<Disorder>();
        for (DisorderSimilarityView disorder : getMatchedDisorders()) {
            if (disorder.getId() != null) {
                result.add(disorder);
            }
        }

        return result;
    }

    /**
     * Get pairs of matching disorders, one from the current patient and one from the reference patient. Unmatched
     * values from either side are paired with a {@code null} value.
     *
     * @return an unmodifiable set of matched disorders.
     */
    protected Set<DisorderSimilarityView> getMatchedDisorders()
    {
        if (this.matchedDisorders == null) {
            Set<DisorderSimilarityView> result = new HashSet<DisorderSimilarityView>();
            for (Disorder disorder : this.match.getDisorders()) {
                result.add(createDisorderSimilarityView(disorder,
                    findMatchingDisorder(disorder, this.reference.getDisorders()), this.access));
            }
            for (Disorder disorder : this.reference.getDisorders()) {
                if (this.match == null || findMatchingDisorder(disorder, this.match.getDisorders()) == null) {
                    result.add(createDisorderSimilarityView(null, disorder, this.access));
                }
            }
            this.matchedDisorders = Collections.unmodifiableSet(result);
        }
        return this.matchedDisorders;
    }

    /**
     * Return a (potentially empty) collection of terms present in the patient.
     *
     * @param patient
     * @return a collection of terms present in the patient
     */
    private Collection<VocabularyTerm> getPresentPatientTerms(Patient patient)
    {
        Set<VocabularyTerm> terms = new HashSet<VocabularyTerm>();
        for (Feature feature : patient.getFeatures()) {
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
     * Return a (potentially empty) mapping from VocabularyTerm IDs back to features in the patient.
     * Un-mappable features are not included.
     *
     * @param patient
     * @return a mapping from term IDs to features in the patient
     */
    private Map<String, Feature> getTermLookup(Patient patient)
    {
        Map<String, Feature> lookup = new HashMap<String, Feature>();
        for (Feature feature : patient.getFeatures()) {
            String id = feature.getId();
            if (!id.isEmpty()) {
                lookup.put(id, feature);
            }
        }
        return lookup;
    }

    /**
     * Return the set of terms implied by a collection of features in the vocabulary.
     *
     * @param terms a collection of terms
     * @return all provided VocabularyTerm terms and their ancestors
     */
    private Set<VocabularyTerm> getAncestors(Collection<VocabularyTerm> terms)
    {
        Set<VocabularyTerm> ancestors = new HashSet<VocabularyTerm>(terms);
        for (VocabularyTerm term : terms) {
            // Add all ancestors
            ancestors.addAll(term.getAncestorsAndSelf());
        }
        return ancestors;
    }

    /**
     * Return the total IC across a collection of terms.
     *
     * @param terms (should include implied ancestors) that are present in the patient
     * @return the total IC for all the terms
     */
    private double getTermICs(Collection<VocabularyTerm> terms)
    {
        double cost = 0;
        for (VocabularyTerm term : terms) {
            Double ic = termICs.get(term);
            if (ic == null) {
                ic = 0.0;
            }
            cost += ic;
        }
        return cost;
    }

    /**
     * Get the phenotypic similarity score for this patient match.
     *
     * @return the similarity score, between 0 (a poor match) and 1 (a good match)
     */
    public double getPhenotypeScore()
    {
        if (this.match == null || this.reference == null) {
            return 0.0;
        } else {
            // Get ancestors for both patients
            Set<VocabularyTerm> refAncestors = getAncestors(getPresentPatientTerms(this.reference));
            Set<VocabularyTerm> matchAncestors = getAncestors(getPresentPatientTerms(this.match));

            if (refAncestors.isEmpty() || matchAncestors.isEmpty()) {
                return 0.0;
            } else {
                // Score overlapping ancestors
                Set<VocabularyTerm> commonAncestors = new HashSet<VocabularyTerm>();
                commonAncestors.addAll(refAncestors);
                commonAncestors.retainAll(matchAncestors);

                Set<VocabularyTerm> allAncestors = new HashSet<VocabularyTerm>();
                allAncestors.addAll(refAncestors);
                allAncestors.addAll(matchAncestors);

                return getTermICs(commonAncestors) / getTermICs(allAncestors);
            }
        }
    }

    /**
     * Adjust the similarity score by taking into account common disorders. Matching disorders will boost the base score
     * given by the phenotypic similarity, while unmatched disorders don't affect the score at all.
     *
     * @param baseScore the score given by features alone, a number between {@code 0} and {@code 1}
     * @return the adjusted similarity score, boosted closer to {@code 1} if there are common disorders between this
     *         patient and the reference patient, or the unmodified base score otherwise; the score is never lowered,
     *         and never goes above {@code 1}
     * @see #getScore()
     */
    private double adjustScoreWithDisordersScore(double baseScore)
    {
        Set<DisorderSimilarityView> disorders = getMatchedDisorders();
        if (disorders.isEmpty()) {
            return baseScore;
        }
        double adjustedScore = baseScore;
        double bias = 3;
        for (DisorderSimilarityView disorder : disorders) {
            if (disorder.isMatchingPair()) {
                // For each disorder match, reduce the distance between the current score to 1 by 1/3
                adjustedScore = adjustedScore + (1 - adjustedScore) / bias;
            }
        }
        return adjustedScore;
    }

    @Override
    public double getScore()
    {
        // Memoize the score
        if (this.score == null) {
            double phenotypeScore = getPhenotypeScore();
            phenotypeScore = adjustScoreWithDisordersScore(phenotypeScore);

            // Factor in overlap between candidate genes
            PatientGenotypeSimilarityView genotypeSimilarity = getGenotypeSimilarity();
            Collection<String> sharedGenes = new HashSet<String>();
            sharedGenes = genotypeSimilarity.getCandidateGenes();

            double geneBoost = 0.0;
            if (!sharedGenes.isEmpty()) {
                geneBoost = 0.7;
            }

            // Return boosted score
            return Math.pow(phenotypeScore, 1.0 - geneBoost);
        }
        return this.score;
    }

    /**
     * Get the genotype similarity view for this pair of patients, lazily evaluated and memoized.
     *
     * @return the genotype similarity view for this pair of patients
     */
    private PatientGenotypeSimilarityView getGenotypeSimilarity()
    {
        if (this.matchedGenes == null) {
            this.matchedGenes = new RestrictedPatientGenotypeSimilarityView(this.match, this.reference, this.access);
        }
        return this.matchedGenes;
    }

    @Override
    public JSONArray getGenesJSON()
    {
        return getGenotypeSimilarity().toJSON();
    }

    /**
     * {@inheritDoc} Return the features present in the match patient. If the features in the match are not visible at
     * the current access level, an empty set will be returned.
     *
     * @see org.phenotips.data.Patient#getFeatures()
     */
    @Override
    public Set<? extends Feature> getFeatures()
    {
        return this.match.getFeatures();
    }

    @Override
    protected JSONArray getFeaturesJSON()
    {
        // Just return a simple array of the features in the match patient
        JSONArray featuresJSON = new JSONArray();
        for (Feature f : getFeatures()) {
            if (f.isPresent()) {
                featuresJSON.add(f.toJSON());
            }
        }
        return featuresJSON;
    }

    @Override
    protected JSONArray getDisordersJSON()
    {
        JSONArray disordersJSON = new JSONArray();
        for (Disorder disorder : getDisorders()) {
            disordersJSON.add(disorder.toJSON());
        }
        return disordersJSON;
    }

    /**
     * Find, remove, and return all terms with given ancestor.
     *
     * @param terms the terms, modified by removing terms with given ancestor
     * @param ancestor the ancestor to search for
     * @return the terms with the given ancestor (removed from given terms)
     */
    private Collection<VocabularyTerm> popTermsWithAncestor(Collection<VocabularyTerm> terms, VocabularyTerm ancestor)
    {
        Collection<VocabularyTerm> matched = new HashSet<VocabularyTerm>();
        for (VocabularyTerm term : terms) {
            if (term.getAncestorsAndSelf().contains(ancestor)) {
                matched.add(term);
            }
        }
        terms.removeAll(matched);
        return matched;
    }

    /**
     * Finds the best term match, removes these terms, and return the JSON for that match.
     *
     * @param refTerms the terms in the reference
     * @param matchTerms the terms in the match
     * @param matchFeatureLookup a mapping from VocabularyTerm IDs back to the original Features in the match patient
     * @param refFeatureLookup a mapping from VocabularyTerm IDs back to the original Features in the reference patient
     * @return the FeatureClusterView of the best-matching features from refTerms and matchTerms (removes the matched
     *         terms from the passed lists) or null if the terms are not a good match (the term collections are then
     *         unchanged)
     */
    private FeatureClusterView popBestFeatureCluster(Collection<VocabularyTerm> matchTerms,
        Collection<VocabularyTerm> refTerms, Map<String, Feature> matchFeatureLookup,
        Map<String, Feature> refFeatureLookup)
    {
        Collection<VocabularyTerm> sharedAncestors = getAncestors(refTerms);
        sharedAncestors.retainAll(getAncestors(matchTerms));

        // Find ancestor with highest (normalized) information content
        VocabularyTerm ancestor = null;
        double ancestorScore = Double.NEGATIVE_INFINITY;
        for (VocabularyTerm term : sharedAncestors) {
            Double termIC = termICs.get(term);
            if (termIC == null) {
                termIC = 0.0;
            }

            double termScore = termIC / maxIC;
            if (termScore > ancestorScore) {
                ancestorScore = termScore;
                ancestor = term;
            }
        }

        // If the top-scoring ancestor is the root (or phenotype root), report everything remaining as unmatched
        if (ancestor == null || HP_ROOT.equals(ancestor.getId()) || PHENOTYPE_ROOT.equals(ancestor.getId())) {
            return null;
        }

        // Find, remove, and return all ref and match terms under the selected ancestor
        Collection<VocabularyTerm> matchMatched = popTermsWithAncestor(matchTerms, ancestor);
        Collection<VocabularyTerm> refMatched = popTermsWithAncestor(refTerms, ancestor);

        // Return match json from matched terms
        FeatureClusterView cluster = createFeatureClusterView(termsToFeatures(matchMatched, matchFeatureLookup),
            termsToFeatures(refMatched, refFeatureLookup), this.access, ancestor, ancestorScore);
        return cluster;
    }

    private Collection<FeatureClusterView> getMatchedFeatures()
    {
        Collection<FeatureClusterView> clusters = new LinkedList<FeatureClusterView>();

        // Get term -> feature lookups for creating cluster views
        Map<String, Feature> matchFeatureLookup = getTermLookup(this.match);
        Map<String, Feature> refFeatureLookup = getTermLookup(this.reference);

        // Get the present vocabulary terms
        Collection<VocabularyTerm> matchTerms = getPresentPatientTerms(this.match);
        Collection<VocabularyTerm> refTerms = getPresentPatientTerms(this.reference);

        // Keep removing most-related sets of terms until none match lower than HP roots
        while (!refTerms.isEmpty() && !matchTerms.isEmpty()) {
            FeatureClusterView cluster =
                popBestFeatureCluster(matchTerms, refTerms, matchFeatureLookup, refFeatureLookup);
            if (cluster == null) {
                break;
            }
            clusters.add(cluster);
        }

        // Add any unmatched terms
        if (!refTerms.isEmpty() || !matchTerms.isEmpty()) {
            FeatureClusterView cluster = createFeatureClusterView(termsToFeatures(matchTerms, matchFeatureLookup),
                termsToFeatures(refTerms, refFeatureLookup), this.access, null, 0.0);
            clusters.add(cluster);
        }
        return clusters;
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
        Collection<Feature> features = new ArrayList<Feature>();
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

    @Override
    protected JSONArray getFeatureMatchesJSON()
    {
        // Get list of clusters and convert to JSON
        JSONArray matchesJSON = new JSONArray();
        Collection<FeatureClusterView> clusters = getMatchedFeatures();
        for (FeatureClusterView cluster : clusters) {
            matchesJSON.add(cluster.toJSON());
        }
        return matchesJSON;
    }
}
