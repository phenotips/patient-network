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

import org.phenotips.data.Feature;

import org.xwiki.component.annotation.Role;

import java.util.Set;

/**
 * @version $Id$
 * @since 1.1
 */

@Role
public interface PatientPhenotypeSimilarityViewFactory
{
    /**
     * Create an instance of the {@link PatientPhenotypeSimilarityView} for this
     * {@link PatientPhenotypeSimilarityViewFactory}.
     * This can be overridden to have the same {@link PatientPhenotypeSimilarityViewFactory} functionality with a
     * different {@link PatientPhenotypeSimilarityView} implementation.
     *
     * @param matchFeatures the features in the matched patient, can be empty
     * @param refFeatures the features in the reference patient, can be empty
     * @param access the access level of the match
     * @return a {@link PatientPhenotypeSimilarityView} for the patients
     */
    PatientPhenotypeSimilarityView createPatientPhenotypeSimilarityView(Set<? extends Feature> matchFeatures,
        Set<? extends Feature> refFeatures, AccessType access);
}
