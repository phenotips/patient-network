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

import org.phenotips.data.Feature;
import org.phenotips.data.Patient;
import org.phenotips.data.similarity.AccessType;
import org.phenotips.data.similarity.ExternalToolJobManager;
import org.phenotips.data.similarity.Genotype;
import org.phenotips.data.similarity.GenotypeSimilarityView;
import org.phenotips.ontology.OntologyManager;
import org.phenotips.ontology.OntologyTerm;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

/**
 * Implementation of {@link org.phenotips.data.similarity.PatientSimilarityView} that uses a mutual information metric
 * to score similar patients.
 * 
 * @version $Id$
 * @since
 */
public class MutualInformationPatientSimilarityView extends RestrictedPatientSimilarityView
{
    /** The overall root of the HPO. */
    private static final String HP_ROOT = "HP:0000001";

    /** The root of the phenotypic abnormality portion of HPO. */
    private static final String PHENOTYPE_ROOT = "HP:0000118";

    /** Pre-computed term information content (-logp), for each node t (i.e. t.inf). */
    private static Map<OntologyTerm, Double> termICs;

    /** The largest IC found, for normalizing. */
    private static Double maxIC;

    /** Pre-computed bound on -logP(t|parents(t)), for each node t (i.e. t.cond_inf). */
    private static Map<OntologyTerm, Double> parentCondIC;

    /** Provides access to the term ontology. */
    private static OntologyManager ontologyManager;

    /** The exomizer manager object to handle genetic comparisons. */
    private static ExternalToolJobManager<Genotype> exomizerManager;

    /** Logging helper object. */
    private final Logger logger = LoggerFactory.getLogger(MutualInformationPatientSimilarityView.class);

    /** Memoized match score. */
    private Double score;

    /** Memoized genotype match, retrieved through getGenotypeSimilarity. */
    private GenotypeSimilarityView matchedGenes;

    /**
     * Simple constructor passing both {@link #match the patient} and the {@link #reference reference patient}.
     * 
     * @param match the matched patient to represent, must not be {@code null}
     * @param reference the reference patient against which to compare, must not be {@code null}
     * @param access the access level the current user has on the matched patient
     * @throws IllegalArgumentException if one of the patients is {@code null}
     * @throws NullPointerException if the class was not statically initialized with
     *             {@link #initializeStaticData(Map, Map, OntologyManager)} before use
     */
    public MutualInformationPatientSimilarityView(Patient match, Patient reference, AccessType access)
        throws IllegalArgumentException
    {
        super(match, reference, access);
        if (MutualInformationPatientSimilarityView.termICs == null
            || MutualInformationPatientSimilarityView.parentCondIC == null
            || MutualInformationPatientSimilarityView.ontologyManager == null
            || MutualInformationPatientSimilarityView.exomizerManager == null) {
            String error =
                "Static data of MutualInformationPatientSimilarityView was not initilized before instantiation";
            this.logger.error(error);
            throw new NullPointerException(error);
        }
    }

    /**
     * Constructor that copies the data from another patient pair.
     * 
     * @param openView the open patient pair to clone
     */
    public MutualInformationPatientSimilarityView(AbstractPatientSimilarityView openView)
    {
        this(openView.match, openView.reference, openView.access);
    }

    /**
     * Set the static information for the class. Must be run before creating instances of this class.
     * 
     * @param termICs the information content of each term
     * @param condICs the conditional information content of each term, given its parents
     * @param ontologyManager the ontology manager
     * @param exomizerManager the exomizer job manager
     */
    public static void initializeStaticData(Map<OntologyTerm, Double> termICs, Map<OntologyTerm, Double> condICs,
        OntologyManager ontologyManager, ExternalToolJobManager<Genotype> exomizerManager)
    {
        MutualInformationPatientSimilarityView.ontologyManager = ontologyManager;
        MutualInformationPatientSimilarityView.parentCondIC = condICs;
        MutualInformationPatientSimilarityView.termICs = termICs;
        MutualInformationPatientSimilarityView.maxIC = Collections.max(termICs.values());
        MutualInformationPatientSimilarityView.exomizerManager = exomizerManager;
    }

    @Override
    protected void matchFeatures()
    {
        // Features aren't directly matched to each other in this Patient similarity implementation.
        this.matchedFeatures = null;
    }

    @Override
    protected JSONArray getFeaturesJSON()
    {
        // Just return a simple array of the features in the match patient
        JSONArray featuresJSON = new JSONArray();
        if (this.access.isOpenAccess()) {
            for (Feature f : this.match.getFeatures()) {
                featuresJSON.add(f.toJSON());
            }
        }
        return featuresJSON;
    }

    /**
     * Return a (potentially empty) collection of terms present in the patient.
     * 
     * @param patient
     * @return a collection of terms present in the patient
     */
    private Collection<OntologyTerm> getPresentPatientTerms(Patient patient)
    {
        Set<OntologyTerm> terms = new HashSet<OntologyTerm>();
        for (Feature feature : patient.getFeatures()) {
            if (!feature.isPresent()) {
                continue;
            }

            OntologyTerm term = ontologyManager.resolveTerm(feature.getId());
            if (term == null) {
                this.logger.error("Error resolving term: " + feature.getId() + " " + feature.getName());
            } else {
                terms.add(term);
            }
        }
        return terms;
    }

    /**
     * Return the set of terms implied by a collection of features in the ontology.
     * 
     * @param terms a collection of terms
     * @return all provided OntologyTerm terms and their ancestors
     */
    private Set<OntologyTerm> getAncestors(Collection<OntologyTerm> terms)
    {
        Set<OntologyTerm> ancestors = new HashSet<OntologyTerm>(terms);
        for (OntologyTerm term : terms) {
            // Add all ancestors
            ancestors.addAll(term.getAncestorsAndSelf());
        }
        return ancestors;
    }

    /**
     * Return the set of terms that occur in the patient's features (and their ancestors).
     * 
     * @param patient
     * @return all OntologyTerms that are implied by the patient's phenotypes
     */
    private Set<OntologyTerm> getAncestors(Patient patient)
    {
        return getAncestors(getPresentPatientTerms(patient));
    }

    /**
     * Return the cost of encoding all the terms (and their ancestors) in a tree together.
     * 
     * @param all terms (and their ancestors) that are present in the patient
     * @return the cost of the encoding all the terms
     */
    private double getJointTermsCost(Collection<OntologyTerm> ancestors)
    {
        double cost = 0;
        for (OntologyTerm term : ancestors) {
            Double ic = parentCondIC.get(term);
            if (ic == null) {
                ic = 0.0;
            }
            cost += ic;
        }
        return cost;
    }

    @Override
    public double getScore()
    {
        if (this.score == null) {
            if (this.match == null || this.reference == null) {
                this.score = Double.NaN;
            } else {
                // Get ancestors for both patients
                Set<OntologyTerm> refAncestors = getAncestors(this.reference);
                Set<OntologyTerm> matchAncestors = getAncestors(this.match);

                // Compute costs of each patient separately
                double p1Cost = getJointTermsCost(refAncestors);
                double p2Cost = getJointTermsCost(matchAncestors);

                // Score overlapping (min) ancestors
                Set<OntologyTerm> sharedAncestors = new HashSet<OntologyTerm>();
                sharedAncestors.addAll(refAncestors);
                sharedAncestors.retainAll(matchAncestors);

                double sharedCost = getJointTermsCost(sharedAncestors);
                assert (sharedCost <= p1Cost && sharedCost <= p2Cost) : "sharedCost > individiual cost";

                double harmonicMeanIC = 2 / (p1Cost / sharedCost + p2Cost / sharedCost);

                this.score = harmonicMeanIC;
            }
        }
        return this.score;
    }

    /**
     * Get the genotype similarity view for this pair of patients, lazily evaluated and memoized.
     * 
     * @return the genotype similarity view for this pair of patients
     */
    private GenotypeSimilarityView getGenotypeSimilarity()
    {
        if (this.matchedGenes == null) {
            this.matchedGenes = new RestrictedGenotypeSimilarityView(this.match, this.reference, this.access);
        }
        return this.matchedGenes;
    }

    /**
     * Construct JSON for this partial matching of terms from both patients, according to the access level.
     * 
     * @param ancestor the shared ancestor of the feature match (or null if no ancestor)
     * @param ancestorScore the score of this shared ancestor (or 0.0 if no ancestor)
     * @param refFeatures the bag of matched features in the reference
     * @param matchFeatures the bag of matched features in the match
     * @return a JSON object for the match
     */
    private JSONObject getFeatureMatchJSON(OntologyTerm ancestor, double ancestorScore,
        Collection<OntologyTerm> refFeatures, Collection<OntologyTerm> matchFeatures)
    {
        String ancestorName;
        String ancestorId;

        if (ancestor == null) {
            ancestorId = "";
            ancestorName = "Unmatched";
        } else {
            ancestorId = ancestor.getId();
            ancestorName = ancestor.getName();
        }

        // Add ancestor info
        JSONObject featureMatchJSON = new JSONObject();
        featureMatchJSON.element("score", ancestorScore);

        JSONObject sharedParentJSON = new JSONObject();
        sharedParentJSON.element("id", ancestorId);
        sharedParentJSON.element("name", ancestorName);
        featureMatchJSON.element("category", sharedParentJSON);

        // Add reference features
        JSONArray referenceJSON = new JSONArray();
        for (OntologyTerm term : refFeatures) {
            referenceJSON.add(term.getId());
        }
        featureMatchJSON.element("reference", referenceJSON);

        // Add match features
        JSONArray matchJSON = new JSONArray();
        for (OntologyTerm term : matchFeatures) {
            matchJSON.add(this.access.isOpenAccess() ? term.getId() : "");
        }
        featureMatchJSON.element("match", matchJSON);
        return featureMatchJSON;
    }

    /**
     * Find, remove, and return all terms with given ancestor.
     * 
     * @param terms the terms, modified by removing terms with given ancestor
     * @param ancestor the ancestor to search for
     * @return the terms with the given ancestor (removed from given terms)
     */
    private Collection<OntologyTerm> popTermsWithAncestor(Collection<OntologyTerm> terms, OntologyTerm ancestor)
    {
        Collection<OntologyTerm> matched = new HashSet<OntologyTerm>();
        for (OntologyTerm term : terms) {
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
     * @return the JSON representation of the best match (removes the matched terms from the passed lists) or null if
     *         the terms are not a good match (the term collections are then unchanged)
     */
    private JSONObject popBestFeatureMatch(Collection<OntologyTerm> refTerms, Collection<OntologyTerm> matchTerms)
    {
        Collection<OntologyTerm> sharedAncestors = getAncestors(refTerms);
        sharedAncestors.retainAll(getAncestors(matchTerms));

        // Find ancestor with highest (normalized) information content
        OntologyTerm ancestor = null;
        double ancestorScore = Double.NEGATIVE_INFINITY;
        for (OntologyTerm term : sharedAncestors) {
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
        Collection<OntologyTerm> refMatched = popTermsWithAncestor(refTerms, ancestor);
        Collection<OntologyTerm> matchMatched = popTermsWithAncestor(matchTerms, ancestor);

        // Return match json from matched terms
        return getFeatureMatchJSON(ancestor, ancestorScore, refMatched, matchMatched);
    }

    /**
     * Get nicely-grouped feature matching JSON for the reference and the match.
     * 
     * @return a JSON array of feature matches
     */
    private JSONArray getFeatureMatchesJSON()
    {
        // Compute nicer information-content phenotype cluster matching
        JSONArray matchesJSON = new JSONArray();
        Collection<OntologyTerm> refTerms = getPresentPatientTerms(this.reference);
        Collection<OntologyTerm> matchTerms = getPresentPatientTerms(this.match);

        // Keep removing most-related sets of terms until none match lower than HP roots
        while (!refTerms.isEmpty() && !matchTerms.isEmpty()) {
            JSONObject matched = popBestFeatureMatch(refTerms, matchTerms);
            if (matched == null) {
                break;
            }
            matchesJSON.add(matched);
        }

        // Add any unmatched terms
        if (!refTerms.isEmpty() || !matchTerms.isEmpty()) {
            JSONObject unmatched = getFeatureMatchJSON(null, 0.0, refTerms, matchTerms);
            matchesJSON.add(unmatched);
        }

        return matchesJSON;
    }

    @Override
    public JSONObject toJSON()
    {
        if (this.access.isPrivateAccess()) {
            return new JSONObject(true);
        }
        JSONObject result = new JSONObject();

        if (this.access.isOpenAccess()) {
            result.element("id", this.match.getDocument().getName());
            result.element("owner", this.match.getReporter().getName());
        }
        result.element("token", getContactToken());
        result.element("access", this.access.toString());
        result.element("myCase", Objects.equals(this.reference.getReporter(), this.match.getReporter()));
        result.element("score", getScore());
        result.element("featuresCount", this.match.getFeatures().size());

        JSONArray featuresJSON = getFeaturesJSON();
        if (!featuresJSON.isEmpty()) {
            result.element("features", featuresJSON);
        }

        JSONArray disordersJSON = getDisordersJSON();
        if (!disordersJSON.isEmpty()) {
            result.element("disorders", disordersJSON);
        }

        // Create genotype similarity view right when we need it
        GenotypeSimilarityView genes = getGenotypeSimilarity();
        JSONArray genesJSON = genes.toJSON();
        if (!genesJSON.isEmpty()) {
            result.element("genes", genesJSON);
        }

        // Compute nicer information-content phenotype cluster matching
        JSONArray matchesJSON = getFeatureMatchesJSON();
        result.element("featureMatches", matchesJSON);

        return result;
    }
}
