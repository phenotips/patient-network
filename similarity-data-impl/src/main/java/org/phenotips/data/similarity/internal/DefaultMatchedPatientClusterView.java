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
import java.util.List;
import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Implementation of {@link MatchedPatientClusterView} that stores the provided reference patient and its matches.
 *
 * @version $Id$
 * @since 1.2
 */
public class DefaultMatchedPatientClusterView implements MatchedPatientClusterView
{
    private static final String QUERY_LABEL = "query";

    private static final String IS_MANAGER_LABEL = "isManager";

    private static final String TOTAL_SIZE_LABEL = "resultsCount";

    private static final String RESULTS_LABEL = "results";

    private static final String RETURNED_SIZE_LABEL = "returnedCount";

    private static final String OWNER_LABEL = "owner";

    private static final String EMAIL_LABEL = "email";

    private static final String OFFSET_LABEL = "offset";

    /** @see #getReference(). */
    private final Patient reference;

    /** @see #getMatches(). */
    private final List<PatientSimilarityView> matches;

    /** True iff the user is the manager for {@link #getReference()}. */
    private final boolean isManager;

    /**
     * Default constructor that takes in a reference {@code patient}, and its {@code matches}.
     *
     * @param patient the reference {@link Patient} object
     * @param isManager true iff the current user is the manager for {@code patient} document
     * @param matches a list of {@link PatientSimilarityView} objects representing patients matching {@code patient}
     */
    public DefaultMatchedPatientClusterView(
        @Nonnull final Patient patient,
        final boolean isManager,
        @Nullable final List<PatientSimilarityView> matches)
    {
        Validate.notNull(patient, "The reference patient should not be null.");
        this.reference = patient;
        this.matches = CollectionUtils.isNotEmpty(matches) ? matches : Collections.<PatientSimilarityView>emptyList();
        this.isManager = isManager;
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
        final int size = size() == 0 ? 0 : size() - 1;
        return toJSON(0, size);
    }

    @Override
    public JSONObject toJSON(final int fromIndex, final int toIndex)
        throws IndexOutOfBoundsException, IllegalArgumentException
    {
        if (toIndex < fromIndex) {
            throw new IllegalArgumentException("fromIndex(" + fromIndex + ") > toIndex(" + toIndex + ")");
        }
        final JSONObject referenceJson = this.reference.toJSON();
        final JSONArray matchesJson = convertEmpty(fromIndex, toIndex)
            ? new JSONArray()
            : buildMatchesJSONArray(fromIndex, toIndex);
        return new JSONObject()
            .put(QUERY_LABEL, referenceJson)
            .put(IS_MANAGER_LABEL, this.isManager)
            .put(TOTAL_SIZE_LABEL, size())
            .put(RESULTS_LABEL, matchesJson)
            .put(OFFSET_LABEL, fromIndex + 1)
            .put(RETURNED_SIZE_LABEL, matchesJson.length());
    }

    /**
     * Returns true iff there are no matches, and this empty data set should be converted to JSON.
     *
     * @param fromIndex the starting index for match to convert (inclusive, zero-based)
     * @param toIndex the last index for match to convert (inclusive, zero-based)
     * @return true iff there are no matches, but the empty {@link #getMatches()} should be converted to JSON
     */
    private boolean convertEmpty(final int fromIndex, final int toIndex)
    {
        return size() == 0 && fromIndex == 0 && toIndex == 0;
    }

    /**
     * Builds a JSON representation of {@link #getMatches()}.
     *
     * @param fromIndex the starting index for match to convert (inclusive, zero-based)
     * @param toIndex the last index for match to convert (inclusive, zero-based)
     * @return a {@link JSONArray} of {@link #getMatches()}
     */
    private JSONArray buildMatchesJSONArray(final int fromIndex, final int toIndex)
    {
        final JSONArray matchesJson = new JSONArray();

        for (int i = fromIndex; i <= toIndex; i++) {
            final JSONObject matchJson = this.matches.get(i).toJSON();
            processEmail(matchJson);
            matchesJson.put(matchJson);
        }
        return matchesJson;
    }

    /**
     * Sets the email as blank for {@code matchJson} if the current user does not manage {@link #getReference() the
     * reference patient}.
     *
     * @param matchJson the JSON for a patient matching {@link #getReference()}
     */
    private void processEmail(@Nonnull final JSONObject matchJson)
    {
        if (!this.isManager && matchJson.has(OWNER_LABEL)) {
            matchJson.optJSONObject(OWNER_LABEL).put(EMAIL_LABEL, StringUtils.EMPTY);
        }
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
        return this.isManager == that.isManager
            && Objects.equals(this.reference, that.reference)
            && Objects.equals(this.matches, that.matches);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(this.reference, this.matches, this.isManager);
    }
}
