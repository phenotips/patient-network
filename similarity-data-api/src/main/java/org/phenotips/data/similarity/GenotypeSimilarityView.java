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

import net.sf.json.JSONArray;

/**
 * View of a patient genotype as related to another reference genotype.
 * 
 * @version $Id$
 * @since
 */
@Unstable
public interface GenotypeSimilarityView extends Genotype
{
    /**
     * How similar is this genotype to the reference.
     * 
     * @return a similarity score, between {@code 0} for no related genes and {@code 1} for a perfect candidate.
     */
    double getScore();

    /**
     * Retrieve all genotype match information in a JSON format. For example:
     * 
     * <pre>
     *      [
     *         {
     *            "gene" : <gene name>,
     *            "reference" : {
     *                "score" : <number>,
     *                "variants" : [
     *                    {
     *                        "chrom" : "1"|"2"|...|"X"|"Y"|"MT",
     *                        "position" : <number>,
     *                        "ref" : "A"|"CT"|"-",
     *                        "alt" : "G"|"AAG"|"-",
     *                        "type" : <mutation type>,
     *                        "score" : <number>
     *                    },
     *                    ...
     *                ]
     *            }
     *            "match" : {
     *                "score" : <number>,
     *                "variants" : [
     *                    {
     *                        "chrom" : "1"|"2"|...|"X"|"Y"|"MT",
     *                        "position" : <number>,
     *                        "ref" : "A"|"CT"|"-",
     *                        "alt" : "G"|"AAAAAAAAAAAAA"|"-",
     *                        "type" : <mutation type>,
     *                        "score" : <number>
     *                    },
     *                    ...
     *                ]
     *            }
     *        },
     *        ...
     *     ]
     * </pre>
     * 
     * @return the data about this value, using the json-lib classes, or null if no information is visible
     */
    JSONArray toJSON();
}
