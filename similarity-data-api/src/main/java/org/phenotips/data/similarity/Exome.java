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

import java.util.List;
import java.util.Set;

import net.sf.json.JSONArray;

/**
 * A basic representation of a exome of a patient, with scored {@link Variant} objects in scored genes.
 *
 * @version $Id$
 * @since 1.0M6
 */
@Unstable
public interface Exome
{
    /**
     * Get the names of all genes with variants in the patient.
     *
     * @return an unmodifiable set of gene names with variants in the patient
     */
    Set<String> getGenes();

    /**
     * Return the score for a gene.
     *
     * @param gene the gene in question
     * @return the score of the gene, between 0 and 1, where 1 is better (or {@code null} if no variants for gene)
     */
    Double getGeneScore(String gene);

    /**
     * Get {@link Variant}s for a gene.
     *
     * @param gene the gene to get {@link Variant}s for.
     * @return an unmodifiable (potentially-empty) list of top {@link Variant}s for the gene, by decreasing score
     */
    List<Variant> getTopVariants(String gene);

    /**
     * Get the n highest genes, in descending order of score. If there are fewer than n genes, all will be returned.
     *
     * @param n the number of genes to return (specify 0 for all)
     * @return an unmodifiable (potentially-empty) list of gene names
     */
    List<String> getTopGenes(int n);

    /**
     * Retrieve all variant information in a JSON format. For example:
     *
     * <pre>
     *   [
     *     {
     *       "gene": "SRCAP",
     *       "score": 0.7, // phenotype score
     *       "variants": [ // variants sorted by decreasing score
     *         {
     *           "score": 0.8, // genotype score
     *           "chrom": "1",
     *           "position": 2014819,
     *           "type": "SPLICING",
     *           "ref": "A",
     *           "alt": "T",
     *         },
     *         {...},
     *        ]
     *     }
     *   ]
     * </pre>
     *
     * @return the data about this value, using the json-lib classes
     */
    JSONArray toJSON();
}
