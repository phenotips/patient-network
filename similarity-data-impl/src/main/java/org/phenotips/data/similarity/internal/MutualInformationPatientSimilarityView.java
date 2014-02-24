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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.phenotips.data.Feature;
import org.phenotips.data.Patient;
import org.phenotips.data.similarity.AccessType;
import org.phenotips.ontology.OntologyManager;
import org.phenotips.ontology.OntologyTerm;
import org.slf4j.Logger;

/**
 * Implementation of FeatureSimilarityView that uses a mutual information metric to score similar patients.
 * 
 * @version $Id$
 * @since
 */
public class MutualInformationPatientSimilarityView extends RestrictedPatientSimilarityView
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

    /** Pre-computed bound on -logP(t|parents(t)), for each node t (i.e. t.cond_inf). */
    private static Map<OntologyTerm, Double> parentCondIC;

    /** The matched patient to represent. */
    private final Patient match;

    /** The reference patient against which to compare. */
    private final Patient reference;

    /** Provides access to the term ontology. */
    private final OntologyManager ontologyManager;

    /** Logging helper object. */
    private final Logger logger;

    /**
     * Simple constructor passing both {@link #match the patient} and the {@link #reference reference patient}.
     * 
     * @param match the matched patient to represent, must not be {@code null}
     * @param reference the reference patient against which to compare, must not be {@code null}
     * @param access the access level the current user has on the matched patient
     * @param ontologyManager the term ontology
     * @param logger logging helper object
     * @throws IllegalArgumentException if one of the patients is {@code null}
     */
    public MutualInformationPatientSimilarityView(Patient match, Patient reference, AccessType access,
        OntologyManager ontologyManager, Logger logger) throws IllegalArgumentException
    {
        super(match, reference, access);
        this.match = match;
        this.reference = reference;
        this.ontologyManager = ontologyManager;
        this.logger = logger;
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
                logger.error("Error resolving term: " + feature.getId() + " " + feature.getName());
            } else {
                terms.add(term);
            }
        }
        return terms;
    }

    /**
     * Return the set of terms that occur in the patient's features (and their ancestors).
     * 
     * @param patient
     * @return all OntologyTerms that are implied by the patient's phenotypes
     */
    private Set<OntologyTerm> getAncestors(Patient patient)
    {
        Set<OntologyTerm> ancestors = new HashSet<OntologyTerm>();
        Collection<OntologyTerm> terms = getPresentPatientTerms(patient);
        // Add directly-specified terms
        ancestors.addAll(terms);
        for (OntologyTerm term : terms) {
            // Add all ancestors
            ancestors.addAll(term.getAncestors());
        }
        return ancestors;
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

}
