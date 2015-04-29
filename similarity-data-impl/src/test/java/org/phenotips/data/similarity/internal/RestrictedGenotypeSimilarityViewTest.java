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
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.phenotips.data.similarity.internal;

import org.phenotips.components.ComponentManagerRegistry;
import org.phenotips.data.IndexedPatientData;
import org.phenotips.data.Patient;
import org.phenotips.data.PatientData;
import org.phenotips.data.permissions.internal.access.NoAccessLevel;
import org.phenotips.data.permissions.internal.access.OwnerAccessLevel;
import org.phenotips.data.similarity.AccessType;
import org.phenotips.data.similarity.Genotype;
import org.phenotips.data.similarity.GenotypeSimilarityView;
import org.phenotips.data.similarity.permissions.internal.MatchAccessLevel;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Provider;

import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Matchers;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for the {@link ExomiserGenotype} implementation based on the latest Exomiser-3.0.2 output file format
 * 
 * @version $Id$
 */
public class RestrictedGenotypeSimilarityViewTest
{
    /** The matched patient document. */
    private static final DocumentReference PATIENT_1 = new DocumentReference("xwiki", "data", "P0000001");

    /** The default user used as the referrer of the matched patient, and of the reference patient for public access. */
    private static final DocumentReference USER_1 = new DocumentReference("xwiki", "XWiki", "padams");

    private static AccessType open;

    private static AccessType limited;

    private static AccessType priv;

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

    /** Get basic match patient. */
    private Patient getBasicMockMatch()
    {
        Patient mockPatient = mock(Patient.class);
        when(mockPatient.getDocument()).thenReturn(PATIENT_1);
        when(mockPatient.getId()).thenReturn(PATIENT_1.getName());
        when(mockPatient.getReporter()).thenReturn(USER_1);
        return mockPatient;
    }

    /** Get simple reference patient. */
    private Patient getBasicMockReference()
    {
        Patient mockPatient = mock(Patient.class);
        when(mockPatient.getReporter()).thenReturn(null);
        return mockPatient;
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
    }

    /** Candidate genes intersected properly. */
    @Test
    public void testCandidateGeneMatch()
    {
        Patient mockMatch = getBasicMockMatch();
        Patient mockReference = getBasicMockReference();

        Collection<String> matchGenes = new ArrayList<String>();
        matchGenes.add("SRCAP");
        matchGenes.add("TTN");
        setPatientCandidateGenes(mockMatch, matchGenes);

        Collection<String> refGenes = new ArrayList<String>();
        refGenes.add("Corf27");
        refGenes.add("HEXA");
        refGenes.add("SRCAP");
        setPatientCandidateGenes(mockReference, refGenes);

        GenotypeSimilarityView o = new RestrictedGenotypeSimilarityView(mockMatch, mockReference, open);

        Set<String> genes = o.getGenes();
        Assert.assertEquals(1, genes.size());
        Assert.assertTrue(genes.contains("SRCAP"));

        JSONArray results = o.toJSON();
        Assert.assertEquals(1, results.size());

        JSONObject top = results.getJSONObject(0);
        JSONArray vars;
        JSONObject v;
        Assert.assertTrue(top.getString("gene").equals("SRCAP"));
        Assert.assertTrue(top.getDouble("score") > 0.9);

        // Ensure reference only shows score
        JSONObject refVars = top.getJSONObject("reference");
        Assert.assertEquals(1, refVars.size());
        vars = refVars.getJSONArray("variants");
        Assert.assertEquals(1, vars.size());
        v = vars.getJSONObject(0);
        Assert.assertTrue(v.getDouble("score") > 0.9);
        Assert.assertEquals(1, v.size());

        // Ensure match only shows score
        JSONObject matchVars = top.getJSONObject("match");
        Assert.assertEquals(1, matchVars.size());
        vars = matchVars.getJSONArray("variants");
        Assert.assertEquals(1, vars.size());
        v = vars.getJSONObject(0);
        Assert.assertTrue(v.getDouble("score") > 0.9);
        Assert.assertEquals(1, v.size());
    }

    /** Candidate genes intersected properly. */
    @Test
    public void testCandidateGeneNoMatch()
    {
        Patient mockMatch = getBasicMockMatch();
        Patient mockReference = getBasicMockReference();

        Collection<String> matchGenes = new ArrayList<String>();
        matchGenes.add("SRCAP");
        matchGenes.add("TTN");
        setPatientCandidateGenes(mockMatch, matchGenes);

        Collection<String> refGenes = new ArrayList<String>();
        refGenes.add("Corf27");
        refGenes.add("HEXA");
        setPatientCandidateGenes(mockReference, refGenes);

        GenotypeSimilarityView o = new RestrictedGenotypeSimilarityView(mockMatch, mockReference, open);

        Set<String> genes = o.getGenes();
        Assert.assertTrue(genes.isEmpty());

        JSONArray results = o.toJSON();
        Assert.assertTrue(results.isEmpty());
    }

    /** Candidate genes intersected properly. */
    @Test
    public void testCandidateGeneIgnoreBlanks()
    {
        Patient mockMatch = getBasicMockMatch();
        Patient mockReference = getBasicMockReference();

        Collection<String> matchGenes = new ArrayList<String>();
        matchGenes.add("SRCAP");
        matchGenes.add("   ");
        matchGenes.add("");
        setPatientCandidateGenes(mockMatch, matchGenes);

        Collection<String> refGenes = new ArrayList<String>();
        refGenes.add("");
        refGenes.add("HEXA");
        refGenes.add("  ");
        setPatientCandidateGenes(mockReference, refGenes);

        GenotypeSimilarityView o = new RestrictedGenotypeSimilarityView(mockMatch, mockReference, open);

        Set<String> genes = o.getGenes();
        Assert.assertTrue(genes.isEmpty());

        JSONArray results = o.toJSON();
        Assert.assertTrue(results.isEmpty());
    }

    /** Candidate genes intersected properly. */
    @Test
    public void testCandidateGeneIgnoreWhitespace()
    {
        Patient mockMatch = getBasicMockMatch();
        Patient mockReference = getBasicMockReference();

        Collection<String> matchGenes = new ArrayList<String>();
        matchGenes.add("SRCAP");
        setPatientCandidateGenes(mockMatch, matchGenes);

        Collection<String> refGenes = new ArrayList<String>();
        refGenes.add("  SRCAP  ");
        setPatientCandidateGenes(mockReference, refGenes);

        GenotypeSimilarityView o = new RestrictedGenotypeSimilarityView(mockMatch, mockReference, open);

        Set<String> genes = o.getGenes();
        Assert.assertEquals(1, genes.size());

        JSONArray results = o.toJSON();
        Assert.assertTrue(!results.isEmpty());
    }

    /** Candidate genes work if match is missing genotype data. */
    @Test
    public void testCandidateGeneEmptyMatch()
    {
        Patient mockMatch = getBasicMockMatch();
        Patient mockReference = getBasicMockReference();

        Collection<String> matchGenes = new ArrayList<String>();
        setPatientCandidateGenes(mockMatch, matchGenes);

        Collection<String> refGenes = new ArrayList<String>();
        refGenes.add("Corf27");
        refGenes.add("HEXA");
        setPatientCandidateGenes(mockReference, refGenes);

        GenotypeSimilarityView o = new RestrictedGenotypeSimilarityView(mockMatch, mockReference, open);

        Set<String> genes = o.getGenes();
        Assert.assertTrue(genes.isEmpty());

        JSONArray results = o.toJSON();
        Assert.assertTrue(results == null);
    }

    /** Candidate genes work if ref is missing genotype data. */
    @Test
    public void testCandidateGeneEmptyRef()
    {
        Patient mockMatch = getBasicMockMatch();
        Patient mockReference = getBasicMockReference();

        Collection<String> matchGenes = new ArrayList<String>();
        matchGenes.add("SRCAP");
        matchGenes.add("TTN");
        setPatientCandidateGenes(mockMatch, matchGenes);

        Collection<String> refGenes = new ArrayList<String>();
        setPatientCandidateGenes(mockReference, refGenes);

        GenotypeSimilarityView o = new RestrictedGenotypeSimilarityView(mockMatch, mockReference, open);

        Set<String> genes = o.getGenes();
        Assert.assertTrue(genes.isEmpty());

        JSONArray results = o.toJSON();
        Assert.assertTrue(results == null);
    }

    @Before
    @SuppressWarnings("unchecked")
    public void setupComponents() throws ComponentLookupException, CacheException
    {
        ComponentManager componentManager = mock(ComponentManager.class);
        Provider<ComponentManager> mockProvider = mock(Provider.class);
        // This is a bit fragile, let's hope the field name doesn't change
        ReflectionUtils.setFieldValue(new ComponentManagerRegistry(), "cmProvider", mockProvider);
        when(mockProvider.get()).thenReturn(componentManager);

        CacheManager cacheManager = mock(CacheManager.class);
        when(componentManager.getInstance(CacheManager.class)).thenReturn(cacheManager);

        CacheFactory cacheFactory = mock(CacheFactory.class);
        when(cacheManager.getLocalCacheFactory()).thenReturn(cacheFactory);

        Cache<Genotype> cache = mock(Cache.class);
        doReturn(cache).when(cacheFactory).newCache(Matchers.any(CacheConfiguration.class));
        doReturn(null).when(cache).get(Matchers.anyString());
    }
}
