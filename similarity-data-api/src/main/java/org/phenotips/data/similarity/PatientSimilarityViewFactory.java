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
package org.phenotips.data.similarity;

import org.phenotips.data.Patient;

import org.xwiki.component.annotation.Role;

/**
 * Creates a custom view of the similarities between two patients, a reference patients and a patient matching the
 * reference patient's phenotypic profile. The resulting object is an extended version of the {@link Patient base
 * patient API}, which may block access to certain restricted information, and may extend data with similarity
 * information. For example, a feature from the matched patient that matches another feature from the reference patient
 * will {@link FeatureClusterView#getReference() indicate that}, and will be able to compute a
 * {@link FeatureClusterView#getScore() similarity score}.
 *
 * @version $Id$
 * @since 1.0M1
 */
@Role
public interface PatientSimilarityViewFactory
{
    /**
     * Instantiates a {@link PatientSimilarityView} specific to this factory, linking the two patients.
     *
     * @param match the matched patient whose data will be exposed
     * @param reference the patient used as the reference against which to compare
     * @return the extended patient
     * @throws IllegalArgumentException if one of the patients is {@code null}
     */
    PatientSimilarityView makeSimilarPatient(Patient match, Patient reference) throws IllegalArgumentException;

    /**
     * Converts a different type of {@link PatientSimilarityView} to the type managed by this factory. Useful for
     * converting between restricted and open patient similarity views.
     *
     * @param patientPair the patient similarity view to convert
     * @return a clone of the passed patient pair, using this factory's type of view
     */
    PatientSimilarityView convert(PatientSimilarityView patientPair);
}
