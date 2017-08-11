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
 * View of the relationship between a collection of features in each of two patients.
 *
 * @version $Id$
 * @since 1.2
 */
@Unstable
public interface PatientPhenotypeSimilarityView
{
    /**
     * Retrieve all information about the cluster of features. For example:
     *
     * <pre>
     * {
     *   "id": "HP:0100247",
     *   "name": "Recurrent singultus",
     *   "type": "ancestor",
     *   "reference": [
     *     "HP:0001201",
     *     "HP:0000100"
     *   ],
     *   "match": [
     *     "HP:0001201"
     *   ]
     * }
     * </pre>
     *
     * @return the feature similarity data, using the org.json classes
     */
    JSONArray toJSON();
}
