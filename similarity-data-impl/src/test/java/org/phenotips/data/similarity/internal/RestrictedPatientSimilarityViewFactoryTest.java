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

import org.phenotips.components.ComponentManagerRegistry;
import org.phenotips.data.Disorder;
import org.phenotips.data.Feature;
import org.phenotips.data.FeatureMetadatum;
import org.phenotips.data.Patient;
import org.phenotips.data.permissions.AccessLevel;
import org.phenotips.data.permissions.PatientAccess;
import org.phenotips.data.permissions.PermissionsManager;
import org.phenotips.data.similarity.PatientSimilarityView;
import org.phenotips.data.similarity.PatientSimilarityViewFactory;
import org.phenotips.data.similarity.internal.mocks.MockDisorder;
import org.phenotips.data.similarity.internal.mocks.MockFeature;
import org.phenotips.data.similarity.internal.mocks.MockFeatureMetadatum;
import org.phenotips.messaging.Connection;
import org.phenotips.messaging.ConnectionManager;

import org.xwiki.cache.Cache;
import org.xwiki.cache.CacheException;
import org.xwiki.cache.CacheFactory;
import org.xwiki.cache.CacheManager;
import org.xwiki.cache.config.CacheConfiguration;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.test.mockito.MockitoComponentMockingRule;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for the "restricted" {@link PatientSimilarityViewFactory} implementation,
 * {@link RestrictedPatientSimilarityViewFactory}.
 * 
 * @version $Id$
 */
public class RestrictedPatientSimilarityViewFactoryTest
{
    /** The matched patient document. */
    private static final DocumentReference PATIENT_1 = new DocumentReference("xwiki", "data", "P0000001");

    /** The default user used as the referrer of the matched patient, and of the reference patient for public access. */
    private static final DocumentReference USER_1 = new DocumentReference("xwiki", "XWiki", "padams");

    /** The alternative user used as the referrer of the reference patient for matchable or private access. */
    private static final DocumentReference USER_2 = new DocumentReference("xwiki", "XWiki", "hmccoy");

    @Rule
    public final MockitoComponentMockingRule<PatientSimilarityViewFactory> mocker =
        new MockitoComponentMockingRule<PatientSimilarityViewFactory>(RestrictedPatientSimilarityViewFactory.class);

    /** Basic tests for makeSimilarPatient. */
    @Test
    public void testMakeSimilarPatient() throws Exception
    {
        Patient mockMatch = mock(Patient.class);
        Patient mockReference = mock(Patient.class);
        when(mockMatch.getDocument()).thenReturn(PATIENT_1);
        when(mockMatch.getId()).thenReturn(PATIENT_1.getName());
        when(mockMatch.getReporter()).thenReturn(USER_1);
        when(mockReference.getReporter()).thenReturn(USER_1);

        PermissionsManager pm = this.mocker.getInstance(PermissionsManager.class);
        PatientAccess pa = mock(PatientAccess.class);
        when(pm.getPatientAccess(mockMatch)).thenReturn(pa);
        when(pa.getAccessLevel()).thenReturn(this.mocker.<AccessLevel>getInstance(AccessLevel.class, "view"));

        ConnectionManager cm = mock(ConnectionManager.class);
        when(ComponentManagerRegistry.getContextComponentManager().getInstance(ConnectionManager.class)).thenReturn(cm);
        Connection c = mock(Connection.class);
        when(cm.getConnection(Mockito.any(PatientSimilarityView.class))).thenReturn(c);
        when(c.getId()).thenReturn(Long.valueOf(42));

        PatientSimilarityView result = this.mocker.getComponentUnderTest().makeSimilarPatient(mockMatch, mockReference);
        Assert.assertNotNull(result);
        Assert.assertSame(PATIENT_1, result.getDocument());
        Assert.assertSame(mockReference, result.getReference());
        Assert.assertEquals("42", result.getContactToken());
    }

    /** Pairing with a matchable patient does indeed restrict access to private information. */
    @Test
    public void testMakeSimilarPatientIsRestricted() throws ComponentLookupException
    {
        Patient mockMatch = mock(Patient.class);
        Patient mockReference = mock(Patient.class);
        when(mockMatch.getDocument()).thenReturn(PATIENT_1);
        when(mockMatch.getId()).thenReturn(PATIENT_1.getName());
        when(mockMatch.getReporter()).thenReturn(USER_1);
        when(mockReference.getReporter()).thenReturn(USER_2);

        PermissionsManager pm = this.mocker.getInstance(PermissionsManager.class);
        PatientAccess pa = mock(PatientAccess.class);
        when(pm.getPatientAccess(mockMatch)).thenReturn(pa);
        AccessLevel match = this.mocker.<AccessLevel>getInstance(AccessLevel.class, "match");
        AccessLevel view = this.mocker.<AccessLevel>getInstance(AccessLevel.class, "view");
        when(pa.getAccessLevel()).thenReturn(match);
        when(match.compareTo(view)).thenReturn(-5);

        ConnectionManager cm = mock(ConnectionManager.class);
        when(ComponentManagerRegistry.getContextComponentManager().getInstance(ConnectionManager.class)).thenReturn(cm);
        Connection c = mock(Connection.class);
        when(cm.getConnection(Mockito.any(PatientSimilarityView.class))).thenReturn(c);
        when(c.getId()).thenReturn(Long.valueOf(42));

        Map<String, FeatureMetadatum> matchMeta = new HashMap<String, FeatureMetadatum>();
        matchMeta.put("age_of_onset", new MockFeatureMetadatum("HP:0003577", "Congenital onset", "age_of_onset"));
        matchMeta.put("speed_of_onset", new MockFeatureMetadatum("HP:0011010", "Chronic", "speed_of_onset"));
        matchMeta.put("pace", new MockFeatureMetadatum("HP:0003677", "Slow", "pace"));
        Map<String, FeatureMetadatum> referenceMeta = new HashMap<String, FeatureMetadatum>();
        referenceMeta.put("age_of_onset", new MockFeatureMetadatum("HP:0003577", "Congenital onset", "age_of_onset"));
        referenceMeta.put("speed_of_onset", new MockFeatureMetadatum("HP:0011009", "Acute", "speed_of_onset"));
        referenceMeta.put("death", new MockFeatureMetadatum("HP:0003826", "Stillbirth", "death"));

        Feature jhm = new MockFeature("HP:0001382", "Joint hypermobility", "phenotype", true);
        Feature od = new MockFeature("HP:0012165", "Oligodactyly", "phenotype", true);
        Feature cat = new MockFeature("HP:0000518", "Cataract", "phenotype", true);
        Feature id = new MockFeature("HP:0001249", "Intellectual disability", "phenotype", matchMeta, false);
        Feature mid =
            new MockFeature("HP:0001256", "Mild intellectual disability", "phenotype", referenceMeta, true);
        Set<Feature> matchPhenotypes = new HashSet<Feature>();
        Set<Feature> referencePhenotypes = new HashSet<Feature>();
        matchPhenotypes.add(jhm);
        matchPhenotypes.add(od);
        matchPhenotypes.add(id);
        referencePhenotypes.add(jhm);
        referencePhenotypes.add(mid);
        referencePhenotypes.add(cat);
        Mockito.<Set<? extends Feature>>when(mockMatch.getFeatures()).thenReturn(matchPhenotypes);
        Mockito.<Set<? extends Feature>>when(mockReference.getFeatures()).thenReturn(referencePhenotypes);

        Disorder d1 = new MockDisorder("MIM:123", "Some disease");
        Disorder d2 = new MockDisorder("MIM:234", "Some other disease");
        Disorder d3 = new MockDisorder("MIM:345", "Yet another disease");
        Set<Disorder> matchDiseases = new HashSet<Disorder>();
        matchDiseases.add(d1);
        matchDiseases.add(d2);
        Set<Disorder> referenceDiseases = new HashSet<Disorder>();
        referenceDiseases.add(d1);
        referenceDiseases.add(d3);
        Mockito.<Set<? extends Disorder>>when(mockMatch.getDisorders()).thenReturn(matchDiseases);
        Mockito.<Set<? extends Disorder>>when(mockReference.getDisorders()).thenReturn(referenceDiseases);

        PatientSimilarityView result = this.mocker.getComponentUnderTest().makeSimilarPatient(mockMatch, mockReference);
        Assert.assertNotNull(result);
        Assert.assertSame(mockReference, result.getReference());
        Assert.assertNull(result.getDocument());
        Assert.assertEquals(2, result.getFeatures().size());
        Assert.assertEquals(0, result.getDisorders().size());
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
    @SuppressWarnings("unchecked")
    public void setupComponents() throws ComponentLookupException, CacheException
    {
        CacheManager cacheManager = this.mocker.getInstance(CacheManager.class);

        CacheFactory cacheFactory = mock(CacheFactory.class);
        when(cacheManager.getLocalCacheFactory()).thenReturn(cacheFactory);

        Cache<PatientSimilarityView> cache = (Cache<PatientSimilarityView>) mock(Cache.class);
        doReturn(cache).when(cacheFactory).newCache(Mockito.any(CacheConfiguration.class));
        doReturn(null).when(cache).get(Mockito.anyString());
    }
}
