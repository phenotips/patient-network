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

import org.json.JSONArray;

/**
 * View of a patient genotype as related to another reference genotype.
 *
 * @version $Id$
 * @since 1.0M6
 */
@Unstable
public interface PatientGenotypeSimilarityView extends PatientGenotype
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
     * @return the data about this value, using the json-lib classes, empty if there are no matches and null if the
     *         necessary genetic information is not available or visible
     */
    @Override
    JSONArray toJSON();
}
