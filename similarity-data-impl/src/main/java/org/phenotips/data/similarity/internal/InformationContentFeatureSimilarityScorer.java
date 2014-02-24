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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.phenotips.data.Feature;
import org.phenotips.data.similarity.FeatureSimilarityScorer;
import org.phenotips.ontology.OntologyManager;
import org.phenotips.ontology.OntologyService;
import org.phenotips.ontology.OntologyTerm;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.phase.Initializable;
import org.xwiki.component.phase.InitializationException;

/**
 * {@link FeatureSimilarityScorer Similarity scorer} for features, rewarding features that are closely related.
 * 
 * @version $Id$
 * @since 1.0M8
 */
@Component
@Singleton
@Named("ic")
public class InformationContentFeatureSimilarityScorer implements FeatureSimilarityScorer, Initializable
{
    /** Provides access to the term ontology. */
    @Inject
    private OntologyManager ontologyManager;

    /** Provides access to the OMIM/HPO mapping, which helps compute the information content of a term (IC). */
    private OntologyService icSource;

    /** Denominator for computing the frequency of a term in the OMIM/HPO mapping. */
    private long frequencyDenominator;

    /**
     * The maximum achievable information content value, corresponding to a symptom that appears in only one disorder.
     */
    private double maximumIC;

    @Override
    public void initialize() throws InitializationException
    {
        this.icSource = this.ontologyManager.getOntology("MIM");
        this.frequencyDenominator = this.icSource.size();
        this.maximumIC = -Math.log(1.0 / this.frequencyDenominator);
    }

    @Override
    public double getScore(Feature match, Feature reference)
    {
        if (match == null || reference == null) {
            return Double.NaN;
        }
        return getRelevance(match.getId(), reference.getId());
    }

    /**
     * Compute the relevance of matching a pair of terms, which is a number between 0 and 1.
     * 
     * @param matchId The identifier of the query term
     * @param referenceId The identifier of the reference term
     * @return The information content (IC) of the most informative common ancestor of the two terms normalized (divided
     *         by the maximum achievable IC value)
     * @see #getIC(OntologyTerm)
     * @see #maximumIC
     */
    private double getRelevance(String matchId, String referenceId)
    {
        if (matchId == null || referenceId == null) {
            return Double.NaN;
        }
        OntologyTerm matchTerm = this.ontologyManager.resolveTerm(matchId);
        OntologyTerm referenceTerm = this.ontologyManager.resolveTerm(referenceId);

        if (matchTerm == null || referenceTerm == null) {
            return Double.NaN;
        }
        // Get common ancestors (lowest only)
        // TODO: implement more efficiently!
        Set<OntologyTerm> commonAncestors = new HashSet<OntologyTerm>();
        Set<OntologyTerm> redundantAncestors = new HashSet<OntologyTerm>();

        commonAncestors.addAll(matchTerm.getAncestors());
        commonAncestors.retainAll(referenceTerm.getAncestors());
        for (OntologyTerm ancestor : commonAncestors) {
            redundantAncestors.addAll(ancestor.getParents());
        }
        commonAncestors.removeAll(redundantAncestors);

        // Find the most informative one
        double bestInformationContent = 0;
        for (OntologyTerm ancestor : commonAncestors) {
            double informationContent = this.getIC(ancestor);
            if (informationContent >= bestInformationContent) {
                bestInformationContent = informationContent;
            }
        }
        return bestInformationContent / this.maximumIC;
    }

    /**
     * Compute the information content (IC) of an HPO term, based on its mappings to OMIM disorders.
     * 
     * @param term The ontology term
     * @return The negative log of the number of disorders which have it as a symptom (implicit or explicit) divided by
     *         the total number of annotated disorders
     */
    private double getIC(final OntologyTerm term)
    {
        double frequencyNumerator = this.icSource.count(new HashMap<String, String>()
        {
            private static final long serialVersionUID = 1L;
            {
                put("symptom", term.getId());
            }
        });
        if (frequencyNumerator == 0) {
            return 0;
        }
        return -Math.log(frequencyNumerator / this.frequencyDenominator);
    }
}
