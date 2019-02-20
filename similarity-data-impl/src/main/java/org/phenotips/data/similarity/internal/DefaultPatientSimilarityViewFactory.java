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

import org.phenotips.data.Patient;
import org.phenotips.data.permissions.AccessLevel;
import org.phenotips.data.permissions.EntityAccess;
import org.phenotips.data.permissions.EntityPermissionsManager;
import org.phenotips.data.permissions.Visibility;
import org.phenotips.data.similarity.AccessType;
import org.phenotips.data.similarity.PatientSimilarityView;
import org.phenotips.data.similarity.PatientSimilarityViewFactory;
import org.phenotips.vocabulary.Vocabulary;
import org.phenotips.vocabulary.VocabularyManager;
import org.phenotips.vocabulary.VocabularyTerm;

import org.xwiki.cache.CacheException;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.phase.Initializable;
import org.xwiki.component.phase.InitializationException;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.solr.common.params.CommonParams;
import org.slf4j.Logger;

/**
 * Implementation of {@link org.phenotips.data.similarity.PatientSimilarityViewFactory} which uses the mutual
 * information to score pairs of patients.
 *
 * @version $Id$
 * @since 1.0M1
 */
@Component
@Singleton
public class DefaultPatientSimilarityViewFactory implements PatientSimilarityViewFactory, Initializable
{
    /** The root of the phenotypic abnormality portion of HPO. */
    private static final String HP_ROOT = "HP:0000118";

    /** Small value used to round things too close to 0 or 1. */
    private static final double EPS = 1e-9;

    /** Logging helper object. */
    @Inject
    protected Logger logger;

    /** Computes the real access level for a patient. */
    @Inject
    @Named("secure")
    protected EntityPermissionsManager permissions;

    /** Needed by {@link DefaultAccessType} for checking if a given access level provides read access to patients. */
    @Inject
    @Named("view")
    protected AccessLevel viewAccess;

    /** Needed by {@link DefaultAccessType} for checking if a given access level provides limited access to patients. */
    @Inject
    @Named("match")
    protected AccessLevel matchAccess;

    /** The minimal visibility level needed for including a patient in the result. */
    @Inject
    @Named("matchable")
    protected Visibility matchVisibility;

    /** Provides access to the term vocabulary. */
    @Inject
    protected VocabularyManager vocabularyManager;

    /** Cache for patient similarity views. */
    private PairCache<PatientSimilarityView> viewCache;

    /**
     * Create an instance of the PatientSimilarityView for this PatientSimilarityViewFactory. This can be overridden to
     * have the same PatientSimilarityViewFactory functionality with a different PatientSimilarityView implementation.
     *
     * @param match the match patient
     * @param reference the reference patient
     * @param access the access level
     * @return a PatientSimilarityView for the patients
     */
    protected PatientSimilarityView createPatientSimilarityView(Patient match, Patient reference, AccessType access)
    {
        return new DefaultPatientSimilarityView(match, reference, access);
    }

    /**
     * Get PatientSimilarityView from cache or create a new one and add to the cache.
     *
     * @param match the match patient
     * @param reference the reference patient
     * @param access the access level
     * @return a PatientSimilarityView for the patients
     */
    protected PatientSimilarityView getCachedPatientSimilarityView(Patient match, Patient reference, AccessType access)
    {
        // Get potentially-cached patient similarity view
        String cacheKey = match.getId() + '|' + reference.getId() + '|' + access.getAccessLevel().getName();
        PatientSimilarityView result = null;
        if (this.viewCache != null) {
            // result = this.viewCache.get(cacheKey);
        }
        if (result == null) {
            result = createPatientSimilarityView(match, reference, access);
            if (this.viewCache != null) {
                this.viewCache.set(match.getId(), reference.getId(), cacheKey, result);
            }
        }
        return result;
    }

    @Override
    public PatientSimilarityView makeSimilarPatient(Patient match, Patient reference) throws IllegalArgumentException
    {
        if (match == null || reference == null) {
            throw new IllegalArgumentException("Similar patients require both a match and a reference");
        }

        // FIXME: a patient may have visibility level "matchable" but have access level below "match".
        //        this does not allow MME code to return matchable patients, and so a workaround is
        //        implemented below. The workaround should be removed if the condition is no longer possible
        //        - or the entire system need to be redone to use visibility instead of access
        EntityAccess entityAccess = this.permissions.getEntityAccess(match);
        AccessLevel useAccess = entityAccess.getAccessLevel();
        Visibility visibility = entityAccess.getVisibility();
        if (useAccess.compareTo(this.matchAccess) < 0 && visibility.compareTo(this.matchVisibility) >= 0) {
            // matches which are not matchable are filtered out elsewhere. But for MME requests which are
            // done as a guest user (who has no acceess to all patients), need to make sure the match can
            // be processed and returned, for which there should be at least "match" access level
            useAccess = this.matchAccess;
        }

        AccessType access = new DefaultAccessType(useAccess, this.viewAccess, this.matchAccess);
        return getCachedPatientSimilarityView(match, reference, access);
    }

    @Override
    public PatientSimilarityView convert(PatientSimilarityView patientPair)
    {
        Patient match;
        Patient reference;
        AccessType access;
        if (patientPair instanceof AbstractPatientSimilarityView) {
            AbstractPatientSimilarityView view = (AbstractPatientSimilarityView) patientPair;
            match = view.match;
            reference = view.reference;
            access = view.access;
        } else {
            match = patientPair;
            reference = patientPair.getReference();
            access = new DefaultAccessType(patientPair.getAccess(), this.viewAccess, this.matchAccess);
        }
        return getCachedPatientSimilarityView(match, reference, access);
    }

    /**
     * Bound probability to between (0, 1) exclusive.
     *
     * @param prob the input value to bound
     * @return probability bounded between (0, 1) exclusive
     */
    private static double limitProb(double prob)
    {
        return Math.min(Math.max(prob, EPS), 1 - EPS);
    }

    /**
     * Return all terms in the vocabulary.
     *
     * @param vocabulary the vocabulary to query
     * @return a Collection of all VocabularyTerms in the vocabulary
     */
    private Collection<VocabularyTerm> queryAllTerms(Vocabulary vocabulary)
    {
        this.logger.info("Querying all terms in vocabulary: " + vocabulary.getAliases().iterator().next());
        Map<String, String> queryAll = new HashMap<>();
        queryAll.put("id", "*");
        Map<String, String> queryAllParams = new HashMap<>();
        queryAllParams.put(CommonParams.ROWS, String.valueOf(vocabulary.size()));
        Collection<VocabularyTerm> results = vocabulary.search(queryAll, queryAllParams);
        this.logger.info(String.format("  ... found %d entries.", results.size()));
        return results;
    }

    /**
     * Return the observed information content across provided HPO terms seen in MIM.
     *
     * @param mim the MIM vocabulary with diseases and symptom frequencies
     * @param hpo the human phenotype ontology
     * @return a map from VocabularyTerm to the absolute frequency (sum over all terms ~1)
     */
    private Map<VocabularyTerm, Double> getTermICs(Vocabulary mim, Vocabulary hpo)
    {
        Map<VocabularyTerm, Double> termFreq = new HashMap<>();
        Map<VocabularyTerm, Double> termICs = new HashMap<>();
        // Add up frequencies of each term across diseases
        Collection<VocabularyTerm> diseases = queryAllTerms(mim);
        for (VocabularyTerm disease : diseases) {
            // Get a Collection<String> of symptom HP IDs, or null
            Collection<VocabularyTerm> symptoms = getDiseaseVocabularyTerms(hpo, disease);

            for (VocabularyTerm symptom : symptoms) {
                for (VocabularyTerm impliedTerm : symptom.getAncestorsAndSelf()) {
                    double freq = 1.0;
                    // Add to accumulated term frequency
                    Double prevFreq = termFreq.get(impliedTerm);
                    if (prevFreq != null) {
                        freq += prevFreq;
                    }
                    termFreq.put(impliedTerm, freq);
                }
            }
        }

        this.logger.info("Normalizing term frequency distribution...");
        // Normalize all the term frequencies to be a proper distribution
        VocabularyTerm root = hpo.getTerm(HP_ROOT);
        double maxFreq = termFreq.get(root);
        for (Map.Entry<VocabularyTerm, Double> entry : termFreq.entrySet()) {
            double p = limitProb(entry.getValue() / maxFreq);
            termICs.put(entry.getKey(), -Math.log(p));
        }

        return termICs;
    }

    /**
     * Return all terms associated with a particular disease.
     *
     * @param hpo the human phenotype ontology
     * @param disease the MIM disease vocabulary term
     * @return a collection of resolved VocabularyTerms associated with the disease, excluding terms that could not be
     *         resolved to a VocubularyTerm
     */
    @SuppressWarnings("unchecked")
    private Collection<VocabularyTerm> getDiseaseVocabularyTerms(Vocabulary hpo, VocabularyTerm disease)
    {
        Collection<VocabularyTerm> terms = new LinkedList<>();
        Object symptomNames = disease.get("actual_symptom");
        if (symptomNames != null) {
            if (symptomNames instanceof Collection<?>) {
                for (String symptomName : ((Collection<String>) symptomNames)) {
                    VocabularyTerm symptom = hpo.getTerm(symptomName);
                    // Ideally use frequency with which symptom occurs in disease
                    // This information isn't prevalent or reliable yet, however
                    if (symptom == null) {
                        this.logger.warn("Unable to find term in HPO: " + symptomName);
                    } else {
                        terms.add(symptom);
                    }
                }
            } else {
                String err = "Solr returned non-collection symptoms: " + String.valueOf(symptomNames);
                this.logger.error(err);
                throw new RuntimeException(err);
            }
        }
        return terms;
    }

    @Override
    public void initialize() throws InitializationException
    {
        this.logger.info("Initializing...");
        if (this.viewCache == null) {
            try {
                this.viewCache = new PairCache<>();
            } catch (CacheException e) {
                this.logger.warn("Unable to create cache for PatientSimilarityViews");
            }
        }
        if (!DefaultPatientSimilarityView.isInitialized()) {
            // Load the OMIM/HPO mappings
            Vocabulary mim = this.vocabularyManager.getVocabulary("MIM");
            Vocabulary hpo = this.vocabularyManager.getVocabulary("HPO");

            // Pre-compute term information content (-logp), for each node t (i.e. t.inf).
            Map<VocabularyTerm, Double> termICs = getTermICs(mim, hpo);

            // Give data to views to use
            this.logger.info("Setting view globals...");
            DefaultPatientSimilarityView.initializeStaticData(termICs, this.vocabularyManager);
        }
        this.logger.info("Initialized.");
    }

    /**
     * Clear all cached patient similarity data.
     */
    public void clearCache()
    {
        if (this.viewCache != null) {
            this.viewCache.removeAll();
            this.logger.info("Cleared cache.");
        }
    }

    /**
     * Clear all cached similarity data associated with a particular patient.
     *
     * @param id the document ID of the patient to remove from the cache
     */
    public void clearPatientCache(String id)
    {
        if (this.viewCache != null) {
            this.viewCache.removeAssociated(id);
            this.logger.info("Cleared patient from cache: " + id);
        }
    }
}
