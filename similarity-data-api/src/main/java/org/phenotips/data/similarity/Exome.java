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

import java.util.List;
import java.util.Set;

import net.sf.json.JSONArray;

/**
 * A basic representation of a exome of a patient, with scored {@link #Variant} objects in scored genes.
 *
 * @version $Id$
 * @since 1.0M8
 */
@Unstable
public interface Exome
{
    /**
     * Get the names of all genes with variants in the patient.
     *
     * @return a set of gene names with variants in the patient
     */
    Set<String> getGenes();

    /**
     * Return the score for a gene.
     *
     * @param gene the gene in question
     * @return the score of the gene, between 0 and 1, where 1 is better (or null if no variants for gene)
     */
    Double getGeneScore(String gene);

    /**
     * Get the kth highest scoring {@link #Variant} in the given gene.
     *
     * @param gene the gene for which a {@link #Variant} will be returned
     * @param k the ranked position of the {@link #Variant} to get (0 is the 1st, 1 is the 2nd, ..., etc)
     * @return get the kth highest scoring {@link #Variant} (or null)
     */
    Variant getTopVariant(String gene, int k);

    /**
     * Get top {@link #Variant}s for a gene.
     *
     * @param gene the gene to get {@link #Variant}s for.
     * @return a (potentially-empty) list of top {@link #Variant}s for the gene, by decreasing score
     */
    List<Variant> getTopVariants(String gene);
    
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
