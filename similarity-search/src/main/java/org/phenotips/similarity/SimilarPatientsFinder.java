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
package org.phenotips.similarity;

import org.phenotips.data.Patient;
import org.phenotips.data.similarity.PatientSimilarityView;

import org.xwiki.component.annotation.Role;
import org.xwiki.stability.Unstable;

import java.util.List;

/**
 * Allows searching for patients similar to a reference patient in the current PhenoTips instance.
 *
 * @version $Id$
 * @since 1.0M1
 */
@Unstable
@Role
public interface SimilarPatientsFinder
{
    /**
     * Returns a list of local patients similar to the reference patient. Only matchable patients are returned.
     * May only return a subsset of all matches (e.g. top N matches based on internally computed match score).
     *
     * Only returns patients with the requested consent granted (e.g. a "remotely matchable" consent).
     *
     * @param referencePatient the reference patient, must not be {@code null}.
     * @param requiredConsentId a (possibly {@code null}) id of a consent which should be granted to a
     *                          patient for it to be considered as a match
     * @return a list of similar patients found in the database, an empty list if no patients are found or
     *         if the reference patient is invalid
     */
    List<PatientSimilarityView> findSimilarPatients(Patient referencePatient, String requiredConsentId);

    /**
     * A convenience method similar to {@link #findSimilarPatients(Patient)} above,
     * for the case when no consents are requied.
     *
     * @param referencePatient the reference patient, must not be {@code null}
     * @return a (possibly empty) list of similar patients found in the database
     */
    List<PatientSimilarityView> findSimilarPatients(Patient referencePatient);

    /**
     * Returns a list of template patients similar to a reference patient.
     *
     * @param referencePatient the reference patient, must not be {@code null}
     * @return the similar patient templates found in the database, an empty list if no templates are found or if the
     *         reference patient is invalid
     */
    List<PatientSimilarityView> findSimilarPrototypes(Patient referencePatient);
}
