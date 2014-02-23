/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.phenotips.data.similarity.internal;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Matchers;
import org.mockito.Mockito;
import org.phenotips.data.Patient;
import org.phenotips.data.permissions.AccessLevel;
import org.phenotips.data.permissions.PatientAccess;
import org.phenotips.data.permissions.PermissionsManager;
import org.phenotips.data.similarity.PatientSimilarityView;
import org.phenotips.data.similarity.PatientSimilarityViewFactory;
import org.phenotips.data.similarity.internal.mocks.MockOntologyTerm;
import org.phenotips.ontology.OntologyManager;
import org.phenotips.ontology.OntologyService;
import org.phenotips.ontology.OntologyTerm;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.test.mockito.MockitoComponentMockingRule;

/**
 * Tests for the "restricted" {@link PatientSimilarityViewFactory} implementation,
 * {@link RestrictedPatientSimilarityViewFactory}.
 * 
 * @version $Id$
 */
public class MutualInformationPatientSimilarityViewFactoryTest
{
    /** The matched patient document. */
    private static final DocumentReference PATIENT_1 = new DocumentReference("xwiki", "data", "P0000001");

    /** The default user used as the referrer of the matched patient, and of the reference patient for public access. */
    private static final DocumentReference USER_1 = new DocumentReference("xwiki", "XWiki", "padams");

    @Rule
    public final MockitoComponentMockingRule<PatientSimilarityViewFactory> mocker =
        new MockitoComponentMockingRule<PatientSimilarityViewFactory>(MutualInformationPatientSimilarityViewFactory.class);

    /** Basic tests for makeSimilarPatient. */
    @Test
    public void testMakeSimilarPatient() throws ComponentLookupException
    {
        Patient mockMatch = mock(Patient.class);
        Patient mockReference = mock(Patient.class);
        when(mockMatch.getDocument()).thenReturn(PATIENT_1);
        when(mockMatch.getReporter()).thenReturn(USER_1);
        when(mockReference.getReporter()).thenReturn(USER_1);

        PermissionsManager pm = this.mocker.getInstance(PermissionsManager.class);
        PatientAccess pa = mock(PatientAccess.class);
        when(pm.getPatientAccess(mockMatch)).thenReturn(pa);
        when(pa.getAccessLevel()).thenReturn(this.mocker.<AccessLevel> getInstance(AccessLevel.class, "view"));

        PatientSimilarityView result = this.mocker.getComponentUnderTest().makeSimilarPatient(mockMatch, mockReference);
        Assert.assertNotNull(result);
        Assert.assertSame(PATIENT_1, result.getDocument());
        Assert.assertSame(mockReference, result.getReference());
    }

    /** Missing reference throws exception. */
    @Test(expected = IllegalArgumentException.class)
    public void testMakeSimilarPatientWithNullReference() throws ComponentLookupException
    {
        this.mocker.getComponentUnderTest().makeSimilarPatient(mock(Patient.class), null);
    }

    /** Missing match throws exception. */
    @Test(expected = IllegalArgumentException.class)
    public void testMakeSimilarPatientWithNullMatch() throws ComponentLookupException
    {
        this.mocker.getComponentUnderTest().makeSimilarPatient(null, mock(Patient.class));
    }

    @Before
    public void setupComponents() throws ComponentLookupException
    {
        // Setup the ontology manager
        OntologyManager om = this.mocker.getInstance(OntologyManager.class);
        // Setup MIM and HPO ontology mocks
        OntologyService mim = Mockito.mock(OntologyService.class);
        when(om.getOntology("MIM")).thenReturn(mim);
        long mimSize = 50;
        when(mim.size()).thenReturn((long) mimSize);
        when(mim.count(Matchers.anyMapOf(String.class, String.class))).thenReturn(Math.round(Math.random() * mimSize));

        Set<OntologyTerm> ancestors = new HashSet<OntologyTerm>();

        OntologyTerm all =
            new MockOntologyTerm("HP:0000001", Collections.<OntologyTerm> emptySet(),
                Collections.<OntologyTerm> emptySet());
        ancestors.add(all);
        OntologyTerm phenotypes =
            new MockOntologyTerm("HP:0000118", Collections.singleton(all), new HashSet<OntologyTerm>(ancestors));
        ancestors.add(phenotypes);
        OntologyTerm abnormalNS =
            new MockOntologyTerm("HP:0000707", Collections.singleton(phenotypes), new HashSet<OntologyTerm>(ancestors));
        ancestors.add(abnormalNS);
        OntologyTerm abnormalCNS =
            new MockOntologyTerm("HP:0002011", Collections.singleton(abnormalNS), new HashSet<OntologyTerm>(ancestors));
        ancestors.add(abnormalCNS);
        OntologyTerm abnormalHMF =
            new MockOntologyTerm("HP:0011446", Collections.singleton(abnormalCNS), new HashSet<OntologyTerm>(ancestors));
        ancestors.add(abnormalHMF);
        OntologyTerm cognImp =
            new MockOntologyTerm("HP:0100543", Collections.singleton(abnormalHMF), new HashSet<OntologyTerm>(ancestors));
        ancestors.add(cognImp);
        OntologyTerm intDis =
            new MockOntologyTerm("HP:0001249", Collections.singleton(cognImp), new HashSet<OntologyTerm>(ancestors));
        ancestors.add(intDis);
        OntologyTerm mildIntDis =
            new MockOntologyTerm("HP:0001256", Collections.singleton(intDis), new HashSet<OntologyTerm>(ancestors));
        ancestors.add(mildIntDis);
        for (OntologyTerm term : ancestors) {
            when(om.resolveTerm(term.getId())).thenReturn(term);
        }

        ancestors.clear();
        ancestors.add(all);
        ancestors.add(phenotypes);
        OntologyTerm abnormalSkelS =
            new MockOntologyTerm("HP:0000924", Collections.singleton(phenotypes), new HashSet<OntologyTerm>(ancestors));
        ancestors.add(abnormalSkelS);
        OntologyTerm abnormalSkelM =
            new MockOntologyTerm("HP:0011842", Collections.singleton(abnormalSkelS), new HashSet<OntologyTerm>(
                ancestors));
        ancestors.add(abnormalSkelM);
        OntologyTerm abnormalJointMorph =
            new MockOntologyTerm("HP:0001367", Collections.singleton(abnormalSkelM), new HashSet<OntologyTerm>(
                ancestors));
        ancestors.add(abnormalJointMorph);
        OntologyTerm abnormalJointMob =
            new MockOntologyTerm("HP:0011729", Collections.singleton(abnormalJointMorph), new HashSet<OntologyTerm>(
                ancestors));
        ancestors.add(abnormalJointMob);
        OntologyTerm jointHyperm =
            new MockOntologyTerm("HP:0001382", Collections.singleton(abnormalJointMob), new HashSet<OntologyTerm>(
                ancestors));
        ancestors.add(jointHyperm);
        for (OntologyTerm term : ancestors) {
            when(om.resolveTerm(term.getId())).thenReturn(term);
        }
    }
}
