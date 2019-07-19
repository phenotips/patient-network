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
package org.phenotips.data.similarity.internal;

import org.phenotips.components.ComponentManagerRegistry;
import org.phenotips.data.Disorder;
import org.phenotips.data.Feature;
import org.phenotips.data.Patient;
import org.phenotips.data.PatientData;
import org.phenotips.data.PatientWritePolicy;
import org.phenotips.data.permissions.internal.EntityAccessManager;
import org.phenotips.data.similarity.AccessType;
import org.phenotips.data.similarity.DisorderSimilarityView;
import org.phenotips.data.similarity.PatientGenotypeSimilarityView;
import org.phenotips.data.similarity.PatientPhenotypeSimilarityView;
import org.phenotips.data.similarity.genotype.RestrictedPatientGenotypeSimilarityView;
import org.phenotips.data.similarity.phenotype.DefaultPatientPhenotypeSimilarityView;
import org.phenotips.vocabulary.VocabularyManager;
import org.phenotips.vocabulary.VocabularyTerm;

import org.xwiki.component.manager.ComponentManager;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.users.UserManager;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import com.xpn.xwiki.api.Document;
import com.xpn.xwiki.doc.XWikiDocument;

/**
 * Implementation of {@link org.phenotips.data.similarity.PatientSimilarityView} that uses a mutual information metric
 * to score similar patients.
 *
 * @version $Id$
 * @since 1.0M1
 */
public class DefaultPatientSimilarityView extends AbstractPatientSimilarityView
{
    static {
        EntityAccessManager pa = null;
        UserManager um = null;
        try {
            ComponentManager ccm = ComponentManagerRegistry.getContextComponentManager();
            pa = ccm.getInstance(EntityAccessManager.class);
            um = ccm.getInstance(UserManager.class);
        } catch (Exception e) {
            LOGGER.error("Error loading static components: {}", e.getMessage(), e);
        }
        accessHelper = pa;
        userManager = um;
    }

    /** Pre-computed term information content (-logp), for each node t (i.e. t.inf). */
    private static Map<VocabularyTerm, Double> termICs;

    /** Provides access to the term vocabulary. */
    private static VocabularyManager vocabularyManager;

    /** Memoized match score. */
    private Double score;

    /** Links disorder values from this patient to the reference. */
    private Set<DisorderSimilarityView> matchedDisorders;

    /** Memoized genotype match, retrieved through getMatchedGenes. */
    private PatientGenotypeSimilarityView matchedGenes;

    /** Memoized phenotype match, retrieved through getMatchedFeatures. */
    private PatientPhenotypeSimilarityView matchedFeatures;

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
                "Static data of DefaultPatientSimilarityView was not initilized before instantiation";
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
    }

    /**
     * Create an instance of the PatientPhenotypeSimilarityView for this PatientSimilarityView.
     *
     * @param match the features in the matched patient
     * @param reference the features in the reference patient
     * @param access the access level of the match
     * @return the PatientPhenotypeSimilarityView for the pair of patients
     */
    protected PatientPhenotypeSimilarityView createPhenotypeSimilarityView(Set<? extends Feature> match,
        Set<? extends Feature> reference, AccessType access)
    {
        if (!DefaultPatientPhenotypeSimilarityView.isInitialized()) {
            DefaultPatientPhenotypeSimilarityView.initializeStaticData(vocabularyManager);
        }
        return new DefaultPatientPhenotypeSimilarityView(match, reference);
    }

    /**
     * Create an instance of the PatientGenotypeSimilarityView for this PatientSimilarityView.
     *
     * @param match  match patient
     * @param reference the  reference patient
     * @param access the access level
     * @return the PatientGenotypeSimilarityView for the pair of patients
     */
    protected PatientGenotypeSimilarityView createGenotypeSimilarityView(Patient match, Patient reference,
        AccessType access)
    {
        return new RestrictedPatientGenotypeSimilarityView(match, reference, this.access);
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
        Set<Disorder> result = new HashSet<>();
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
            Set<DisorderSimilarityView> result = new HashSet<>();
            for (Disorder disorder : this.match.getDisorders()) {
                result.add(createDisorderSimilarityView(disorder,
                    findMatchingDisorder(disorder, this.reference.getDisorders()), this.access));
            }
            for (Disorder disorder : this.reference.getDisorders()) {
                if (this.match == null || findMatchingDisorder(disorder, this.match.getDisorders()) == null) {
                    result.add(createDisorderSimilarityView(null, disorder, this.access));
                }
            }
            Set<Disorder> clinicalMatchDisorders = getClinicalDisorders(this.match);
            Set<Disorder> clinicalRefDisorders = getClinicalDisorders(this.reference);
            for (Disorder disorder : clinicalMatchDisorders) {
                result.add(createDisorderSimilarityView(disorder,
                    findMatchingDisorder(disorder, clinicalRefDisorders), this.access));
            }
            for (Disorder disorder : clinicalRefDisorders) {
                if (this.match == null || findMatchingDisorder(disorder, clinicalMatchDisorders) == null) {
                    result.add(createDisorderSimilarityView(null, disorder, this.access));
                }
            }
            this.matchedDisorders = Collections.unmodifiableSet(result);
        }
        return this.matchedDisorders;
    }

    /**
     * Get the genotype similarity view for this pair of patients, lazily evaluated and memoized.
     *
     * @return the genotype similarity view for this pair of patients
     */
    private PatientGenotypeSimilarityView getMatchedGenes()
    {
        if (this.matchedGenes == null) {
            this.matchedGenes = createGenotypeSimilarityView(this.match, this.reference, this.access);
        }
        return this.matchedGenes;
    }

    /**
     * Get the phenotype similarity view for this pair of patients, lazily evaluated and memoized.
     *
     * @return the phenotype similarity view for this pair of patients
     */
    private PatientPhenotypeSimilarityView getMatchedFeatures()
    {
        if (this.matchedFeatures == null) {
            this.matchedFeatures =
                createPhenotypeSimilarityView(this.match.getFeatures(), this.reference.getFeatures(),
                    this.access);
        }
        return this.matchedFeatures;
    }

    @Override
    public double getGenotypeScore()
    {
        return getMatchedGenes().getScore();
    }

    @Override
    public Set<String> getMatchingGenes()
    {
        return getMatchedGenes().getMatchingGenes();
    }

    @Override
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
                Set<VocabularyTerm> commonAncestors = new HashSet<>();
                commonAncestors.addAll(refAncestors);
                commonAncestors.retainAll(matchAncestors);

                Set<VocabularyTerm> allAncestors = new HashSet<>();
                allAncestors.addAll(refAncestors);
                allAncestors.addAll(matchAncestors);

                double baseScore = getTermICs(commonAncestors) / getTermICs(allAncestors);
                return adjustScoreWithDisordersScore(baseScore);
            }
        }
    }

    /**
     * Return a (potentially empty) collection of terms present in the patient.
     *
     * @param features the patient features to process
     * @return a collection of terms present in the patient
     */
    private Collection<VocabularyTerm> getPresentPatientTerms(Patient patient)
    {
        Set<VocabularyTerm> terms = new HashSet<>();
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
     * Return the set of terms implied by a collection of features in the vocabulary.
     *
     * @param terms a collection of terms
     * @return all provided VocabularyTerm terms and their ancestors
     */
    private Set<VocabularyTerm> getAncestors(Collection<VocabularyTerm> terms)
    {
        Set<VocabularyTerm> ancestors = new HashSet<>(terms);
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
     * Adjust the similarity score by taking into account common disorders. Matching disorders will boost the base score
     * given by the phenotypic similarity, while unmatched disorders don't affect the score at all.
     *
     * @param baseScore the score given by features alone, a number between {@code 0} and {@code 1}
     * @return the adjusted similarity score, boosted closer to {@code 1} if there are common disorders between this
     *         patient and the reference patient, or the unmodified base score otherwise; the score is never lowered,
     *         and never goes above {@code 1}
     * @see #getPhenotypeScore()
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
            // Factor in overlap between candidate genes
            double genotypeScore = getGenotypeScore();
            // Return boosted score
            this.score = 0.5 * (phenotypeScore + genotypeScore);
        }
        return this.score;
    }

    @Override
    public EntityReference getType()
    {
        return Patient.CLASS_REFERENCE;
    }

    @Override
    public String getName()
    {
        return getExternalId();
    }

    @Override
    public String getDescription()
    {
        return "";
    }

    @Override
    public JSONArray getGenesJSON()
    {
        return getMatchedGenes().toJSON();
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
                featuresJSON.put(f.toJSON());
            }
        }
        return featuresJSON;
    }

    @Override
    protected JSONArray getDisordersJSON()
    {
        JSONArray disordersJSON = new JSONArray();
        for (Disorder disorder : getDisorders()) {
            disordersJSON.put(disorder.toJSON());
        }
        return disordersJSON;
    }

    @Override
    public JSONArray getFeatureMatchesJSON()
    {
        return getMatchedFeatures().toJSON();
    }

    Set<Disorder> getClinicalDisorders(Patient patient)
    {
        PatientData<Disorder> data = patient.getData("clinical-diagnosis");
        Set<Disorder> disorders = new TreeSet<>();
        if (data != null) {
            Iterator<Disorder> iterator = data.iterator();
            while (iterator.hasNext()) {
                Disorder disorder = iterator.next();
                disorders.add(disorder);
            }
        }
        return Collections.unmodifiableSet(disorders);
    }

    @Override
    public void updateFromJSON(JSONObject arg0, PatientWritePolicy arg1)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Document getSecureDocument()
    {
        return this.match.getSecureDocument();
    }

    @Override
    public XWikiDocument getXDocument()
    {
        return this.match.getXDocument();
    }

    @Override
    public DocumentReference getDocumentReference()
    {
        return this.match.getDocumentReference();
    }

    /**
     * {@inheritDoc}
     *
     * @deprecated use {@link #getDocumentReference()} instead
     */
    @Deprecated
    @Override
    public DocumentReference getDocument()
    {
        return this.match.getDocumentReference();
    }
}
