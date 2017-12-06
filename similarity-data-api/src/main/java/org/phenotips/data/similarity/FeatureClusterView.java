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
import org.phenotips.vocabulary.VocabularyTerm;

import org.xwiki.stability.Unstable;

import java.util.Collection;

import org.json.JSONObject;

/**
 * View of the relationship between a collection of features in each of two patients within one cluster of features.
 *
 * @version $Id$
 * @since 1.0M1
 */
@Unstable
public interface FeatureClusterView
{
    /**
     * Returns the reference features, if any.
     *
     * @return a potentially empty, unmodifiable collection of the features from the reference patient
     */
    Collection<Feature> getReference();

    /**
     * Returns the features matched by this feature, if any.
     *
     * @return a potentially empty, unmodifiable collection of the features from the reference patient, with
     *         {@code null} values corresponding to undisclosed features
     */
    Collection<Feature> getMatch();

    /**
     * Returns the root/ancestor of the cluster.
     *
     * @return the root feature for the reference and match features
     */
    VocabularyTerm getRoot();

    /**
     * Returns the root/ancestor id of the cluster.
     *
     * @return the root feature for the reference and match features
     */
    String getId();

    /**
     * Returns the root/ancestor name of the cluster.
     *
     * @return the root feature for the reference and match features
     */
    String getName();

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
     * @return the feature data, using the org.json classes
     */
    JSONObject toJSON();
}
