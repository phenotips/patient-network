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

import org.phenotips.data.Disorder;
import org.phenotips.data.Feature;
import org.phenotips.data.FeatureMetadatum;
import org.phenotips.data.Patient;
import org.phenotips.data.permissions.internal.access.NoAccessLevel;
import org.phenotips.data.permissions.internal.access.OwnerAccessLevel;
import org.phenotips.data.similarity.AccessType;
import org.phenotips.data.similarity.PatientSimilarityView;
import org.phenotips.data.similarity.internal.mocks.MockDisorder;
import org.phenotips.data.similarity.internal.mocks.MockFeature;
import org.phenotips.data.similarity.internal.mocks.MockFeatureMetadatum;
import org.phenotips.data.similarity.permissions.internal.MatchAccessLevel;
import org.phenotips.ontology.OntologyManager;
import org.phenotips.ontology.OntologyTerm;

import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.logging.Logger;
import org.xwiki.model.reference.DocumentReference;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;

import net.sf.json.JSONObject;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for the "restricted" {@link PatientSimilarityView} implementation, {@link RestrictedPatientSimilarityView}.
 * 
 * @version $Id$
 */
public class RestrictedPatientSimilarityViewTest
{
    /** The matched patient document. */
    private static final DocumentReference PATIENT_1 = new DocumentReference("xwiki", "data", "P0000001");

    /** The default user used as the referrer of the matched patient, and of the reference patient for public access. */
    private static final DocumentReference USER_1 = new DocumentReference("xwiki", "XWiki", "padams");

    /** The alternative user used as the referrer of the reference patient for matchable or private access. */
    private static final DocumentReference USER_2 = new DocumentReference("xwiki", "XWiki", "hmccoy");

    private static AccessType open;

    private static AccessType limited;

    private static AccessType priv;

    /** Mocked component manager. */
    private ComponentManager cm;

    @BeforeClass
    public static void setupAccessTypes()
    {
        open = mock(AccessType.class);
        when(open.isOpenAccess()).thenReturn(true);
        when(open.isLimitedAccess()).thenReturn(false);
        when(open.isPrivateAccess()).thenReturn(false);
        when(open.toString()).thenReturn("owner");
        when(open.getAccessLevel()).thenReturn(new OwnerAccessLevel());

        limited = mock(AccessType.class);
        when(limited.isOpenAccess()).thenReturn(false);
        when(limited.isLimitedAccess()).thenReturn(true);
        when(limited.isPrivateAccess()).thenReturn(false);
        when(limited.toString()).thenReturn("match");
        when(limited.getAccessLevel()).thenReturn(new MatchAccessLevel());

        priv = mock(AccessType.class);
        when(priv.isOpenAccess()).thenReturn(false);
        when(priv.isLimitedAccess()).thenReturn(false);
        when(priv.isPrivateAccess()).thenReturn(true);
        when(priv.toString()).thenReturn("none");
        when(priv.getAccessLevel()).thenReturn(new NoAccessLevel());
    }

    /** Missing match throws exception. */
    @Test(expected = IllegalArgumentException.class)
    public void testConstructorWithMissingMatch()
    {
        Patient mockReference = mock(Patient.class);
        new RestrictedPatientSimilarityView(null, mockReference, null);
    }

    /** Missing reference throws exception. */
    @Test(expected = IllegalArgumentException.class)
    public void testConstructorWithMissingReference()
    {
        Patient mockMatch = mock(Patient.class);
        new RestrictedPatientSimilarityView(mockMatch, null, null);
    }

    /** The document is disclosed for public patients. */
    @Test
    public void testGetAccess()
    {
        Patient mockMatch = mock(Patient.class);
        Patient mockReference = mock(Patient.class);

        PatientSimilarityView o = new RestrictedPatientSimilarityView(mockMatch, mockReference, limited);
        Assert.assertEquals("match", o.getAccess().getName());
    }

    /** The document is disclosed for public patients. */
    @Test
    public void testGetDocumentWithPublicAccess()
    {
        Patient mockMatch = mock(Patient.class);
        Patient mockReference = mock(Patient.class);
        when(mockMatch.getDocument()).thenReturn(PATIENT_1);
        when(mockMatch.getId()).thenReturn(PATIENT_1.getName());

        PatientSimilarityView o = new RestrictedPatientSimilarityView(mockMatch, mockReference, open);
        Assert.assertSame(PATIENT_1, o.getDocument());
    }

    /** The document is not disclosed for matchable patients. */
    @Test
    public void testGetDocumentWithMatchAccess()
    {
        Patient mockMatch = mock(Patient.class);
        Patient mockReference = mock(Patient.class);
        when(mockMatch.getDocument()).thenReturn(PATIENT_1);
        when(mockMatch.getId()).thenReturn(PATIENT_1.getName());

        PatientSimilarityView o = new RestrictedPatientSimilarityView(mockMatch, mockReference, limited);
        Assert.assertNull(o.getDocument());
    }

    /** The document is not disclosed for private patients. */
    @Test
    public void testGetDocumentWithNoAccess()
    {
        Patient mockMatch = mock(Patient.class);
        Patient mockReference = mock(Patient.class);
        when(mockMatch.getDocument()).thenReturn(PATIENT_1);
        when(mockMatch.getId()).thenReturn(PATIENT_1.getName());

        PatientSimilarityView o = new RestrictedPatientSimilarityView(mockMatch, mockReference, priv);
        Assert.assertNull(o.getDocument());
    }

    /** The reporter is disclosed for public patients. */
    @Test
    public void testGetReporterWithPublicAccess()
    {
        Patient mockMatch = mock(Patient.class);
        Patient mockReference = mock(Patient.class);
        when(mockMatch.getDocument()).thenReturn(PATIENT_1);
        when(mockMatch.getId()).thenReturn(PATIENT_1.getName());
        when(mockMatch.getReporter()).thenReturn(USER_1);

        PatientSimilarityView o = new RestrictedPatientSimilarityView(mockMatch, mockReference, open);
        Assert.assertSame(USER_1, o.getReporter());
    }

    /** The reporter is not disclosed for matchable patients. */
    @Test
    public void testGetReporterWithMatchAccess()
    {
        Patient mockMatch = mock(Patient.class);
        Patient mockReference = mock(Patient.class);
        when(mockMatch.getDocument()).thenReturn(PATIENT_1);
        when(mockMatch.getId()).thenReturn(PATIENT_1.getName());
        when(mockMatch.getReporter()).thenReturn(USER_1);

        PatientSimilarityView o = new RestrictedPatientSimilarityView(mockMatch, mockReference, limited);
        Assert.assertNull(o.getReporter());
    }

    /** The reporter is not disclosed for private patients. */
    @Test
    public void testGetReporterWithNoAccess()
    {
        Patient mockMatch = mock(Patient.class);
        Patient mockReference = mock(Patient.class);
        when(mockMatch.getDocument()).thenReturn(PATIENT_1);
        when(mockMatch.getId()).thenReturn(PATIENT_1.getName());
        when(mockMatch.getReporter()).thenReturn(USER_1);

        PatientSimilarityView o = new RestrictedPatientSimilarityView(mockMatch, mockReference, priv);
        Assert.assertNull(o.getReporter());
    }

    /** The reference is always disclosed. */
    @Test
    public void testGetReference()
    {
        Patient mockMatch = mock(Patient.class);
        Patient mockReference = mock(Patient.class);
        PatientSimilarityView o = new RestrictedPatientSimilarityView(mockMatch, mockReference, priv);
        Assert.assertSame(mockReference, o.getReference());
    }

    /** All the patient's phenotypes are disclosed for public patients. */
    @Test
    public void testGetPhenotypesWithPublicAccess() throws ComponentLookupException
    {
        setupComponents();

        Patient mockMatch = mock(Patient.class);
        Patient mockReference = mock(Patient.class);

        when(mockMatch.getDocument()).thenReturn(PATIENT_1);
        when(mockMatch.getId()).thenReturn(PATIENT_1.getName());

        Feature jhm = new MockFeature("HP:0001382", "Joint hypermobility", "phenotype", true);
        Feature od = new MockFeature("HP:0012165", "Oligodactyly", "phenotype", true);
        Feature cat = new MockFeature("HP:0000518", "Cataract", "phenotype", true);
        Feature id = new MockFeature("HP:0001249", "Intellectual disability", "phenotype", false);
        Feature mid = new MockFeature("HP:0001256", "Mild intellectual disability", "phenotype", true);
        Set<Feature> matchPhenotypes = new HashSet<Feature>();
        Set<Feature> referencePhenotypes = new HashSet<Feature>();
        Mockito.<Set<? extends Feature>>when(mockMatch.getFeatures()).thenReturn(matchPhenotypes);
        Mockito.<Set<? extends Feature>>when(mockReference.getFeatures()).thenReturn(referencePhenotypes);

        matchPhenotypes.add(jhm);
        matchPhenotypes.add(id);
        matchPhenotypes.add(od);
        referencePhenotypes.add(jhm);
        referencePhenotypes.add(mid);
        referencePhenotypes.add(cat);

        PatientSimilarityView o = new RestrictedPatientSimilarityView(mockMatch, mockReference, open);
        Set<? extends Feature> phenotypes = o.getFeatures();
        Assert.assertEquals(3, phenotypes.size());
        for (Feature p : phenotypes) {
            Assert.assertNotNull(p.getId());
        }
    }

    /** Only matching phenotypes are disclosed for matchable patients. */
    @Test
    public void testGetPhenotypesWithMatchAccess() throws ComponentLookupException
    {
        setupComponents();

        Patient mockMatch = mock(Patient.class);
        Patient mockReference = mock(Patient.class);

        when(mockMatch.getDocument()).thenReturn(PATIENT_1);
        when(mockMatch.getId()).thenReturn(PATIENT_1.getName());

        Feature jhm = new MockFeature("HP:0001382", "Joint hypermobility", "phenotype", true);
        Feature od = new MockFeature("HP:0012165", "Oligodactyly", "phenotype", true);
        Feature cat = new MockFeature("HP:0000518", "Cataract", "phenotype", true);
        Feature id = new MockFeature("HP:0001249", "Intellectual disability", "phenotype", false);
        Feature mid = new MockFeature("HP:0001256", "Mild intellectual disability", "phenotype", true);
        Set<Feature> matchPhenotypes = new HashSet<Feature>();
        Set<Feature> referencePhenotypes = new HashSet<Feature>();
        Mockito.<Set<? extends Feature>>when(mockMatch.getFeatures()).thenReturn(matchPhenotypes);
        Mockito.<Set<? extends Feature>>when(mockReference.getFeatures()).thenReturn(referencePhenotypes);

        matchPhenotypes.add(jhm);
        matchPhenotypes.add(id);
        matchPhenotypes.add(od);
        referencePhenotypes.add(jhm);
        referencePhenotypes.add(mid);
        referencePhenotypes.add(cat);

        PatientSimilarityView o = new RestrictedPatientSimilarityView(mockMatch, mockReference, limited);
        Set<? extends Feature> phenotypes = o.getFeatures();
        Assert.assertEquals(2, phenotypes.size());
        for (Feature p : phenotypes) {
            Assert.assertNull(p.getId());
        }
    }

    /** No phenotypes are disclosed for private patients. */
    @Test
    public void testGetPhenotypesWithPrivateAccess() throws ComponentLookupException
    {
        setupComponents();

        Patient mockMatch = mock(Patient.class);
        Patient mockReference = mock(Patient.class);

        when(mockMatch.getDocument()).thenReturn(PATIENT_1);
        when(mockMatch.getId()).thenReturn(PATIENT_1.getName());

        // Define phenotypes for later use
        Feature jhm = new MockFeature("HP:0001382", "Joint hypermobility", "phenotype", true);
        Feature od = new MockFeature("HP:0012165", "Oligodactyly", "phenotype", true);
        Feature cat = new MockFeature("HP:0000518", "Cataract", "phenotype", true);
        Feature id = new MockFeature("HP:0001249", "Intellectual disability", "phenotype", false);
        Feature mid = new MockFeature("HP:0001256", "Mild intellectual disability", "phenotype", true);
        Set<Feature> matchPhenotypes = new HashSet<Feature>();
        Set<Feature> referencePhenotypes = new HashSet<Feature>();
        Mockito.<Set<? extends Feature>>when(mockMatch.getFeatures()).thenReturn(matchPhenotypes);
        Mockito.<Set<? extends Feature>>when(mockReference.getFeatures()).thenReturn(referencePhenotypes);

        matchPhenotypes.add(jhm);
        matchPhenotypes.add(id);
        matchPhenotypes.add(od);
        referencePhenotypes.add(jhm);
        referencePhenotypes.add(mid);
        referencePhenotypes.add(cat);

        PatientSimilarityView o = new RestrictedPatientSimilarityView(mockMatch, mockReference, priv);
        Set<? extends Feature> phenotypes = o.getFeatures();
        Assert.assertTrue(phenotypes.isEmpty());
    }

    /** All the patient's diseases are disclosed for public patients. */
    @Test
    public void testGetDiseasesWithPublicAccess()
    {
        Patient mockMatch = mock(Patient.class);
        Patient mockReference = mock(Patient.class);

        when(mockMatch.getDocument()).thenReturn(PATIENT_1);
        when(mockMatch.getId()).thenReturn(PATIENT_1.getName());

        Set<Disorder> matchDiseases = new HashSet<Disorder>();
        matchDiseases.add(new MockDisorder("MIM:123", "Some disease"));
        matchDiseases.add(new MockDisorder("MIM:234", "Some other disease"));
        Mockito.<Set<? extends Disorder>>when(mockMatch.getDisorders()).thenReturn(matchDiseases);
        Set<Disorder> referenceDiseases = new HashSet<Disorder>();
        referenceDiseases.add(new MockDisorder("MIM:123", "Some disease"));
        referenceDiseases.add(new MockDisorder("MIM:345", "Some new disease"));
        Mockito.<Set<? extends Disorder>>when(mockReference.getDisorders()).thenReturn(referenceDiseases);

        PatientSimilarityView o = new RestrictedPatientSimilarityView(mockMatch, mockReference, open);
        Set<? extends Disorder> matchedDiseases = o.getDisorders();
        Assert.assertEquals(2, matchedDiseases.size());
    }

    /** Diseases aren't disclosed for matchable patients. */
    @Test
    public void testGetDiseasesWithMatchAccess()
    {
        Patient mockMatch = mock(Patient.class);
        Patient mockReference = mock(Patient.class);

        when(mockMatch.getDocument()).thenReturn(PATIENT_1);
        when(mockMatch.getId()).thenReturn(PATIENT_1.getName());

        Set<Disorder> matchDiseases = new HashSet<Disorder>();
        matchDiseases.add(new MockDisorder("MIM:123", "Some disease"));
        matchDiseases.add(new MockDisorder("MIM:234", "Some other disease"));
        Mockito.<Set<? extends Disorder>>when(mockMatch.getDisorders()).thenReturn(matchDiseases);
        Set<Disorder> referenceDiseases = new HashSet<Disorder>();
        referenceDiseases.add(new MockDisorder("MIM:123", "Some disease"));
        referenceDiseases.add(new MockDisorder("MIM:345", "Some new disease"));
        Mockito.<Set<? extends Disorder>>when(mockReference.getDisorders()).thenReturn(referenceDiseases);

        PatientSimilarityView o = new RestrictedPatientSimilarityView(mockMatch, mockReference, limited);
        Set<? extends Disorder> matchedDiseases = o.getDisorders();
        Assert.assertTrue(matchedDiseases.isEmpty());
    }

    /** No information is disclosed for private access. */
    @Test
    public void testToJSONWithPrivateAccess() throws ComponentLookupException
    {
        Patient mockMatch = mock(Patient.class);
        Patient mockReference = mock(Patient.class);

        when(mockMatch.getDocument()).thenReturn(PATIENT_1);
        when(mockMatch.getId()).thenReturn(PATIENT_1.getName());
        when(mockMatch.getReporter()).thenReturn(USER_1);
        when(mockReference.getReporter()).thenReturn(null);

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
        Feature mid = new MockFeature("HP:0001256", "Mild intellectual disability", "phenotype", referenceMeta, true);
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

        PatientSimilarityView o = new RestrictedPatientSimilarityView(mockMatch, mockReference, priv);

        // Nothing at all
        Assert.assertTrue(o.toJSON().isNullObject());
    }

    /** No "features" or "disorders" empty arrays are included when none are available. */
    @Test
    public void testToJSONWithNoPhenotypesOrDiseases() throws ComponentLookupException
    {
        Patient mockMatch = mock(Patient.class);
        Patient mockReference = mock(Patient.class);

        when(mockMatch.getDocument()).thenReturn(PATIENT_1);
        when(mockMatch.getId()).thenReturn(PATIENT_1.getName());
        when(mockMatch.getReporter()).thenReturn(USER_1);
        when(mockReference.getReporter()).thenReturn(USER_1);

        Feature jhm = new MockFeature("HP:0001382", "Joint hypermobility", "phenotype", true);
        Disorder d = new MockDisorder("MIM:234", "Some other disease");
        Mockito.<Set<? extends Feature>>when(mockReference.getFeatures()).thenReturn(Collections.singleton(jhm));
        Mockito.<Set<? extends Disorder>>when(mockReference.getDisorders()).thenReturn(Collections.singleton(d));

        PatientSimilarityView o = new RestrictedPatientSimilarityView(mockMatch, mockReference, open);

        JSONObject result = o.toJSON();
        Assert.assertFalse(result.has("features"));
        Assert.assertFalse(result.has("disorders"));
    }

    @Before
    private void setupComponents() throws ComponentLookupException
    {
        OntologyManager om = mock(OntologyManager.class);
        Logger logger = mock(Logger.class);
        Map<OntologyTerm, Double> termScores = new HashMap<OntologyTerm, Double>();

        RestrictedPatientSimilarityView.initializeStaticData(termScores, termScores, om, logger);
    }
}
