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
package org.phenotips.data.similarity.rest.internal;

import org.phenotips.data.Patient;
import org.phenotips.data.PatientRepository;
import org.phenotips.data.similarity.MatchedPatientClusterView;
import org.phenotips.data.similarity.PatientSimilarityView;
import org.phenotips.data.similarity.internal.DefaultMatchedPatientClusterView;
import org.phenotips.data.similarity.rest.PatientMatchResource;
import org.phenotips.matchingnotification.match.PatientMatch;
import org.phenotips.matchingnotification.storage.MatchStorageManager;
import org.phenotips.similarity.SimilarPatientsFinder;

import org.xwiki.component.annotation.Component;
import org.xwiki.container.Container;
import org.xwiki.container.Request;
import org.xwiki.rest.XWikiResource;

import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.json.JSONObject;

/**
 * Default implementation of the {@link PatientMatchResource}.
 *
 * @version $Id$
 * @since 1.1
 */
@Component
@Named("org.phenotips.data.similarity.rest.internal.DefaultPatientMatchResource")
@Singleton
public class DefaultPatientMatchResource extends XWikiResource implements PatientMatchResource
{
    private static final String REQ_NO = "reqNo";

    private static final String OFFSET = "offset";

    private static final String LIMIT = "maxResults";

    @Inject
    private PatientRepository repository;

    /** The similar patients finder. */
    @Inject
    private SimilarPatientsFinder similarPatientsFinder;

    @Inject
    private MatchStorageManager matchStorageManager;

    /** The XWiki container. */
    @Inject
    private Container container;

    @Override
    public Response findMatchesForPatient(@Nullable final String reference)
    {
        try {
            final Request request = this.container.getRequest();
            final int offset = NumberUtils.toInt((String) request.getProperty(OFFSET), 1);
            final int limit = NumberUtils.toInt((String) request.getProperty(LIMIT), -1);
            final int reqNo = NumberUtils.toInt((String) request.getProperty(REQ_NO), 1);

            return processRequest(reference, offset, limit, reqNo);
        } catch (final Exception e) {
            this.slf4Jlogger.error("Unexpected exception while generating matches: {}", e.getMessage(), e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }
    }

    private Response processRequest(final String reference, int offset, int limit, int reqNo)
    {
        if (StringUtils.isBlank(reference)) {
            this.slf4Jlogger.error("No reference patient ID was provided.");
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        try {
            final Patient patient = this.repository.get(reference);

            if (patient == null) {
                this.slf4Jlogger.error("Patient with ID: {} could not be found.", reference);
                return Response.status(Response.Status.BAD_REQUEST).build();
            }

            return generateMatchesResponse(patient, offset, limit, reqNo);
        } catch (final SecurityException e) {
            this.slf4Jlogger.error("Failed to retrieve patient with ID [{}]: {}", reference, e);
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
    }

    /**
     * Returns the JSON representation matching patients for {@code patient}.
     *
     * @param patient the reference patient object
     * @param offset the offset for the returned match results
     * @param limit the limit for the number of matches to return
     * @param reqNo the request number
     * @return a JSON representation of matches for patient with ID {@code reference}
     */
    private Response generateMatchesResponse(
        @Nonnull final Patient patient,
        final int offset,
        final int limit,
        final int reqNo)
    {
        final List<PatientSimilarityView> matches = this.similarPatientsFinder.findSimilarPatients(patient);

        Map<PatientSimilarityView, PatientMatch> savedMatches =
            this.matchStorageManager.saveLocalMatches(matches, patient.getId());

        final MatchedPatientClusterView cluster = new DefaultMatchedPatientClusterView(patient, matches, savedMatches);
        try {
            JSONObject matchesJson = cluster.toJSON(offset - 1, limit);
            matchesJson.put(REQ_NO, reqNo);
            return Response.ok(matchesJson, MediaType.APPLICATION_JSON_TYPE).build();
        } catch (final IndexOutOfBoundsException e) {
            this.slf4Jlogger.error("The requested offset is out of bounds: {}", offset);
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
    }
}
