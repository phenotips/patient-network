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
package org.phenotips.data.similarity;

import org.phenotips.data.Patient;
import org.phenotips.data.permissions.AccessLevel;

import org.xwiki.stability.Unstable;

import java.util.Set;

import org.json.JSONArray;

/**
 * View of a patient as related to another reference patient.
 *
 * @version $Id$
 * @since 1.0M1
 */
@Unstable
public interface PatientSimilarityView extends Patient
{
    /**
     * The reference patient against which we're comparing.
     *
     * @return the patient for which we're searching similar cases
     */
    Patient getReference();

    /**
     * What type of access does the user have to this patient profile.
     *
     * @return an {@link AccessLevel} value
     */
    AccessLevel getAccess();

    /**
     * How similar is this patient to the reference.
     *
     * @return a similarity score, between {@code -1} for opposite patient descriptions and {@code 1} for an exact
     *         match, with {@code 0} for patients with no similarities
     */
    double getScore();

    /**
     * Get the phenotypic similarity score for this patient match.
     *
     * @return the similarity score, between 0 (a poor match) and 1 (a good match)
     */
    double getPhenotypeScore();

    /**
     * Get the genotypic similarity score for this patient match.
     *
     * @return the similarity score, between 0 (a poor match) and 1 (a good match)
     */
    double getGenotypeScore();

    /**
     * Get JSON for many-to-many feature matches between the reference and the match.
     *
     * @return a JSON array of feature matches, empty if none to display
     */
    JSONArray getFeatureMatchesJSON();

    /**
     * Get the genes that are candidates for the match between the two patients, based on both manually entered genes
     * and exome data of the patients.
     *
     * @return a (potentially-empty) set of gene names
     */
    Set<String> getMatchingGenes();
}
