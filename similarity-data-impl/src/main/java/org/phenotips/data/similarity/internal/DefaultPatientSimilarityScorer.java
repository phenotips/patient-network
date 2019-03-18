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

import org.phenotips.data.Feature;
import org.phenotips.data.Patient;
import org.phenotips.data.similarity.PatientSimilarityScorer;
import org.phenotips.vocabulary.VocabularyManager;
import org.phenotips.vocabulary.VocabularyTerm;

import org.xwiki.component.annotation.Component;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.inject.Singleton;

/**
 * For Arun.
 * @version $Id$
 */
@Component
@Singleton
public class DefaultPatientSimilarityScorer implements PatientSimilarityScorer
{
    /** Pre-computed term information content (-logp), for each node t (i.e. t.inf). */
    private static Map<VocabularyTerm, Double> termICs;

    /** Provides access to the term vocabulary. */
    private static VocabularyManager vocabularyManager;

    /**
     * Set the static information for the class. Must be run before creating instances of this class.
     *
     * @param termICs the information content of each term
     * @param vocabularyManager the vocabulary manager
     */
    public static void initializeStaticData(Map<VocabularyTerm, Double> termICs, VocabularyManager vocabularyManager)
    {
        DefaultPatientSimilarityScorer.termICs = termICs;
        DefaultPatientSimilarityScorer.vocabularyManager = vocabularyManager;
    }

    /**
     * Returns phenotype similarity score.
     * @param reference patient1 with a bag of phenotypes
     * @param match patient2 with a bag of phenotypes
     * @return phenotype similarity score
     */
    public double getScore(Patient reference, Patient match)
    {
        // Get ancestors for both patients
        Set<VocabularyTerm> refAncestors = getAncestors(getPresentPatientTerms(reference));
        Set<VocabularyTerm> matchAncestors = getAncestors(getPresentPatientTerms(match));

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
            return baseScore;
        }
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
}
