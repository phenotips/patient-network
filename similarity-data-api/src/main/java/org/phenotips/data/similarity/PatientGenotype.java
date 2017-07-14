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

import org.xwiki.stability.Unstable;

import java.util.Set;

/**
 * This class represents the combined genetics (manually entered genes and exome sequence data) for a Patient.
 *
 * @version $Id$
 * @since 1.0M6
 */
@Unstable
public interface PatientGenotype extends Exome
{
    /**
     * Return whether the patient has any genotype data available (manually entered genes or exome data).
     *
     * @return true iff the patient has any manually entered genes or exome data
     */
    boolean hasGenotypeData();

    /**
     * Return a set of the names of either candidate or solved genes listed for the patient.
     *
     * @return a (potentially-empty) set of the names of either candidate or solved genes
     */
    Set<String> getCandidateGenes();

    /**
     * Get the genes likely mutated in the patient, both from manually entered genes and exome data.
     *
     * @return a (potentially-empty) set of gene names
     * @see org.phenotips.data.similarity.Exome#getGenes()
     */
    @Override
    Set<String> getGenes();

    /**
     * Return a status of genes (solved or candidate) stored in class.
     *
     * @return a status of genes
     */
    String getGenesStatus();

    /**
     * @return true if patient has exome data.
     */
    boolean hasExomeData();
}
