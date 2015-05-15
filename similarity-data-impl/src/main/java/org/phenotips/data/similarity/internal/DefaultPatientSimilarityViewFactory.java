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

import org.phenotips.data.Patient;
import org.phenotips.data.permissions.AccessLevel;
import org.phenotips.data.permissions.PermissionsManager;
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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
    protected PermissionsManager permissions;

    /** Needed by {@link DefaultAccessType} for checking if a given access level provides read access to patients. */
    @Inject
    @Named("view")
    protected AccessLevel viewAccess;

    /** Needed by {@link DefaultAccessType} for checking if a given access level provides limited access to patients. */
    @Inject
    @Named("match")
    protected AccessLevel matchAccess;

    /** Provides access to the term ontology. */
    @Inject
    protected VocabularyManager ontologyManager;

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
        AccessType access = new DefaultAccessType(this.permissions.getPatientAccess(match).getAccessLevel(),
            this.viewAccess, this.matchAccess);
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
     * @param prob
     * @return probability bounded between (0, 1) exclusive
     */
    private static double limitProb(double prob)
    {
        return Math.min(Math.max(prob, EPS), 1 - EPS);
    }

    /**
     * Return all terms in the ontology.
     *
     * @param ontology the ontology to query
     * @return a Collection of all OntologyTerms in the ontology
     */
    private Collection<VocabularyTerm> queryAllTerms(Vocabulary ontology)
    {
        this.logger.info("Querying all terms in ontology: " + ontology.getAliases().iterator().next());
        Map<String, String> queryAll = new HashMap<String, String>();
        queryAll.put("id", "*");
        Map<String, String> queryAllParams = new HashMap<String, String>();
        queryAllParams.put(CommonParams.ROWS, String.valueOf(ontology.size()));
        Collection<VocabularyTerm> results = ontology.search(queryAll, queryAllParams);
        this.logger.info(String.format("  ... found %d entries.", results.size()));
        return results;
    }

    /**
     * Return a mapping from OntologyTerms to their children in the given ontology.
     *
     * @param ontology the ontology
     * @return a map from each term to the children in ontology
     */
    private Map<VocabularyTerm, Collection<VocabularyTerm>> getChildrenMap(Vocabulary ontology)
    {
        Map<VocabularyTerm, Collection<VocabularyTerm>> children = new HashMap<>();
        this.logger.info("Getting all children of ontology terms...");
        Collection<VocabularyTerm> terms = queryAllTerms(ontology);
        for (VocabularyTerm term : terms) {
            for (VocabularyTerm parent : term.getParents()) {
                // Add term to parent's set of children
                Collection<VocabularyTerm> parentChildren = children.get(parent);
                if (parentChildren == null) {
                    parentChildren = new ArrayList<VocabularyTerm>();
                    children.put(parent, parentChildren);
                }
                parentChildren.add(term);
            }
        }
        this.logger.info(String.format("cached children of %d ontology terms.", children.size()));
        return children;
    }

    /**
     * Helper method to recursively fill a map with the descendants of all terms under a root term.
     *
     * @param root the root of the ontology to explore
     * @param termChildren a map from each ontology term to its children
     * @param termDescendants a partially-complete map of terms to descendants, filled in by this method
     */
    private void setDescendantsMap(VocabularyTerm root, Map<VocabularyTerm, Collection<VocabularyTerm>> termChildren,
        Map<VocabularyTerm, Collection<VocabularyTerm>> termDescendants)
    {
        if (termDescendants.containsKey(root)) {
            return;
        }
        // Compute descendants from children
        Collection<VocabularyTerm> descendants = new HashSet<VocabularyTerm>();
        Collection<VocabularyTerm> children = termChildren.get(root);
        if (children != null) {
            for (VocabularyTerm child : children) {
                // Recurse on child
                setDescendantsMap(child, termChildren, termDescendants);
                // On return, termDescendants[child] should be non-null
                Collection<VocabularyTerm> childDescendants = termDescendants.get(child);
                if (childDescendants != null) {
                    descendants.addAll(childDescendants);
                } else {
                    this.logger.warn("Descendants were null after recursion");
                }
            }
        }
        descendants.add(root);
        termDescendants.put(root, descendants);
    }

    /**
     * Return a mapping from OntologyTerms to their descendants in the part of the ontology under root.
     *
     * @param root the root of the ontology to explore
     * @param termChildren a map from each ontology term to its children
     * @return a map from each term to the descendants in ontology
     */
    private Map<VocabularyTerm, Collection<VocabularyTerm>> getDescendantsMap(VocabularyTerm root,
        Map<VocabularyTerm, Collection<VocabularyTerm>> termChildren)
    {
        Map<VocabularyTerm, Collection<VocabularyTerm>> termDescendants =
            new HashMap<VocabularyTerm, Collection<VocabularyTerm>>();
        setDescendantsMap(root, termChildren, termDescendants);
        return termDescendants;
    }

    /**
     * Return the observed frequency distribution across provided HPO terms seen in MIM.
     *
     * @param mim the MIM ontology with diseases and symptom frequencies
     * @param hpo the human phenotype ontology
     * @param allowedTerms only frequencies for a subset of these terms will be returned
     * @return a map from VocabularyTerm to the absolute frequency (sum over all terms ~1)
     */
    @SuppressWarnings("unchecked")
    private Map<VocabularyTerm, Double> getTermFrequencies(Vocabulary mim, Vocabulary hpo,
        Collection<VocabularyTerm> allowedTerms)
    {
        Map<VocabularyTerm, Double> termFreq = new HashMap<VocabularyTerm, Double>();
        double freqDenom = 0.0;
        // Add up frequencies of each term across diseases
        Collection<VocabularyTerm> diseases = queryAllTerms(mim);
        Set<VocabularyTerm> ignoredSymptoms = new HashSet<VocabularyTerm>();
        for (VocabularyTerm disease : diseases) {
            // Get a Collection<String> of symptom HP IDs, or null
            Object symptomNames = disease.get("actual_symptom");
            if (symptomNames != null) {
                if (symptomNames instanceof Collection<?>) {
                    for (String symptomName : ((Collection<String>) symptomNames)) {
                        VocabularyTerm symptom = hpo.getTerm(symptomName);
                        if (!allowedTerms.contains(symptom)) {
                            ignoredSymptoms.add(symptom);
                            continue;
                        }
                        // Ideally use frequency with which symptom occurs in disease
                        // This information isn't prevalent or reliable yet, however
                        double freq = 1.0;
                        freqDenom += freq;
                        // Add to accumulated term frequency
                        Double prevFreq = termFreq.get(symptom);
                        if (prevFreq != null) {
                            freq += prevFreq;
                        }
                        termFreq.put(symptom, freq);
                    }
                } else {
                    String err = "Solr returned non-collection symptoms: " + String.valueOf(symptomNames);
                    this.logger.error(err);
                    throw new RuntimeException(err);
                }
            }
        }
        if (!ignoredSymptoms.isEmpty()) {
            this.logger.warn(String.format("Ignored %d symptoms", ignoredSymptoms.size()));
        }

        this.logger.info("Normalizing term frequency distribution...");
        // Normalize all the term frequencies to be a proper distribution
        for (Map.Entry<VocabularyTerm, Double> entry : termFreq.entrySet()) {
            entry.setValue(limitProb(entry.getValue() / freqDenom));
        }

        return termFreq;
    }

    /**
     * Return the information content of each VocabularyTerm in termFreq.
     *
     * @param termFreq the absolute frequency of each VocabularyTerm
     * @param termDescendants the descendants of each VocabularyTerm
     * @return a map from each term to the information content of that term
     */
    private Map<VocabularyTerm, Double> getTermICs(Map<VocabularyTerm, Double> termFreq,
        Map<VocabularyTerm, Collection<VocabularyTerm>> termDescendants)
    {
        Map<VocabularyTerm, Double> termICs = new HashMap<VocabularyTerm, Double>();

        for (VocabularyTerm term : termFreq.keySet()) {
            Collection<VocabularyTerm> descendants = termDescendants.get(term);
            if (descendants == null) {
                this.logger.warn("Found no descendants of term: " + term.getId());
            }
            // Sum up frequencies of all descendants
            double probMass = 0.0;
            for (VocabularyTerm descendant : descendants) {
                Double freq = termFreq.get(descendant);
                if (freq != null) {
                    probMass += freq;
                }
            }

            if (HP_ROOT.equals(term.getId())) {
                this.logger.warn(String
                    .format("Probability mass under %s should be 1.0, was: %.6f", HP_ROOT, probMass));
            }
            if (probMass > EPS) {
                probMass = limitProb(probMass);
                termICs.put(term, -Math.log(probMass));
            }
        }
        return termICs;
    }

    @Override
    public void initialize() throws InitializationException
    {
        this.logger.info("Initializing...");
        if (this.viewCache == null) {
            try {
                this.viewCache = new PairCache<PatientSimilarityView>();
            } catch (CacheException e) {
                this.logger.warn("Unable to create cache for PatientSimilarityViews");
            }
        }
        if (!DefaultPatientSimilarityView.isInitialized()) {
            // Load the OMIM/HPO mappings
            Vocabulary mim = this.ontologyManager.getVocabulary("MIM");
            Vocabulary hpo = this.ontologyManager.getVocabulary("HPO");
            VocabularyTerm hpRoot = hpo.getTerm(HP_ROOT);

            // Pre-compute HPO descendant lookups
            Map<VocabularyTerm, Collection<VocabularyTerm>> termChildren = getChildrenMap(hpo);
            Map<VocabularyTerm, Collection<VocabularyTerm>> termDescendants = getDescendantsMap(hpRoot, termChildren);

            // Compute prior frequencies of phenotypes (based on disease frequencies and phenotype prevalence)
            Map<VocabularyTerm, Double> termFreq = getTermFrequencies(mim, hpo, termDescendants.keySet());

            // Pre-compute term information content (-logp), for each node t (i.e. t.inf).
            Map<VocabularyTerm, Double> termICs = getTermICs(termFreq, termDescendants);

            // Give data to views to use
            this.logger.info("Setting view globals...");
            DefaultPatientSimilarityView.initializeStaticData(termICs, this.ontologyManager);
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
