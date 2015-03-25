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
package org.phenotips.data.similarity;

import org.xwiki.stability.Unstable;

import java.util.Collection;
import java.util.Set;

/**
 * This class represents the combined genetics (candidate genes and exome sequence data) for a Patient.
 *
 * @version $Id$
 * @since 1.0M6
 */
@Unstable
public interface PatientGenotype extends Exome
{
    /**
     * Return whether the patient has any genotype data available (candidate genes or exome data).
     *
     * @return true iff the patient has any candidate genes or exome data
     */
    boolean hasGenotypeData();

    /**
     * Return a collection of the names of candidate genes listed for the patient.
     *
     * @return a (potentially-empty) unmodifiable collection of the names of candidate genes
     */
    Collection<String> getCandidateGenes();

    /**
     * Get the genes likely mutated in the patient, both from candidate genes and exome data.
     *
     * @return a collection of gene names
     */
    @Override
    Set<String> getGenes();
}
