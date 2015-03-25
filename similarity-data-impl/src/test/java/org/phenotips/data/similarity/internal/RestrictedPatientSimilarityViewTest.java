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
import org.phenotips.data.IndexedPatientData;
import org.phenotips.data.Patient;
import org.phenotips.data.PatientData;
import org.phenotips.data.permissions.internal.access.NoAccessLevel;
import org.phenotips.data.permissions.internal.access.OwnerAccessLevel;
import org.phenotips.data.similarity.AccessType;
import org.phenotips.data.similarity.PatientGenotypeManager;
import org.phenotips.data.similarity.PatientSimilarityView;
import org.phenotips.data.similarity.internal.mocks.MockDisorder;
import org.phenotips.data.similarity.internal.mocks.MockFeature;
import org.phenotips.data.similarity.internal.mocks.MockFeatureMetadatum;
import org.phenotips.data.similarity.internal.mocks.MockOntologyTerm;
import org.phenotips.data.similarity.permissions.internal.MatchAccessLevel;
import org.phenotips.messaging.Connection;
import org.phenotips.messaging.ConnectionManager;
import org.phenotips.ontology.OntologyManager;
import org.phenotips.ontology.OntologyTerm;

import org.xwiki.cache.Cache;
import org.xwiki.cache.CacheException;
import org.xwiki.cache.CacheFactory;
import org.xwiki.cache.CacheManager;
import org.xwiki.cache.config.CacheConfiguration;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.component.util.ReflectionUtils;
import org.xwiki.model.reference.DocumentReference;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Provider;

import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Matchers;
import org.mockito.Mockito;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import static org.mockito.Mockito.doReturn;
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

    private static AccessType open;

    private static AccessType limited;

    private static AccessType priv;

    /** The mocked genotype manager component, initialized before each test. */
    private static PatientGenotypeManager genotypeManager;

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

    /** Get empty match patient. */
    private Patient getEmptyMockMatch()
    {
        Patient mockPatient = mock(Patient.class);
        when(mockPatient.getDocument()).thenReturn(PATIENT_1);
        when(mockPatient.getId()).thenReturn(PATIENT_1.getName());
        when(mockPatient.getReporter()).thenReturn(USER_1);

        return mockPatient;
    }

    /** The document is disclosed for public patients. */
    @Test
    public void testGetDocumentWithPublicAccess()
    {
        Patient mockMatch = getEmptyMockMatch();
        Patient mockReference = mock(Patient.class);

        PatientSimilarityView o = new RestrictedPatientSimilarityView(mockMatch, mockReference, open);
        Assert.assertSame(PATIENT_1, o.getDocument());
    }

    /** The document is not disclosed for matchable patients. */
    @Test
    public void testGetDocumentWithMatchAccess()
    {
        Patient mockMatch = getEmptyMockMatch();
        Patient mockReference = mock(Patient.class);

        PatientSimilarityView o = new RestrictedPatientSimilarityView(mockMatch, mockReference, limited);
        Assert.assertNull(o.getDocument());
    }

    /** The document is not disclosed for private patients. */
    @Test
    public void testGetDocumentWithNoAccess()
    {
        Patient mockMatch = getEmptyMockMatch();
        Patient mockReference = mock(Patient.class);

        PatientSimilarityView o = new RestrictedPatientSimilarityView(mockMatch, mockReference, priv);
        Assert.assertNull(o.getDocument());
    }

    /** The reporter is disclosed for public patients. */
    @Test
    public void testGetReporterWithPublicAccess()
    {
        Patient mockMatch = getEmptyMockMatch();
        Patient mockReference = mock(Patient.class);

        PatientSimilarityView o = new RestrictedPatientSimilarityView(mockMatch, mockReference, open);
        Assert.assertSame(USER_1, o.getReporter());
    }

    /** The reporter is not disclosed for matchable patients. */
    @Test
    public void testGetReporterWithMatchAccess()
    {
        Patient mockMatch = getEmptyMockMatch();
        Patient mockReference = mock(Patient.class);

        PatientSimilarityView o = new RestrictedPatientSimilarityView(mockMatch, mockReference, limited);
        Assert.assertNull(o.getReporter());
    }

    /** The reporter is not disclosed for private patients. */
    @Test
    public void testGetReporterWithNoAccess()
    {
        Patient mockMatch = getEmptyMockMatch();
        Patient mockReference = mock(Patient.class);

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

    /** Get simple match patient. */
    private Patient getBasicMockMatch()
    {
        Patient mockPatient = getEmptyMockMatch();

        Map<String, FeatureMetadatum> metadata = new HashMap<String, FeatureMetadatum>();
        metadata.put("age_of_onset", new MockFeatureMetadatum("HP:0003577", "Congenital onset", "age_of_onset"));
        metadata.put("speed_of_onset", new MockFeatureMetadatum("HP:0011010", "Chronic", "speed_of_onset"));
        metadata.put("pace", new MockFeatureMetadatum("HP:0003677", "Slow", "pace"));

        Feature jhm = new MockFeature("HP:0001382", "Joint hypermobility", "phenotype", true);
        Feature od = new MockFeature("HP:0012165", "Oligodactyly", "phenotype", true);
        Feature cat = new MockFeature("HP:0000518", "Cataract", "phenotype", true);
        Feature id = new MockFeature("HP:0001249", "Intellectual disability", "phenotype", metadata, false);

        Set<Feature> phenotypes = new HashSet<Feature>();
        Mockito.<Set<? extends Feature>>when(mockPatient.getFeatures()).thenReturn(phenotypes);

        phenotypes.add(jhm);
        phenotypes.add(cat);
        phenotypes.add(id);
        phenotypes.add(od);

        Set<Disorder> diseases = new HashSet<Disorder>();
        diseases.add(new MockDisorder("MIM:123", "Some disease"));
        diseases.add(new MockDisorder("MIM:234", "Some other disease"));
        Mockito.<Set<? extends Disorder>>when(mockPatient.getDisorders()).thenReturn(diseases);

        when(mockPatient.getData("genes")).thenReturn(null);

        return mockPatient;
    }

    /** Get simple reference patient. */
    private Patient getBasicMockReference()
    {
        Patient mockPatient = mock(Patient.class);
        when(mockPatient.getReporter()).thenReturn(null);

        Map<String, FeatureMetadatum> metadata = new HashMap<String, FeatureMetadatum>();
        metadata.put("age_of_onset", new MockFeatureMetadatum("HP:0003577", "Congenital onset", "age_of_onset"));
        metadata.put("speed_of_onset", new MockFeatureMetadatum("HP:0011009", "Acute", "speed_of_onset"));
        metadata.put("death", new MockFeatureMetadatum("HP:0003826", "Stillbirth", "death"));

        Feature jhm = new MockFeature("HP:0001382", "Joint hypermobility", "phenotype", true);
        Feature cat = new MockFeature("HP:0000518", "Cataract", "phenotype", true);
        Feature mid = new MockFeature("HP:0001256", "Mild intellectual disability", "phenotype", metadata, true);

        Set<Feature> phenotypes = new HashSet<Feature>();
        Mockito.<Set<? extends Feature>>when(mockPatient.getFeatures()).thenReturn(phenotypes);

        phenotypes.add(jhm);
        phenotypes.add(mid);
        phenotypes.add(cat);

        Set<Disorder> diseases = new HashSet<Disorder>();
        diseases.add(new MockDisorder("MIM:123", "Some disease"));
        diseases.add(new MockDisorder("MIM:345", "Some new disease"));
        Mockito.<Set<? extends Disorder>>when(mockPatient.getDisorders()).thenReturn(diseases);

        when(mockPatient.getData("genes")).thenReturn(null);

        return mockPatient;
    }

    /** All the patient's phenotypes are disclosed for public patients. */
    @Test
    public void testGetPhenotypesWithPublicAccess() throws ComponentLookupException
    {
        Patient mockMatch = getBasicMockMatch();
        Patient mockReference = getBasicMockReference();

        PatientSimilarityView o = new RestrictedPatientSimilarityView(mockMatch, mockReference, open);
        Set<? extends Feature> phenotypes = o.getFeatures();
        Assert.assertEquals(4, phenotypes.size());
        for (Feature p : phenotypes) {
            Assert.assertNotNull(p.getId());
        }
    }

    /** No phenotypes are directly disclosed for matchable patients. */
    @Test
    public void testGetPhenotypesWithMatchAccess() throws ComponentLookupException
    {
        Patient mockMatch = getBasicMockMatch();
        Patient mockReference = getBasicMockReference();

        PatientSimilarityView o = new RestrictedPatientSimilarityView(mockMatch, mockReference, limited);
        Set<? extends Feature> phenotypes = o.getFeatures();
        Assert.assertEquals(0, phenotypes.size());
    }

    /** No phenotypes are disclosed for private patients. */
    @Test
    public void testGetPhenotypesWithPrivateAccess() throws ComponentLookupException
    {
        Patient mockMatch = getBasicMockMatch();
        Patient mockReference = getBasicMockReference();

        PatientSimilarityView o = new RestrictedPatientSimilarityView(mockMatch, mockReference, priv);
        Set<? extends Feature> phenotypes = o.getFeatures();
        Assert.assertTrue(phenotypes.isEmpty());
    }

    /** All the patient's diseases are disclosed for public patients. */
    @Test
    public void testGetDiseasesWithPublicAccess()
    {
        Patient mockMatch = getBasicMockMatch();
        Patient mockReference = getBasicMockReference();

        PatientSimilarityView o = new RestrictedPatientSimilarityView(mockMatch, mockReference, open);
        Set<? extends Disorder> matchedDiseases = o.getDisorders();
        Assert.assertEquals(2, matchedDiseases.size());
    }

    /** Diseases aren't disclosed for matchable patients. */
    @Test
    public void testGetDiseasesWithMatchAccess()
    {
        Patient mockMatch = getBasicMockMatch();
        Patient mockReference = getBasicMockReference();

        PatientSimilarityView o = new RestrictedPatientSimilarityView(mockMatch, mockReference, limited);
        Set<? extends Disorder> matchedDiseases = o.getDisorders();
        Assert.assertTrue(matchedDiseases.isEmpty());
    }

    /**
     * Set candidate genes for mock patient.
     *
     * @param mockPatient
     * @param geneNames
     */
    private void setPatientCandidateGenes(Patient mockPatient, Collection<String> geneNames)
    {
        List<Map<String, String>> fakeGenes = new ArrayList<Map<String, String>>();

        if (geneNames != null) {
            for (String gene : geneNames) {
                Map<String, String> fakeGene = new HashMap<String, String>();
                fakeGene.put("gene", gene);
                fakeGenes.add(fakeGene);
            }
        }

        PatientData<Map<String, String>> fakeGeneData =
            new IndexedPatientData<Map<String, String>>("genes", fakeGenes);

        doReturn(fakeGeneData).when(mockPatient).getData("genes");
        
        DefaultPatientGenotype genotype = new DefaultPatientGenotype(mockPatient);
        when(genotypeManager.getGenotype(mockPatient)).thenReturn(genotype);
    }

    /** Matching candidate genes boosts score. */
    @Test
    public void testCandidateGeneMatching()
    {
        Patient mockMatch = getBasicMockMatch();
        Patient mockReference = getBasicMockReference();

        PatientSimilarityView view1 = new RestrictedPatientSimilarityView(mockMatch, mockReference, limited);
        double scoreBefore = view1.getScore();

        Collection<String> matchGenes = new ArrayList<String>();
        matchGenes.add("Another gene");
        matchGenes.add("Matching gene");
        setPatientCandidateGenes(mockMatch, matchGenes);
        setPatientCandidateGenes(mockReference, Collections.singleton("Matching gene"));
        PatientSimilarityView view2 = new RestrictedPatientSimilarityView(mockMatch, mockReference, limited);

        double scoreAfter = view2.getScore();
        Assert.assertTrue(scoreAfter > scoreBefore + 0.01);
    }

    /** Non-matching candidate genes doesn't affect score. */
    @Test
    public void testCandidateGeneNonMatching()
    {
        Patient mockMatch = getBasicMockMatch();
        Patient mockReference = getBasicMockReference();

        PatientSimilarityView view1 = new RestrictedPatientSimilarityView(mockMatch, mockReference, limited);
        double scoreBefore = view1.getScore();

        setPatientCandidateGenes(mockMatch, Collections.singleton("Gene A"));
        setPatientCandidateGenes(mockReference, Collections.singleton("Gene B"));
        PatientSimilarityView view2 = new RestrictedPatientSimilarityView(mockMatch, mockReference, limited);

        double scoreAfter = view2.getScore();
        Assert.assertTrue(Math.abs(scoreAfter - scoreBefore) < 0.00001);
    }

    /** No information is disclosed for private access. */
    @Test
    public void testToJSONWithPrivateAccess() throws ComponentLookupException
    {
        Patient mockMatch = getBasicMockMatch();
        Patient mockReference = getBasicMockReference();

        PatientSimilarityView o = new RestrictedPatientSimilarityView(mockMatch, mockReference, priv);

        // Nothing at all
        Assert.assertTrue(o.toJSON().isNullObject());
    }

    /** Direct phenotype information is disclosed for public access. */
    @Test
    public void testToJSONWithPublicAccess() throws ComponentLookupException
    {
        Patient mockMatch = getBasicMockMatch();
        Patient mockReference = getBasicMockReference();

        PatientSimilarityView o = new RestrictedPatientSimilarityView(mockMatch, mockReference, open);

        JSONObject result = o.toJSON();
        JSONArray clusters = result.getJSONArray("featureMatches");
        Assert.assertTrue(clusters.size() >= 2);
        for (int i = 0; i < clusters.size(); i++) {
            JSONObject cluster = clusters.getJSONObject(i);
            JSONArray match = cluster.getJSONArray("match");
            for (int j = 0; i < match.size(); i++) {
                String id = match.getString(j);
                Assert.assertEquals("HP:", id.substring(0, 3));
            }
            JSONArray reference = cluster.getJSONArray("reference");
            for (int j = 0; i < reference.size(); i++) {
                String id = reference.getString(j);
                Assert.assertEquals("HP:", id.substring(0, 3));
            }
        }
    }

    /** No direct phenotype information is disclosed for match access. */
    @Test
    public void testToJSONWithMatchAccess() throws ComponentLookupException
    {
        Patient mockMatch = getBasicMockMatch();
        Patient mockReference = getBasicMockReference();

        PatientSimilarityView o = new RestrictedPatientSimilarityView(mockMatch, mockReference, limited);

        JSONObject result = o.toJSON();
        JSONArray clusters = result.getJSONArray("featureMatches");
        Assert.assertTrue(clusters.size() >= 2);
        for (int i = 0; i < clusters.size(); i++) {
            JSONObject cluster = clusters.getJSONObject(i);
            JSONArray match = cluster.getJSONArray("match");
            for (int j = 0; i < match.size(); i++) {
                String id = match.getString(j);
                Assert.assertEquals("", id);
            }
            JSONArray reference = cluster.getJSONArray("reference");
            for (int j = 0; i < reference.size(); i++) {
                String id = reference.getString(j);
                Assert.assertEquals("HP:", id.substring(0, 3));
            }
        }
    }

    /** No "features" or "disorders" empty arrays are included when none are available. */
    @Test
    public void testToJSONWithNoPhenotypesOrDiseases() throws ComponentLookupException
    {
        Patient mockMatch = getEmptyMockMatch();
        Patient mockReference = getBasicMockReference();

        when(mockMatch.getDocument()).thenReturn(PATIENT_1);
        when(mockMatch.getId()).thenReturn(PATIENT_1.getName());
        when(mockMatch.getReporter()).thenReturn(USER_1);

        PatientSimilarityView o = new RestrictedPatientSimilarityView(mockMatch, mockReference, open);

        JSONObject result = o.toJSON();
        Assert.assertFalse(result.has("features"));
        Assert.assertFalse(result.has("disorders"));
    }

    @Before
    @SuppressWarnings("unchecked")
    public void setupComponents() throws ComponentLookupException, CacheException
    {
        ComponentManagerRegistry registry = new ComponentManagerRegistry();
        ComponentManager componentManager = mock(ComponentManager.class);
        Provider<ComponentManager> mockProvider = mock(Provider.class);
        // This is a bit fragile, let's hope the field name doesn't change
        ReflectionUtils.setFieldValue(registry, "cmProvider", mockProvider);
        when(mockProvider.get()).thenReturn(componentManager);
        when(ComponentManagerRegistry.getContextComponentManager()).thenReturn(componentManager);

        CacheManager cacheManager = mock(CacheManager.class);
        when(componentManager.getInstance(CacheManager.class)).thenReturn(cacheManager);

        CacheFactory cacheFactory = mock(CacheFactory.class);
        when(cacheManager.getLocalCacheFactory()).thenReturn(cacheFactory);

        Cache<PatientSimilarityView> cache = mock(Cache.class);
        doReturn(cache).when(cacheFactory).newCache(Matchers.any(CacheConfiguration.class));
        doReturn(null).when(cache).get(Matchers.anyString());

        // Mock up the contact token
        ConnectionManager connManager = mock(ConnectionManager.class);
        when(componentManager.getInstance(ConnectionManager.class)).thenReturn(connManager);
        Connection c = mock(Connection.class);
        when(connManager.getConnection(Matchers.any(PatientSimilarityView.class))).thenReturn(c);
        when(c.getId()).thenReturn(Long.valueOf(42));
        
        // Wire up mocked genetics
        genotypeManager = mock(PatientGenotypeManager.class);
        when(componentManager.getInstance(PatientGenotypeManager.class)).thenReturn(genotypeManager);

        // Setup the ontology manager
        OntologyManager ontologyManager = mock(OntologyManager.class);
        Map<OntologyTerm, Double> termICs = new HashMap<OntologyTerm, Double>();
        Set<OntologyTerm> ancestors = new HashSet<OntologyTerm>();

        OntologyTerm all = new MockOntologyTerm("HP:0000001", Collections.<OntologyTerm>emptySet(),
            Collections.<OntologyTerm>emptySet());
        ancestors.add(all);
        OntologyTerm phenotypes =
            new MockOntologyTerm("HP:0000118", Collections.singleton(all), new HashSet<OntologyTerm>(ancestors));
        ancestors.add(phenotypes);
        termICs.put(phenotypes, 0.000001);
        OntologyTerm abnormalNS =
            new MockOntologyTerm("HP:0000707", Collections.singleton(phenotypes), new HashSet<OntologyTerm>(ancestors));
        ancestors.add(abnormalNS);
        termICs.put(abnormalNS, 0.00001);
        OntologyTerm abnormalCNS =
            new MockOntologyTerm("HP:0002011", Collections.singleton(abnormalNS), new HashSet<OntologyTerm>(ancestors));
        ancestors.add(abnormalCNS);
        termICs.put(abnormalCNS, 0.0001);
        OntologyTerm abnormalHMF =
            new MockOntologyTerm("HP:0011446", Collections.singleton(abnormalCNS), new HashSet<OntologyTerm>(ancestors));
        ancestors.add(abnormalHMF);
        termICs.put(abnormalHMF, 0.001);
        OntologyTerm cognImp =
            new MockOntologyTerm("HP:0100543", Collections.singleton(abnormalHMF), new HashSet<OntologyTerm>(ancestors));
        ancestors.add(cognImp);
        termICs.put(cognImp, 0.005);
        OntologyTerm intDis =
            new MockOntologyTerm("HP:0001249", Collections.singleton(cognImp), new HashSet<OntologyTerm>(ancestors));
        ancestors.add(intDis);
        termICs.put(intDis, 0.005);
        OntologyTerm mildIntDis =
            new MockOntologyTerm("HP:0001256", Collections.singleton(intDis), new HashSet<OntologyTerm>(ancestors));
        ancestors.add(mildIntDis);
        termICs.put(intDis, 0.01);
        for (OntologyTerm term : ancestors) {
            when(ontologyManager.resolveTerm(term.getId())).thenReturn(term);
        }

        ancestors.clear();
        ancestors.add(all);
        ancestors.add(phenotypes);
        OntologyTerm abnormalSkelS =
            new MockOntologyTerm("HP:0000924", Collections.singleton(phenotypes), new HashSet<OntologyTerm>(ancestors));
        ancestors.add(abnormalSkelS);
        termICs.put(abnormalSkelS, 0.00001);
        OntologyTerm abnormalSkelM =
            new MockOntologyTerm("HP:0011842", Collections.singleton(abnormalSkelS), new HashSet<OntologyTerm>(
                ancestors));
        ancestors.add(abnormalSkelM);
        termICs.put(abnormalSkelM, 0.0001);
        OntologyTerm abnormalJointMorph =
            new MockOntologyTerm("HP:0001367", Collections.singleton(abnormalSkelM), new HashSet<OntologyTerm>(
                ancestors));
        ancestors.add(abnormalJointMorph);
        termICs.put(abnormalJointMorph, 0.001);
        OntologyTerm abnormalJointMob =
            new MockOntologyTerm("HP:0011729", Collections.singleton(abnormalJointMorph), new HashSet<OntologyTerm>(
                ancestors));
        ancestors.add(abnormalJointMob);
        termICs.put(abnormalJointMob, 0.005);
        OntologyTerm jointHyperm =
            new MockOntologyTerm("HP:0001382", Collections.singleton(abnormalJointMob), new HashSet<OntologyTerm>(
                ancestors));
        ancestors.add(jointHyperm);
        termICs.put(jointHyperm, 0.005);
        for (OntologyTerm term : ancestors) {
            when(ontologyManager.resolveTerm(term.getId())).thenReturn(term);
        }

        DefaultPatientSimilarityView.initializeStaticData(termICs, ontologyManager);
    }
}
