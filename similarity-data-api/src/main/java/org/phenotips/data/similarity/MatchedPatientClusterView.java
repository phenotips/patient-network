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

import org.phenotips.data.Patient;

import org.xwiki.stability.Unstable;

import java.util.List;

import org.json.JSONObject;

/**
 * View of the reference patient and its collection of possible matches.
 *
 * @version $Id$
 * @since 1.2
 */
@Unstable
public interface MatchedPatientClusterView
{
    /**
     * The reference patient against which we're comparing.
     *
     * @return the patient for which we're getting {@link #getMatches() similar cases}
     */
    Patient getReference();

    /**
     * Gets the list of {@link PatientSimilarityView} objects that contain data for {@link Patient} objects matching
     * {@link #getReference() the reference patient}.
     *
     * @return a list of {@link PatientSimilarityView} for {@link #getReference()}
     */
    List<PatientSimilarityView> getMatches();

    /**
     * The number of {@link #getMatches() matches} found.
     *
     * @return the total number of matches
     */
    int size();

    /**
     * Returns the JSON representation of the {@link #getReference() reference patient} and its {@link #getMatches()
     * matches}.
     *
     * @return a {@link JSONObject} containing the data from the {@link #getReference() reference patient} and its
     *         {@link #getMatches() matches}
     */
    JSONObject toJSON();

    /**
     * Returns the JSON representation of the {@link #getReference() reference patient} and its {@link #getMatches()
     * matches}, starting from {@code fromIndex}, inclusive, and up to {@code toIndex}, inclusive. Will throw an
     * {@link IndexOutOfBoundsException} if one or both indices are out of bounds, and {@link IllegalArgumentException}
     * if {@code fromIndex > toIndex}.
     *
     * @param fromIndex the starting position
     * @param toIndex the end position (inclusive)
     * @return a {@link JSONObject} containing the requested subset of data from the {@link #getReference() reference
     *         patient} and its {@link #getMatches() matches}
     * @throws IndexOutOfBoundsException if (<tt>fromIndex &lt; 0 || toIndex &gt;= {@link #size()}
     * @throws IllegalArgumentException if fromIndex &gt; toIndex</tt>)
     */
    JSONObject toJSON(int fromIndex, int toIndex) throws IndexOutOfBoundsException, IllegalArgumentException;
}
