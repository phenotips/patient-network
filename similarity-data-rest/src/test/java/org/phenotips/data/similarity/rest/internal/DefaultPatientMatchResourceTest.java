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
import org.phenotips.data.similarity.PatientSimilarityView;
import org.phenotips.data.similarity.rest.PatientMatchResource;
import org.phenotips.similarity.SimilarPatientsFinder;

import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.container.Container;
import org.xwiki.context.Execution;
import org.xwiki.context.ExecutionContext;
import org.xwiki.store.UnexpectedException;
import org.xwiki.test.mockito.MockitoComponentMockingRule;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.inject.Provider;
import javax.ws.rs.core.Response;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;

import com.xpn.xwiki.XWikiContext;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DefaultPatientMatchResource}.
 */
public class DefaultPatientMatchResourceTest
{
    private static final String REFERENCE = "reference";

    private static final String MATCH_1 = "match1";

    private static final String MATCH_2 = "match2";

    private static final String MATCH_3 = "match3";

    private static final String SECURE = "secure";

    private static final String ID = "id";

    private static final String OWNER = "owner";

    private static final String EMAIL = "email";

    private static final String QUERY = "query";

    private static final String TOTAL_SIZE = "resultsCount";

    private static final String RETURNED_SIZE = "returnedCount";

    private static final String RESULTS = "results";

    private static final String REQ_NO = "reqNo";

    private static final String UNAUTHORIZED_MSG = "User unauthorized";

    private static final String UNEXPECTED_MSG = "Unexpected exception";

    private static final String LIMIT = "maxResults";

    private static final String OFFSET = "offset";

    @Rule
    public MockitoComponentMockingRule<PatientMatchResource> mocker =
        new MockitoComponentMockingRule<PatientMatchResource>(DefaultPatientMatchResource.class);

    @Mock
    private Patient reference;

    @Mock
    private PatientSimilarityView match1;

    @Mock
    private PatientSimilarityView match2;

    @Mock
    private PatientSimilarityView match3;

    @Mock
    private org.xwiki.container.Request request;

    private PatientMatchResource component;

    @Mock
    private Logger logger;

    private PatientRepository repository;

    private SimilarPatientsFinder similarPatientsFinder;

    private JSONObject expectedAll;

    @Before
    public void setUp() throws ComponentLookupException
    {
        MockitoAnnotations.initMocks(this);
        final Execution execution = mock(Execution.class);
        final ExecutionContext executionContext = mock(ExecutionContext.class);
        final ComponentManager compManager = this.mocker.getInstance(ComponentManager.class, "context");
        final Provider<XWikiContext> provider = this.mocker.getInstance(XWikiContext.TYPE_PROVIDER);
        final XWikiContext context = provider.get();
        when(compManager.getInstance(Execution.class)).thenReturn(execution);
        when(execution.getContext()).thenReturn(executionContext);
        when(executionContext.getProperty("xwikicontext")).thenReturn(context);

        this.component = this.mocker.getComponentUnderTest();
        // Set up all injected classes.
        this.logger = this.mocker.getMockedLogger();
        this.repository = this.mocker.getInstance(PatientRepository.class, SECURE);
        this.similarPatientsFinder = this.mocker.getInstance(SimilarPatientsFinder.class);

        // Mock the reference patient.
        when(this.repository.get(REFERENCE)).thenReturn(this.reference);
        when(this.reference.toJSON()).thenReturn(new JSONObject().put(ID, REFERENCE));

        // Mock similar patient search.
        final List<PatientSimilarityView> matches = Arrays.asList(this.match1, this.match2, this.match3);
        when(this.similarPatientsFinder.findSimilarPatients(this.reference)).thenReturn(matches);

        // Mock container interactions.
        final Container container = this.mocker.getInstance(Container.class);
        when(container.getRequest()).thenReturn(this.request);

        // Mock individual match data.
        when(this.match1.toJSON()).thenReturn(new JSONObject().put(ID, MATCH_1)
            .put(OWNER, new JSONObject()
                .put(EMAIL, MATCH_1)));
        when(this.match2.toJSON()).thenReturn(new JSONObject().put(ID, MATCH_2)
            .put(OWNER, new JSONObject()
                .put(EMAIL, MATCH_2)));
        when(this.match3.toJSON()).thenReturn(new JSONObject().put(ID, MATCH_3)
            .put(OWNER, new JSONObject()
                .put(EMAIL, MATCH_3)));

        this.expectedAll = constructAllMatchesJSON();
    }

    @After
    public void tearDown()
    {
        this.expectedAll = null;
    }

    @Test
    public void findMatchesForPatientNullReferenceReturnsBadRequest()
    {
        when(this.request.getProperty(OFFSET)).thenReturn("1");
        when(this.request.getProperty(LIMIT)).thenReturn("10");
        when(this.request.getProperty(REQ_NO)).thenReturn("1");
        final Response response = this.component.findMatchesForPatient(null);
        verify(this.logger).error("No reference patient ID was provided.");
        Assert.assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
    }

    @Test
    public void findMatchesForPatientEmptyReferenceReturnsBadRequest()
    {
        when(this.request.getProperty(OFFSET)).thenReturn("1");
        when(this.request.getProperty(LIMIT)).thenReturn("10");
        when(this.request.getProperty(REQ_NO)).thenReturn("1");
        final Response response = this.component.findMatchesForPatient(StringUtils.EMPTY);
        verify(this.logger).error("No reference patient ID was provided.");
        Assert.assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
    }

    @Test
    public void findMatchesForPatientBlankReferenceReturnsBadRequest()
    {
        when(this.request.getProperty(OFFSET)).thenReturn("1");
        when(this.request.getProperty(LIMIT)).thenReturn("10");
        when(this.request.getProperty(REQ_NO)).thenReturn("1");
        final Response response = this.component.findMatchesForPatient(StringUtils.SPACE);
        verify(this.logger).error("No reference patient ID was provided.");
        Assert.assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
    }

    @Test
    public void findMatchesForPatientPatientDoesNotExistResultsInBadRequest()
    {
        when(this.request.getProperty(OFFSET)).thenReturn("1");
        when(this.request.getProperty(LIMIT)).thenReturn("10");
        when(this.request.getProperty(REQ_NO)).thenReturn("1");
        when(this.repository.get(REFERENCE)).thenReturn(null);
        final Response response = this.component.findMatchesForPatient(REFERENCE);
        verify(this.logger).error("Patient with ID: {} could not be found.", REFERENCE);
        Assert.assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
    }

    @Test
    public void findMatchesForPatientUserNotAuthorizedToSeeReferencePatientResultsInNotAuthorized()
    {
        when(this.request.getProperty(OFFSET)).thenReturn("1");
        when(this.request.getProperty(LIMIT)).thenReturn("10");
        when(this.request.getProperty(REQ_NO)).thenReturn("1");
        SecurityException ex = new SecurityException(UNAUTHORIZED_MSG);
        when(this.repository.get(REFERENCE)).thenThrow(ex);
        final Response response = this.component.findMatchesForPatient(REFERENCE);
        verify(this.logger).error("Failed to retrieve patient with ID [{}]: {}", REFERENCE, ex);
        Assert.assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), response.getStatus());
    }

    @Test
    public void findMatchesForPatientNoMatchesFoundResultsInEmptyValidResponse()
    {
        final List<PatientSimilarityView> matches = Collections.emptyList();
        when(this.request.getProperty(OFFSET)).thenReturn("1");
        when(this.request.getProperty(LIMIT)).thenReturn("10");
        when(this.request.getProperty(REQ_NO)).thenReturn("1");
        when(this.similarPatientsFinder.findSimilarPatients(this.reference)).thenReturn(matches);
        final Response response = this.component.findMatchesForPatient(REFERENCE);
        final JSONObject expected = new JSONObject()
            .put(QUERY, new JSONObject()
                .put(ID, REFERENCE))
            .put(TOTAL_SIZE, 0)
            .put(RETURNED_SIZE, 0)
            .put(REQ_NO, 1)
            .put(OFFSET, 1)
            .put(RESULTS, new JSONArray());
        Assert.assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        Assert.assertTrue(expected.similar(response.getEntity()));
    }

    @Test
    public void findMatchesForPatientLessThanOneOffsetResultsInBadRequest()
    {
        when(this.request.getProperty(OFFSET)).thenReturn("-1");
        when(this.request.getProperty(LIMIT)).thenReturn("10");
        when(this.request.getProperty(REQ_NO)).thenReturn("1");
        final Response response = this.component.findMatchesForPatient(REFERENCE);
        verify(this.logger).error("The requested offset is out of bounds: {}", -1);
        Assert.assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
    }

    @Test
    public void findMatchesForPatientTooLargeOffsetResultsInBadRequest()
    {
        when(this.request.getProperty(OFFSET)).thenReturn("60");
        when(this.request.getProperty(LIMIT)).thenReturn("80");
        when(this.request.getProperty(REQ_NO)).thenReturn("1");
        final Response response = this.component.findMatchesForPatient(REFERENCE);
        verify(this.logger).error("The requested offset is out of bounds: {}", 60);
        Assert.assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
    }

    @Test
    public void findMatchesForPatientOffsetNullDefaultsToOne()
    {
        when(this.request.getProperty(OFFSET)).thenReturn(null);
        when(this.request.getProperty(LIMIT)).thenReturn("80");
        when(this.request.getProperty(REQ_NO)).thenReturn("1");

        final Response response = this.component.findMatchesForPatient(REFERENCE);
        Assert.assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        Assert.assertTrue(this.expectedAll.similar(response.getEntity()));
    }

    @Test
    public void findMatchesForPatientLimitNullDefaultsToNegativeOne()
    {
        when(this.request.getProperty(OFFSET)).thenReturn("1");
        when(this.request.getProperty(LIMIT)).thenReturn(null);
        when(this.request.getProperty(REQ_NO)).thenReturn("1");

        final Response response = this.component.findMatchesForPatient(REFERENCE);
        Assert.assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        Assert.assertTrue(this.expectedAll.similar(response.getEntity()));
    }

    @Test
    public void findMatchesForPatientReqNoNullDefaultsToOne()
    {
        when(this.request.getProperty(OFFSET)).thenReturn("1");
        when(this.request.getProperty(LIMIT)).thenReturn("10");
        when(this.request.getProperty(REQ_NO)).thenReturn(null);

        final Response response = this.component.findMatchesForPatient(REFERENCE);
        Assert.assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        Assert.assertTrue(this.expectedAll.similar(response.getEntity()));
    }

    @Test
    public void findMatchesForPatientLimitBiggerThanMatchesNumberReturnsAllMatchesFromOffset()
    {
        when(this.request.getProperty(OFFSET)).thenReturn("1");
        when(this.request.getProperty(LIMIT)).thenReturn("80");
        when(this.request.getProperty(REQ_NO)).thenReturn("1");

        final Response response = this.component.findMatchesForPatient(REFERENCE);
        Assert.assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        Assert.assertTrue(this.expectedAll.similar(response.getEntity()));
    }

    @Test
    public void findMatchesForPatientLimitNegativeReturnsAllMatchesFromOffset()
    {
        when(this.request.getProperty(OFFSET)).thenReturn("2");
        when(this.request.getProperty(LIMIT)).thenReturn("-1");
        when(this.request.getProperty(REQ_NO)).thenReturn("1");
        final JSONObject expected = new JSONObject()
            .put(QUERY, new JSONObject()
                .put(ID, REFERENCE))
            .put(TOTAL_SIZE, 3)
            .put(RETURNED_SIZE, 2)
            .put(REQ_NO, 1)
            .put(OFFSET, 2)
            .put(RESULTS, new JSONArray()
                .put(this.match2.toJSON())
                .put(this.match3.toJSON()));

        final Response response = this.component.findMatchesForPatient(REFERENCE);
        Assert.assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        Assert.assertTrue(expected.similar(response.getEntity()));
    }

    @Test
    public void findMatchesForPatientLimitLessThanLastResultReturnsCorrectSubset()
    {
        when(this.request.getProperty(OFFSET)).thenReturn("2");
        when(this.request.getProperty(LIMIT)).thenReturn("1");
        when(this.request.getProperty(REQ_NO)).thenReturn("1");
        final JSONObject expected = new JSONObject()
            .put(QUERY, new JSONObject()
                .put(ID, REFERENCE))
            .put(TOTAL_SIZE, 3)
            .put(RETURNED_SIZE, 1)
            .put(REQ_NO, 1)
            .put(OFFSET, 2)
            .put(RESULTS, new JSONArray()
                .put(this.match2.toJSON()));

        final Response response = this.component.findMatchesForPatient(REFERENCE);
        Assert.assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        Assert.assertTrue(expected.similar(response.getEntity()));
    }

    @Test
    public void findMatchesForPatientUnexpectedExceptionIsThrown()
    {
        when(this.request.getProperty(OFFSET)).thenReturn("1");
        when(this.request.getProperty(LIMIT)).thenReturn("10");
        when(this.request.getProperty(REQ_NO)).thenReturn("1");
        UnexpectedException ex = new UnexpectedException(UNEXPECTED_MSG);
        when(this.repository.get(REFERENCE)).thenThrow(ex);
        final Response response = this.component.findMatchesForPatient(REFERENCE);
        verify(this.logger).error("Unexpected exception while generating matches: {}", UNEXPECTED_MSG, ex);
        Assert.assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
    }

    private JSONObject constructAllMatchesJSON()
    {
        return new JSONObject()
            .put(QUERY, new JSONObject()
                .put(ID, REFERENCE))
            .put(TOTAL_SIZE, 3)
            .put(RETURNED_SIZE, 3)
            .put(REQ_NO, 1)
            .put(OFFSET, 1)
            .put(RESULTS, new JSONArray()
                .put(this.match1.toJSON())
                .put(this.match2.toJSON())
                .put(this.match3.toJSON()));
    }
}
