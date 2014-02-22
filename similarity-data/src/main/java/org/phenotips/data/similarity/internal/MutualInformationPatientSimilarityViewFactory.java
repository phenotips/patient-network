/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.phenotips.data.similarity.internal;

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
import org.phenotips.data.Patient;
import org.phenotips.data.permissions.AccessLevel;
import org.phenotips.data.permissions.PermissionsManager;
import org.phenotips.data.similarity.AccessType;
import org.phenotips.data.similarity.PatientSimilarityView;
import org.phenotips.data.similarity.PatientSimilarityViewFactory;
import org.phenotips.ontology.OntologyManager;
import org.phenotips.ontology.OntologyService;
import org.phenotips.ontology.OntologyTerm;
import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.phase.Initializable;
import org.xwiki.component.phase.InitializationException;

/**
 * Implementation of {@link PatientSimilarityViewFactory} which uses the mutual information to score pairs of patients.
 * 
 * @version $Id$
 * @since
 */
@Component
@Named("mi")
@Singleton
public class MutualInformationPatientSimilarityViewFactory implements PatientSimilarityViewFactory, Initializable
{
    /*
     * Pseudo-code of algorithm:
     * 
     * create mapping from parent nodes to list of children
     * root = "HP:0000118"
     * eps = 1e-9
     * freqDenom = 0
     * for term in HPO:
     *   termFreq[term] = 0
     * 
     * for disease in diseaseTraits:
     *   for term in disease.symptoms:
     *     occurrence = disease.getFrequency(term)
     *     termFreq[term] += occurrence
     *     freqDenom += occurrence
     * put("id", "HP:*")
     * put("id", "HP:*")
     * for term in HPO:
     *   termFreq[term] = min(max(termFreq[term] / freqDenom, eps), 1-eps)
     * 
     * def setInformation(node):
     *   probMass = termFreq[node]
     *   for child in children(node):
     *     probMass += setInformation(child)
     *     
     *   probMass = min(max(probMass, eps), 1-eps)
     *   termIC[node] = -log(probMass)
     *   return probMass
     * 
     * setInformation(root)
     * 
     * for term in HPO:
     *   parents = set(term.getParents())
     *   sibling_prob_mass = 0
     *   for parent in parents:
     *     for poss_sibling in children(parent):
     *       if poss_sibling.getParents().issupersetof(parents):
     *         sibling_prob_mass += exp(-termIC(poss_sibling))
     *         
     *   sibling_prob_mass = min(max(sibling_prob_mass, eps), 1-eps)
     *   probCondParents[term] = termIC(term) + log(sibling_prob_mass)
     * 
     */

    /** The root of the phenotypic abnormality portion of HPO. */
    private static final String HP_ROOT = "HP:0000118";

    /** Logging helper object. */
    @Inject
    private Logger logger;

    /** Computes the real access level for a patient. */
    @Inject
    private PermissionsManager permissions;

    /** Needed by {@link DefaultAccessType} for checking if a given access level provides read access to patients. */
    @Inject
    @Named("view")
    private AccessLevel viewAccess;

    /** Needed by {@link DefaultAccessType} for checking if a given access level provides limited access to patients. */
    @Inject
    @Named("match")
    private AccessLevel matchAccess;

    /** Provides access to the term ontology. */
    @Inject
    private OntologyManager ontologyManager;

    @Override
    public PatientSimilarityView makeSimilarPatient(Patient match, Patient reference) throws IllegalArgumentException
    {
        if (match == null || reference == null) {
            throw new IllegalArgumentException("Similar patients require both a match and a reference");
        }
        AccessType access =
            new DefaultAccessType(this.permissions.getPatientAccess(match).getAccessLevel(), this.viewAccess,
                this.matchAccess);
        logger.error("Creating view for " + reference.getDocument().getName() + " + " + match.getDocument().getName());
        MutualInformationPatientSimilarityView view =
            new MutualInformationPatientSimilarityView(match, reference, access, ontologyManager, logger);
        logger.error("  score:" + view.getScore());
        return view;
    }

    @Override
    public PatientSimilarityView convert(PatientSimilarityView patientPair)
    {
        AccessType access = new DefaultAccessType(patientPair.getAccess(), this.viewAccess, this.matchAccess);
        return new MutualInformationPatientSimilarityView(patientPair, patientPair.getReference(), access, ontologyManager, logger);
    }
    
    /**
     * Bound probability to between (0, 1) exclusive.
     * 
     * @param prob
     * @return probability bounded between (0, 1) exclusive
     */
    private double limitProb(double prob)
    {
        return Math.min(Math.max(prob, 1e-9), 1 - 1e-9);
    }

    /**
     * Return all terms in the ontology.
     * 
     * @param ontology the ontology to query
     * @return a Collection of all OntologyTerms in the ontology
     */
    private Collection<OntologyTerm> queryAllTerms(OntologyService ontology)
    {
        logger.error("Querying all terms in ontology: " + ontology.getAliases().iterator().next());
        Map<String, String> queryAll = new HashMap<String, String>();
        queryAll.put("id", "*");
        Map<String, String> queryAllParams = new HashMap<String, String>();
        queryAllParams.put(CommonParams.ROWS, String.valueOf(ontology.size()));
        Collection<OntologyTerm> results = ontology.search(queryAll, queryAllParams);
        logger.error("  ... found " + results.size() + " entries");
        return results;
    }

    /**
     * Return a mapping from OntologyTerms to their children in the given ontology.
     * 
     * @param ontology the ontology
     * @return a map from each term to the children in ontology
     */
    private Map<OntologyTerm, Collection<OntologyTerm>> getChildrenMap(OntologyService ontology)
    {
        Map<OntologyTerm, Collection<OntologyTerm>> children = new HashMap<OntologyTerm, Collection<OntologyTerm>>();

        Collection<OntologyTerm> terms = queryAllTerms(ontology);
        for (OntologyTerm term : terms) {
            for (OntologyTerm parent : term.getParents()) {
                // Add term to parent's set of children
                Collection<OntologyTerm> parentChildren = children.get(parent);
                if (parentChildren == null) {
                    parentChildren = new ArrayList<OntologyTerm>();
                    children.put(parent, parentChildren);
                }
                parentChildren.add(term);
            }
        }
        return children;
    }

    /**
     * Helper method to recursively fill a map with the descendants of all terms under a root term.
     * 
     * @param root the root of the ontology to explore
     * @param termChildren a map from each ontology term to its children
     * @param termDescendants a partially-complete map of terms to descendants, filled in by this method
     */
    private void setDescendantsMap(OntologyTerm root, Map<OntologyTerm, Collection<OntologyTerm>> termChildren,
        Map<OntologyTerm, Collection<OntologyTerm>> termDescendants)
    {
        if (termDescendants.containsKey(root)) {
            return;
        }
        // Compute descendants from children
        Collection<OntologyTerm> descendants = new HashSet<OntologyTerm>();
        Collection<OntologyTerm> children = termChildren.get(root);
        if (children != null) {
            for (OntologyTerm child : children) {
                // Recurse on child
                setDescendantsMap(child, termChildren, termDescendants);
                // On return, termDescendants[child] should be non-null
                Collection<OntologyTerm> childDescendants = termDescendants.get(child);
                if (childDescendants != null) {
                    descendants.addAll(childDescendants);
                } else {
                    logger.error("Descendants were null after recursion");
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
    private Map<OntologyTerm, Collection<OntologyTerm>> getDescendantsMap(OntologyTerm root,
        Map<OntologyTerm, Collection<OntologyTerm>> termChildren)
    {
        Map<OntologyTerm, Collection<OntologyTerm>> termDescendants =
            new HashMap<OntologyTerm, Collection<OntologyTerm>>();
        setDescendantsMap(root, termChildren, termDescendants);
        return termDescendants;
    }

    /**
     * Return the observed frequency distribution across provided HPO terms seen in MIM.
     * 
     * @param mim the MIM ontology with diseases and symptom frequencies
     * @param hpo the human phenotype ontology
     * @param allowedTerms only frequencies for a subset of these terms will be returned
     * @return a map from OntologyTerm to the absolute frequency (sum over all terms ~1)
     */
    @SuppressWarnings("unchecked")
    private Map<OntologyTerm, Double> getTermFrequencies(OntologyService mim, OntologyService hpo,
        Collection<OntologyTerm> allowedTerms)
    {
        Map<OntologyTerm, Double> termFreq = new HashMap<OntologyTerm, Double>();
        double freqDenom = 0.0;
        // Add up frequencies of each term across diseases
        // TODO: Currently uniform weight across diseases
        Collection<OntologyTerm> diseases = queryAllTerms(mim);
        Set<OntologyTerm> ignoredSymptoms = new HashSet<OntologyTerm>();
        for (OntologyTerm disease : diseases) {
            // Get a Collection<String> of symptom HP IDs, or null
            Object symptomNames = disease.get("actual_symptom");
            if (symptomNames != null) {
                if (symptomNames instanceof Collection< ? >) {
                    for (String symptomName : ((Collection<String>) symptomNames)) {
                        OntologyTerm symptom = hpo.getTerm(symptomName);
                        if (!allowedTerms.contains(symptom)) {
                            ignoredSymptoms.add(symptom);
                            continue;
                        }
                        // Get frequency with which symptom occurs in disease, if annotated
                        // TODO: fix with actual frequency
                        Double freq = null;
                        if (freq == null) {
                            // Default frequency
                            freq = 0.25;
                        }
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
                    logger.error(err);
                    throw new RuntimeException(err);
                }
            }
        }
        if (!ignoredSymptoms.isEmpty()) {
            logger.error(String.format("Ignored %d symptoms", ignoredSymptoms.size()));
        }

        logger.error("Normalizing term frequency distribution...");
        // Normalize all the term frequencies to be a proper distribution
        for (Map.Entry<OntologyTerm, Double> entry : termFreq.entrySet()) {
            entry.setValue(limitProb(entry.getValue() / freqDenom));
        }

        return termFreq;
    }

    /**
     * Return the information content of each OntologyTerm in termFreq.
     * 
     * @param termFreq the absolute frequency of each OntologyTerm
     * @param termDescendants the descendants of each OntologyTerm
     * @return a map from each term to the information content of that term
     */
    private Map<OntologyTerm, Double> getTermICs(Map<OntologyTerm, Double> termFreq,
        Map<OntologyTerm, Collection<OntologyTerm>> termDescendants)
    {
        Map<OntologyTerm, Double> termIC = new HashMap<OntologyTerm, Double>();

        for (OntologyTerm term : termFreq.keySet()) {
            Collection<OntologyTerm> descendants = termDescendants.get(term);
            if (descendants == null) {
                logger.error("Found no descendants of term: " + term.getId());
            }
            // Sum up frequencies of all descendants
            double probMass = 0.0;
            for (OntologyTerm descendant : descendants) {
                Double freq = termFreq.get(descendant);
                if (freq != null) {
                    probMass += freq;
                }
            }

            if (HP_ROOT.equals(term.getId())) {
                logger.error(String.format("Probability mass under %s should be 1.0, was: %.6f", HP_ROOT, probMass));
            }
            probMass = limitProb(probMass);
            termIC.put(term, -Math.log(probMass));
        }
        return termIC;
    }

    /**
     * Return the (approximate) conditional information content (IC) of a term, given its parents. Approximation is
     * IC(term) - max(IC(parent) | parent in parents(term))
     * 
     * @param term the OntologyTerm to compute the conditional IC
     * @param termIC the pre-computed IC of each term
     * @return the conditional IC of term, given its parents
     */
    private double getTermCondProb(OntologyTerm term, Map<OntologyTerm, Double> termIC)
    {
        // Compute the max IC of parents of term
        double maxParentIC = 0;
        for (OntologyTerm parent : term.getParents()) {
            Double parentIC = termIC.get(parent);
            if (parentIC != null) {
                maxParentIC = Math.max(maxParentIC, parentIC);
            }
        }
        Double thisIC = termIC.get(term);
        if (thisIC == null) {
            thisIC = 0.0;
        }
        return thisIC - maxParentIC;
    }

    /**
     * Return the (approximate) conditional information content (IC) of all terms, given their parents. Approximation is
     * IC(term) - max(IC(parent) | parent in parents(term))
     * 
     * @param termIC the pre-computed IC of each term
     * @return map of the conditional IC of each term, given its parents
     */
    private Map<OntologyTerm, Double> getCondProbs(Map<OntologyTerm, Double> termIC)
    {
        Map<OntologyTerm, Double> parentCondIC = new HashMap<OntologyTerm, Double>();
        for (OntologyTerm term : termIC.keySet()) {
            double condIC = getTermCondProb(term, termIC);
            // logger.error("Term: " + term.getId() + ", IC|parents: " + condIC);
            parentCondIC.put(term, condIC);
        }
        return parentCondIC;
    }

    @Override
    public void initialize() throws InitializationException
    {
        // Load the OMIM/HPO mappings
        OntologyService mim = ontologyManager.getOntology("MIM");
        OntologyService hpo = ontologyManager.getOntology("HPO");
        OntologyTerm hpRoot = hpo.getTerm(HP_ROOT);

        // Pre-compute HPO descendant lookups
        Map<OntologyTerm, Collection<OntologyTerm>> termChildren = getChildrenMap(hpo);
        Map<OntologyTerm, Collection<OntologyTerm>> termDescendants = getDescendantsMap(hpRoot, termChildren);

        // Compute prior frequencies of phenotypes (based on disease frequencies and phenotype prevalence)
        Map<OntologyTerm, Double> termFreq = getTermFrequencies(mim, hpo, termDescendants.keySet());

        // Pre-compute term information content (-logp), for each node t (i.e. t.inf).
        Map<OntologyTerm, Double> termIC = getTermICs(termFreq, termDescendants);

        logger.error("Calculating conditional probabilities...");
        // Pre-computed bound on -logP(t|parents(t)), for each node t (i.e. t.cond_inf).
        Map<OntologyTerm, Double> parentCondIC = getCondProbs(termIC);
        assert termIC.size() == parentCondIC.size() : "Mismatch between sizes of IC and IC|parent maps";
        assert Math.abs(parentCondIC.get(hpRoot)) < 1e-6 : "IC(root|parents) should equal 0.0";

        logger.error("Setting view globals...");
        // Give data to view to use
        MutualInformationPatientSimilarityView.setConditionalICs(parentCondIC);
        MutualInformationPatientSimilarityView.setICs(termIC);
    }
}
