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

import org.xwiki.model.reference.DocumentReference;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DefaultMatchedPatientClusterView}.
 */
public class DefaultMatchedPatientClusterViewTest
{
    private static final String ID_LABEL = "id";

    private static final String OWNER_LABEL = "owner";

    private static final String EMAIL_LABEL = "email";

    private static final String REFERENCE = "reference";

    private static final String PATIENT_1 = "patient1";

    private static final String PATIENT_2 = "patient2";

    private static final String PATIENT_3 = "patient3";

    private static final String PATIENT_4 = "patient4";

    private static final String PATIENT_5 = "patient5";

    private static final String QUERY_LABEL = "query";

    private static final String TOTAL_LABEL = "resultsCount";

    private static final String RETURNED_LABEL = "returnedCount";

    private static final String RESULTS_LABEL = "results";

    private static final String OFFSET_LABEL = "offset";

    @Mock
    private Patient reference;

    @Mock
    private DocumentReference docRef;

    @Mock
    private PatientSimilarityView patient1;

    @Mock
    private DocumentReference doc1;

    @Mock
    private PatientSimilarityView patient2;

    @Mock
    private DocumentReference doc2;

    @Mock
    private PatientSimilarityView patient3;

    @Mock
    private DocumentReference doc3;

    @Mock
    private PatientSimilarityView patient4;

    @Mock
    private DocumentReference doc4;

    @Mock
    private PatientSimilarityView patient5;

    @Mock
    private DocumentReference doc5;

    private DefaultMatchedPatientClusterView matchesCV;

    private List<PatientSimilarityView> matchList;

    @Before
    public void setUp()
    {
        MockitoAnnotations.initMocks(this);

        when(this.reference.toJSON()).thenReturn(new JSONObject().put(ID_LABEL, REFERENCE));
        when(this.reference.getDocumentReference()).thenReturn(this.docRef);

        when(this.patient1.toJSON()).thenReturn(new JSONObject().put(ID_LABEL, PATIENT_1)
            .put(OWNER_LABEL, new JSONObject()
                .put(EMAIL_LABEL, PATIENT_1)));
        when(this.patient1.getDocumentReference()).thenReturn(this.doc1);

        when(this.patient2.toJSON()).thenReturn(new JSONObject().put(ID_LABEL, PATIENT_2)
            .put(OWNER_LABEL, new JSONObject()
                .put(EMAIL_LABEL, PATIENT_2)));
        when(this.patient2.getDocumentReference()).thenReturn(this.doc2);

        when(this.patient3.toJSON()).thenReturn(new JSONObject().put(ID_LABEL, PATIENT_3)
            .put(OWNER_LABEL, new JSONObject()
                .put(EMAIL_LABEL, PATIENT_3)));
        when(this.patient3.getDocumentReference()).thenReturn(this.doc3);

        when(this.patient4.toJSON()).thenReturn(new JSONObject().put(ID_LABEL, PATIENT_4)
            .put(OWNER_LABEL, new JSONObject()
                .put(EMAIL_LABEL, PATIENT_4)));
        when(this.patient4.getDocumentReference()).thenReturn(this.doc4);

        when(this.patient5.toJSON()).thenReturn(new JSONObject().put(ID_LABEL, PATIENT_5)
            .put(OWNER_LABEL, new JSONObject()
                .put(EMAIL_LABEL, PATIENT_5)));
        when(this.patient5.getDocumentReference()).thenReturn(this.doc5);

        this.matchList = Arrays.asList(this.patient1, this.patient2, this.patient3, this.patient4, this.patient5);
        this.matchesCV = new DefaultMatchedPatientClusterView(this.reference, this.matchList);
    }

    @Test(expected = NullPointerException.class)
    public void instantiatingClassWithNullPatientThrowsException()
    {
        new DefaultMatchedPatientClusterView(null, this.matchList);
    }

    @Test
    public void getReferenceReturnsTheReferenceThatWasSet()
    {
        Assert.assertEquals(this.reference, this.matchesCV.getReference());
    }

    @Test
    public void getMatchesReturnsEmptyListIfNoMatchesSet()
    {
        final MatchedPatientClusterView matches = new DefaultMatchedPatientClusterView(this.reference, null);
        Assert.assertTrue(matches.getMatches().isEmpty());
    }

    @Test
    public void getMatchesReturnsTheMatchesThatWereProvided()
    {
        Assert.assertEquals(this.matchList, this.matchesCV.getMatches());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void getMatchesReturnedMatchesCannotBeModified()
    {
        this.matchesCV.getMatches().add(mock(PatientSimilarityView.class));
    }

    @Test
    public void sizeIsZeroIfMatchesIsNull()
    {
        final MatchedPatientClusterView matches = new DefaultMatchedPatientClusterView(this.reference, null);
        Assert.assertEquals(0, matches.size());
    }

    @Test
    public void sizeIsZeroIfMatchesIsEmpty()
    {
        final MatchedPatientClusterView matches = new DefaultMatchedPatientClusterView(this.reference,
            Collections.<PatientSimilarityView>emptyList());
        Assert.assertEquals(0, matches.size());
    }

    @Test
    public void sizeReturnsCorrectNumberOfMatches()
    {
        Assert.assertEquals(5, this.matchesCV.size());
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void toJSONThrowsExceptionIfFromIndexInvalid()
    {
        this.matchesCV.toJSON(-1, 3);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void toJSONThrowsExceptionIfFromIndexIsGreaterThanNumberOfRecords()
    {
        this.matchesCV.toJSON(100, 1);
    }

    @Test
    public void toJSONWorksOkIfMaxRecordsIsLargerThanNumberOfRecords()
    {
        final JSONObject result = this.matchesCV.toJSON(0, 100);
        Assert.assertEquals(result.getJSONArray(RESULTS_LABEL).length(), 5);
    }

    @Test
    public void toJSONGetsCorrectDataForProvidedIndices()
    {
        final JSONObject result = this.matchesCV.toJSON(1, 3);
        final JSONObject expected = new JSONObject()
            .put(QUERY_LABEL, new JSONObject()
                .put(ID_LABEL, REFERENCE))
            .put(TOTAL_LABEL, 5)
            .put(RETURNED_LABEL, 3)
            .put(OFFSET_LABEL, 2)
            .put(RESULTS_LABEL, new JSONArray()
                .put(new JSONObject()
                    .put(OWNER_LABEL, new JSONObject()
                        .put(EMAIL_LABEL, PATIENT_2))
                    .put(ID_LABEL, PATIENT_2))
                .put(new JSONObject()
                    .put(OWNER_LABEL, new JSONObject()
                        .put(EMAIL_LABEL, PATIENT_3))
                    .put(ID_LABEL, PATIENT_3))
                .put(new JSONObject()
                    .put(OWNER_LABEL, new JSONObject()
                        .put(EMAIL_LABEL, PATIENT_4))
                    .put(ID_LABEL, PATIENT_4)));
        Assert.assertTrue(expected.similar(result));
    }

    @Test
    public void toJSONGetsAllDataIfNoIndicesProvided()
    {
        final JSONObject result = this.matchesCV.toJSON();
        final JSONObject expected = new JSONObject()
            .put(QUERY_LABEL, new JSONObject()
                .put(ID_LABEL, REFERENCE))
            .put(TOTAL_LABEL, 5)
            .put(RETURNED_LABEL, 5)
            .put(OFFSET_LABEL, 1)
            .put(RESULTS_LABEL, new JSONArray()
                .put(new JSONObject()
                    .put(OWNER_LABEL, new JSONObject()
                        .put(EMAIL_LABEL, PATIENT_1))
                    .put(ID_LABEL, PATIENT_1))
                .put(new JSONObject()
                    .put(OWNER_LABEL, new JSONObject()
                        .put(EMAIL_LABEL, PATIENT_2))
                    .put(ID_LABEL, PATIENT_2))
                .put(new JSONObject()
                    .put(OWNER_LABEL, new JSONObject()
                        .put(EMAIL_LABEL, PATIENT_3))
                    .put(ID_LABEL, PATIENT_3))
                .put(new JSONObject()
                    .put(OWNER_LABEL, new JSONObject()
                        .put(EMAIL_LABEL, PATIENT_4))
                    .put(ID_LABEL, PATIENT_4))
                .put(new JSONObject()
                    .put(OWNER_LABEL, new JSONObject()
                        .put(EMAIL_LABEL, PATIENT_5))
                    .put(ID_LABEL, PATIENT_5)));
        Assert.assertTrue(expected.similar(result));
    }

    @Test
    public void equalsReturnsTrueForTwoDifferentObjectsWithSameData()
    {
        final MatchedPatientClusterView v2 = new DefaultMatchedPatientClusterView(this.reference, this.matchList);
        Assert.assertTrue(v2.equals(this.matchesCV));
    }

    @Test
    public void equalsReturnsTrueForTwoIdenticalObjects()
    {
        Assert.assertTrue(this.matchesCV.equals(this.matchesCV));
    }

    @Test
    public void equalsReturnsFalseForTwoDifferentObjectsWithDifferentReference()
    {
        final MatchedPatientClusterView v2 = new DefaultMatchedPatientClusterView(mock(Patient.class), this.matchList);
        Assert.assertFalse(v2.equals(this.matchesCV));
    }

    @Test
    public void equalsReturnsFalseForTwoDifferentObjectsWithDifferentMatchList()
    {
        final List<PatientSimilarityView> m2 = Arrays.asList(this.patient1, this.patient2, this.patient3);
        final MatchedPatientClusterView v2 = new DefaultMatchedPatientClusterView(this.reference, m2);
        Assert.assertFalse(v2.equals(this.matchesCV));
    }

    @Test
    public void hashCodeIsTheSameForTwoObjectsWithSameData()
    {
        final MatchedPatientClusterView v2 = new DefaultMatchedPatientClusterView(this.reference, this.matchList);
        Assert.assertEquals(v2.hashCode(), this.matchesCV.hashCode());
    }
}
