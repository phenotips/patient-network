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
import org.phenotips.similarity.SimilarPatientsFinder;

import org.xwiki.component.annotation.Component;
import org.xwiki.container.Container;
import org.xwiki.container.Request;
import org.xwiki.rest.XWikiResource;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.json.JSONObject;
import org.slf4j.Logger;

/**
 * Default implementation of the {@link PatientMatchResource}.
 *
 * @version $Id$
 * @since 1.2
 */
@Component
@Named("org.phenotips.data.similarity.rest.internal.DefaultPatientMatchResource")
@Singleton
public class DefaultPatientMatchResource extends XWikiResource implements PatientMatchResource
{
    private static final String REQ_NO = "reqNo";

    private static final String OFFSET = "offset";

    private static final String LIMIT = "maxResults";

    /** The logging object. */
    @Inject
    private Logger logger;

    /** The secure patient repository. */
    @Inject
    @Named("secure")
    private PatientRepository repository;

    /** The similar patients finder. */
    @Inject
    private SimilarPatientsFinder similarPatientsFinder;

    /** The XWiki container. */
    @Inject
    private Container container;

    @Override
    public Response findMatchingPatients(@Nullable final String reference)
    {
        final Request request = this.container.getRequest();
        final int offset =  NumberUtils.toInt((String) request.getProperty(OFFSET), 1);
        final int limit = NumberUtils.toInt((String) request.getProperty(LIMIT), -1);
        final int reqNo = NumberUtils.toInt((String) request.getProperty(REQ_NO), 1);
        try {
            return getMatchesResponse(reference, offset, limit, reqNo);
        } catch (final SecurityException e) {
            this.logger.error("Failed to retrieve patient with ID [{}]: {}", reference, e.getMessage());
            return Response.status(Response.Status.UNAUTHORIZED).build();
        } catch (final IndexOutOfBoundsException | IllegalArgumentException e) {
            this.logger.error("The requested offset is out of bounds: {}", offset);
            return Response.status(Response.Status.BAD_REQUEST).build();
        } catch (final Exception e) {
            this.logger.error("Unexpected exception while generating matches JSON: {}", e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Calculates the last index based on the provided {@code offset}, {@code limit}, and size of
     * {@code matchedCluster}.
     *
     * @param matchedCluster the {@link MatchedPatientClusterView} object containing the matches for requested patient
     * @param offset the offset for the match data to be returned
     * @param limit the limit for the number of matches to be returned
     * @return the index of the last match to be returned
     */
    private int getLastIndex(@Nonnull final MatchedPatientClusterView matchedCluster, final int offset, final int limit)
    {
        final int totalSize = matchedCluster.size();
        final int lastItemIdx = totalSize - 1;
        final int requestedLast = limit >= 0 ? offset + limit - 2 : lastItemIdx;
        return requestedLast <= lastItemIdx ? requestedLast : lastItemIdx;
    }

    /**
     * Returns the JSON representation matching patients for {@code reference}. Throws a {@link WebApplicationException}
     * if {@code reference} is blank.
     *
     * @param reference the reference patient ID
     * @param offset the offset for the returned match results
     * @param limit the limit for the number of matches to return
     * @param reqNo the request number
     * @return a JSON representation of matches for patient with ID {@code reference}
     * @throws WebApplicationException if {@code reference} is blank, or patient with that ID does not exist
     */
    private Response getMatchesResponse(
        @Nullable final String reference,
        final int offset,
        final int limit,
        final int reqNo)
    {
        if (StringUtils.isBlank(reference)) {
            this.logger.error("No reference patient ID was provided.");
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        final Patient patient = this.repository.get(reference);

        if (patient == null) {
            this.logger.error("Patient with ID: {} could not be found.", reference);
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        final List<PatientSimilarityView> matches = this.similarPatientsFinder.findSimilarPatients(patient);
        final MatchedPatientClusterView cluster = new DefaultMatchedPatientClusterView(patient, matches);
        final JSONObject matchesJson = !matches.isEmpty()
            ? cluster.toJSON(offset - 1, getLastIndex(cluster, offset, limit))
            : cluster.toJSON();
        matchesJson.put(REQ_NO, reqNo);
        return Response.ok(matchesJson, MediaType.APPLICATION_JSON_TYPE).build();
    }
}
