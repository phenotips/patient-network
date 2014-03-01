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

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.phenotips.data.Feature;
import org.phenotips.data.similarity.FeatureSimilarityScorer;
import org.phenotips.ontology.OntologyManager;
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
@Named("mi")
public class MutualInformationFeatureSimilarityScorer implements FeatureSimilarityScorer, Initializable
{

    /** Pre-computed term information content (-logp), for each node t (i.e. t.inf). */
    private static Map<OntologyTerm, Double> termICs;

    /** The maximum information content score of any term. */
    private static Double maxIC;

    /** Provides access to the term ontology. */
    @Inject
    private OntologyManager ontologyManager;

    @Override
    public void initialize() throws InitializationException
    {
        // Nothing needs to be done.
    }

    /**
     * Set the static term information content used the class. Must be called before class is functional.
     * 
     * @param termICs the information content of each term.
     */
    public static void setTermICs(Map<OntologyTerm, Double> termICs)
    {
        MutualInformationFeatureSimilarityScorer.termICs = termICs;
        maxIC = Collections.max(termICs.values());
    }

    @Override
    public double getScore(Feature match, Feature reference)
    {
        if (match == null || reference == null || termICs == null) {
            return Double.NaN;
        }
        return getRelevance(match.getId(), reference.getId());
    }

    /**
     * Get the largest IC from a set of terms.
     * 
     * @param terms the terms to get the IC of
     * @return the largest IC for any of the terms
     */
    private double getMaxIC(Collection<OntologyTerm> terms) {
        double bestIC = 0.0;
        for (OntologyTerm term : terms) {
            Double termIC = termICs.get(term);
            if (termIC != null && termIC >= bestIC) {
                bestIC = termIC;
            }
        }
        return bestIC;
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
        if (matchId == null || referenceId == null || termICs == null || maxIC == null) {
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
        double bestInformationContent = getMaxIC(commonAncestors);
        return bestInformationContent / MutualInformationFeatureSimilarityScorer.maxIC;
    }

}
