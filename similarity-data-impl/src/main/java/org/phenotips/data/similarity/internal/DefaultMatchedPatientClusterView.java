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
package org.phenotips.data.similarity.internal;

import org.phenotips.data.Patient;
import org.phenotips.data.similarity.MatchedPatientClusterView;
import org.phenotips.data.similarity.PatientSimilarityView;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.Validate;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Implementation of {@link MatchedPatientClusterView} that stores the provided reference patient and its matches.
 *
 * @version $Id$
 * @since 1.1
 */
public class DefaultMatchedPatientClusterView implements MatchedPatientClusterView
{
    private static final String QUERY_LABEL = "query";

    private static final String TOTAL_SIZE_LABEL = "resultsCount";

    private static final String RESULTS_LABEL = "results";

    private static final String RETURNED_SIZE_LABEL = "returnedCount";

    private static final String OFFSET_LABEL = "offset";

    /** @see #getReference() */
    private final Patient reference;

    /** @see #getMatches() */
    private final List<? extends PatientSimilarityView> matches;

    /**
     * Default constructor that takes in a reference {@code patient}, and its {@code matches}.
     *
     * @param patient the reference {@link Patient} object
     * @param matches a list of {@link PatientSimilarityView} objects representing patients matching {@code patient}
     */
    public DefaultMatchedPatientClusterView(
        @Nonnull final Patient patient,
        @Nullable final List<? extends PatientSimilarityView> matches)
    {
        Validate.notNull(patient, "The reference patient should not be null.");
        this.reference = patient;
        this.matches = CollectionUtils.isNotEmpty(matches) ? matches : Collections.<PatientSimilarityView>emptyList();

        // Sort by score
        Collections.sort(this.matches, new Comparator<PatientSimilarityView>()
        {
            @Override
            public int compare(PatientSimilarityView o1, PatientSimilarityView o2)
            {
                double score1 = o1.getScore();
                double score2 = o2.getScore();
                return (int) Math.signum(score2 - score1);
            }
        });
    }

    @Override
    public Patient getReference()
    {
        return this.reference;
    }

    @Override
    public List<PatientSimilarityView> getMatches()
    {
        return Collections.unmodifiableList(this.matches);
    }

    @Override
    public int size()
    {
        return this.matches.size();
    }

    @Override
    public JSONObject toJSON()
    {
        return toJSON(0, size());
    }

    @Override
    public JSONObject toJSON(final int fromIndex, final int maxResults)
        throws IndexOutOfBoundsException
    {
        if (fromIndex < 0) {
            throw new IndexOutOfBoundsException("fromIndex(" + fromIndex + ") < 0");
        }
        // always allow fromIndex of 0, otherwise do not allow index greater than the last element
        if (fromIndex > 0 && fromIndex >= size()) {
            throw new IndexOutOfBoundsException("fromIndex(" + fromIndex + ") > numMatches(" + size() + ")");
        }
        final JSONObject referenceJson = this.reference.toJSON();
        final JSONArray matchesJson = buildMatchesJSONArray(fromIndex, maxResults);
        return new JSONObject()
            .put(QUERY_LABEL, referenceJson)
            .put(TOTAL_SIZE_LABEL, size())
            .put(RESULTS_LABEL, matchesJson)
            .put(OFFSET_LABEL, fromIndex + 1)
            .put(RETURNED_SIZE_LABEL, matchesJson.length());
    }

    /**
     * Builds a JSON representation of {@link #getMatches()}.
     *
     * @param fromIndex the starting index for match to convert (inclusive, zero-based)
     * @param toIndex the last index for match to convert (inclusive, zero-based)
     * @return a {@link JSONArray} of {@link #getMatches()}
     */
    private JSONArray buildMatchesJSONArray(final int fromIndex, final int maxResults)
    {
        final JSONArray matchesJson = new JSONArray();

        final int toIndex = (maxResults < 0) ? size() : Math.min(size(), fromIndex + maxResults);

        for (int i = fromIndex; i < toIndex; i++) {
            final JSONObject matchJson = this.matches.get(i).toJSON();
            matchesJson.put(matchJson);
        }
        return matchesJson;
    }

    @Override
    public boolean equals(@Nullable final Object o)
    {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultMatchedPatientClusterView that = (DefaultMatchedPatientClusterView) o;
        return Objects.equals(this.reference, that.reference)
            && Objects.equals(this.matches, that.matches);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(this.reference, this.matches);
    }
}
