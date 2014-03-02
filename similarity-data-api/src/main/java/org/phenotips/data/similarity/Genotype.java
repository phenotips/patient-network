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

import java.util.List;
import java.util.Set;

import net.sf.json.JSONObject;

import org.apache.commons.lang3.tuple.Pair;
import org.xwiki.stability.Unstable;

/**
 * TODO
 * 
 * @version $Id$
 * @since 1.0M8
 */
@Unstable
public interface Genotype
{
    
    void addGene(String gene, double score, List<Variant> variants);
    
    Set<String> getGenes();

    /**
     * Return the score for a gene.
     * 
     * @param gene the gene in question
     * @return the score of the gene, between 0 and 1, where 1 is better
     */
    double getGeneScore(String gene);

    Pair<Double, Double> getTopVariantScores(String gene);
    
    /**
     * Retrieve all variant information in a JSON format. For example:
     * 
     * <pre>
     * [ 
     *   {
     *     "gene": "SRCAP",
     *     "score": 0.7, // phenotype score
     *     "variants": [ // variants sorted by decreasing score
     *       { 
     *         "score": 0.8, // genotype score
     *         "chrom": "1",
     *         "position": 2014819,
     *         "type": "SPLICING",
     *         "ref": "A",
     *         "alt": "T",
     *       },
     *       {...},
     *      ]
     *   }
     * ]
     * </pre>
     * 
     * @return the data about this value, using the json-lib classes
     */
    JSONObject toJSON();
}
