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

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.apache.commons.lang3.ObjectUtils;
import org.phenotips.data.Disorder;
import org.phenotips.data.Feature;
import org.phenotips.data.Patient;
import org.phenotips.data.similarity.AccessType;
import org.phenotips.data.similarity.PatientSimilarityView;
import org.phenotips.ontology.OntologyManager;
import org.phenotips.ontology.OntologyTerm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of {@link PatientSimilarityView} that uses a mutual information metric to score similar patients.
 * 
 * @version $Id$
 * @since
 */
public class MutualInformationPatientSimilarityView extends RestrictedPatientSimilarityView implements
    PatientSimilarityView
{
    /*
     * (non-Javadoc)
     * 
     * New components need to be declared in: src/main/resources/META-INF/components.txt
     * PatientSimilarityView injected in similarity-search/.../internal/SolrSimilarPatientsFinder.java, 
     *   instantiated by RestPatSimViewFactory
     * 
     * phenotips/resources/solr-configuration/src/main/resources/omim/conf/schema.xml
     */

    /** The root of the phenotypic abnormality portion of HPO. */
    private static final String HP_ROOT = "HP:0000118";
    
    /** Pre-computed term information content (-logp), for each node t (i.e. t.inf). */
    private static Map<OntologyTerm, Double> termICs;
    
    /** The largest IC found, for normalizing. */
    private static Double maxIC;
    
    /** Pre-computed bound on -logP(t|parents(t)), for each node t (i.e. t.cond_inf). */
    private static Map<OntologyTerm, Double> parentCondIC;

    /** The matched patient to represent. */
    private final Patient match;

    /** The reference patient against which to compare. */
    private final Patient reference;

    /** Provides access to the term ontology. */
    private final OntologyManager ontologyManager;

    /** Logging helper object. */
    private final Logger logger = LoggerFactory.getLogger(MutualInformationPatientSimilarityView.class);

    /**
     * Simple constructor passing both {@link #match the patient} and the {@link #reference reference patient}.
     * 
     * @param match the matched patient to represent, must not be {@code null}
     * @param reference the reference patient against which to compare, must not be {@code null}
     * @param access the access level the current user has on the matched patient
     * @param ontologyManager the term ontology
     * @throws IllegalArgumentException if one of the patients is {@code null}
     */
    public MutualInformationPatientSimilarityView(Patient match, Patient reference, AccessType access,
        OntologyManager ontologyManager) throws IllegalArgumentException
    {
        super(match, reference, access);
        this.match = match;
        this.reference = reference;
        this.ontologyManager = ontologyManager;
    }

    /**
     * Constructor that copies the data from another patient pair.
     * 
     * @param openView the open patient pair to clone
     * @param ontologyManager the term ontology
     */
    public MutualInformationPatientSimilarityView(AbstractPatientSimilarityView openView,
        OntologyManager ontologyManager)
    {
        this(openView.match, openView.reference, openView.access, ontologyManager);
    }

    /**
     * Set the static conditional information content map for the class.
     * 
     * @param condICs the conditional information content of each term, given its parents
     */
    public static void setConditionalICs(Map<OntologyTerm, Double> condICs)
    {
        MutualInformationPatientSimilarityView.parentCondIC = condICs;
    }

    /**
     * Set the static term information content map for the class.
     * 
     * @param termICs the information content of each term
     */
    public static void setTermICs(Map<OntologyTerm, Double> termICs)
    {
        MutualInformationPatientSimilarityView.termICs = termICs;
        MutualInformationPatientSimilarityView.maxIC = Collections.max(termICs.values());
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

            OntologyTerm term = this.ontologyManager.resolveTerm(feature.getId());
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
    private Set<OntologyTerm> getAncestors(Collection<OntologyTerm> terms) {
        Set<OntologyTerm> ancestors = new HashSet<OntologyTerm>(terms);
        for (OntologyTerm term : terms) {
            // Add all ancestors
            ancestors.addAll(term.getAncestors());
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
        if (match == null || reference == null) {
            return Double.NaN;
        }

        // Get ancestors for both patients
        Set<OntologyTerm> refAncestors = getAncestors(reference);
        Set<OntologyTerm> matchAncestors = getAncestors(match);

        // Compute costs of each patient separately
        double p1Cost = getJointTermsCost(refAncestors);
        double p2Cost = getJointTermsCost(matchAncestors);

        // Score overlapping (min) ancestors
        Set<OntologyTerm> sharedAncestors = refAncestors;
        // warning: Line below destructively modifies refAncestors
        sharedAncestors.retainAll(matchAncestors);

        double sharedCost = getJointTermsCost(sharedAncestors);
        assert (sharedCost <= p1Cost && sharedCost <= p2Cost) : "sharedCost > individiual cost";

        double harmonicMeanIC = 2 / (p1Cost / sharedCost + p2Cost / sharedCost);

        return harmonicMeanIC;
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
        result.element("myCase", ObjectUtils.equals(this.reference.getReporter(), this.match.getReporter()));
        result.element("score", getScore());
        result.element("featuresCount", this.match.getFeatures().size());

        Set<? extends Feature> features = getFeatures();
        if (!features.isEmpty()) {
            JSONArray featuresJSON = new JSONArray();
            for (Feature feature : features) {
                featuresJSON.add(feature.toJSON());
            }
            result.element("features", featuresJSON);
        }

        Set<? extends Disorder> disorders = getDisorders();
        if (!disorders.isEmpty()) {
            JSONArray disordersJSON = new JSONArray();
            for (Disorder disorder : disorders) {
                disordersJSON.add(disorder.toJSON());
            }
            result.element("disorders", disordersJSON);
        }

        JSONArray matchesJSON = new JSONArray();
        // Compute nicer information-content phenotype cluster matching
        Collection<OntologyTerm> refTerms = getPresentPatientTerms(this.reference);
        Collection<OntologyTerm> matchTerms = getPresentPatientTerms(this.match);
        
        while (!refTerms.isEmpty() && !matchTerms.isEmpty()) {
            Collection<OntologyTerm> sharedAncestors = getAncestors(refTerms);
            sharedAncestors.retainAll(getAncestors(matchTerms));
            
            // Find ancestor with highest information content
            OntologyTerm ancestor = null;
            double ancestorScore = Double.MIN_VALUE;
            for (OntologyTerm term : sharedAncestors) {
                Double termIC = MutualInformationPatientSimilarityView.termICs.get(term);
                if (termIC == null) {
                    termIC = 0.0;
                }
                termIC /= MutualInformationPatientSimilarityView.maxIC;
                
                if (termIC > ancestorScore) {
                    ancestorScore = termIC;
                    ancestor = term;
                }
            }
            
            // Find all ref and match terms under the selected ancestor
            Collection<OntologyTerm> refMatched = new TreeSet<OntologyTerm>();
            for (OntologyTerm term : refTerms) {
                if (term.getAncestorsAndSelf().contains(ancestor)) {
                    refMatched.add(term);
                }
            }
            Collection<OntologyTerm> matchMatched = new TreeSet<OntologyTerm>();
            for (OntologyTerm term : matchTerms) {
                if (term.getAncestorsAndSelf().contains(ancestor)) {
                    matchMatched.add(term);
                }
            }
            
            // Get remaining, unaccounted for terms
            refTerms.removeAll(refMatched);
            matchTerms.removeAll(matchMatched);

            // If shared ancestor is root, use a special name.
            String ancestorName = ancestor.getName();
            if (HP_ROOT.equals(ancestor.getId())) {
                ancestorName = "unmatched";
            }
            
            // Construct JSON for this partial matching of terms
            JSONObject match = new JSONObject();
            match.element("score", ancestorScore);
            
            getAncestors(refTerms);
            JSONObject sharedParentJSON = new JSONObject();
            sharedParentJSON.element("id", ancestor.getId());
            sharedParentJSON.element("name", ancestorName);
            match.element("category", sharedParentJSON);
            
            JSONArray referenceJSON = new JSONArray();
            for (OntologyTerm term : refMatched) {
                referenceJSON.add(term.getId());
            }
            match.element("reference", referenceJSON);
            
            JSONArray matchJSON = new JSONArray();
            for (OntologyTerm term : matchMatched) {
                matchJSON.add(term.getId());
            }
            match.element("match", matchJSON);
        }
        result.element("featureMatches", matchesJSON);
        
        return result;
    }

}
