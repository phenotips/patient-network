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

import org.xwiki.stability.Unstable;

/**
 * This class allows access to an {@link #PatientGenotype} object for a given {@link #Patient}.
 *
 * @version $Id$
 * @since 1.0M6
 */
@Unstable
public interface PatientGenotypeManager
{
    /**
     * Get the (potentially-cached) {@link #PatientGenotype} for the {@link #Patient} with the given id.
     *
     * @param p the {@link #Patient} for which the {@link #PatientGenotype} will be retrieved
     * @return the corresponding {@link #PatientGenotype}, or null if no genotype available
     */
    PatientGenotype getGenotype(Patient p);
}
