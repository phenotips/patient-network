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
import org.phenotips.data.IndexedPatientData;
import org.phenotips.data.Patient;
import org.phenotips.data.PatientData;
import org.phenotips.data.permissions.internal.access.NoAccessLevel;
import org.phenotips.data.permissions.internal.access.OwnerAccessLevel;
import org.phenotips.data.similarity.AccessType;
import org.phenotips.data.similarity.Exome;
import org.phenotips.data.similarity.ExomeManager;
import org.phenotips.data.similarity.PatientGenotypeManager;
import org.phenotips.data.similarity.PatientGenotypeSimilarityView;
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

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Provider;

import org.junit.After;
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
 * Tests for the {@link ExomiserExome} implementation based on the latest Exomiser-3.0.2 output file format
 * 
 * @version $Id$
 */
public class RestrictedGenotypeSimilarityViewTest
{
    /** The matched patient document. */
    private static final DocumentReference PATIENT_1 = new DocumentReference("xwiki", "data", "P0000001");

    /** The default user used as the referrer of the matched patient, and of the reference patient for public access. */
    private static final DocumentReference USER_1 = new DocumentReference("xwiki", "XWiki", "padams");

    /** Sample exome data for match patient. */
    private static final String EXOME_1 =
        "#CHROM\tPOS\tREF\tALT\tQUAL\tFILTER\tGENOTYPE\tCOVERAGE\tFUNCTIONAL_CLASS\tHGVS\tEXOMISER_GENE\tCADD(>0.483)\tPOLYPHEN(>0.956|>0.446)\tMUTATIONTASTER(>0.94)\tSIFT(<0.06)\tDBSNP_ID\tMAX_FREQUENCY\tDBSNP_FREQUENCY\tEVS_EA_FREQUENCY\tEVS_AA_FREQUENCY\tEXOMISER_VARIANT_SCORE\tEXOMISER_GENE_PHENO_SCORE\tEXOMISER_GENE_VARIANT_SCORE\tEXOMISER_GENE_COMBINED_SCORE\n" +
        "chr16\t30748691\tC\tT\t225.0\tPASS\t0/1\t40\tSTOPGAIN\tSRCAP:uc002dzg.1:exon29:c.6715C>T:p.R2239*\tSRCAP\t.\t.\t.\t.\t.\t0.0\t.\t.\t.\t0.95\t0.8603835\t0.95\t0.9876266\n" +
        "chr6\t32628660\tT\tC\t225.0\tPASS\t0/1\t94\tSPLICING\tHLA-DQB1:uc031snx.1:exon5:c.773-1A>G\tHLA-DQB1\t.\t.\t.\t.\t.\t0.0\t.\t.\t.\t0.9\t0.612518\t1.0\t0.9057237\n";

    /** Sample exome data for reference patient. */
    private static final String EXOME_2 =
        "#CHROM\tPOS\tREF\tALT\tQUAL\tFILTER\tGENOTYPE\tCOVERAGE\tFUNCTIONAL_CLASS\tHGVS\tEXOMISER_GENE\tCADD(>0.483)\tPOLYPHEN(>0.956|>0.446)\tMUTATIONTASTER(>0.94)\tSIFT(<0.06)\tDBSNP_ID\tMAX_FREQUENCY\tDBSNP_FREQUENCY\tEVS_EA_FREQUENCY\tEVS_AA_FREQUENCY\tEXOMISER_VARIANT_SCORE\tEXOMISER_GENE_PHENO_SCORE\tEXOMISER_GENE_VARIANT_SCORE\tEXOMISER_GENE_COMBINED_SCORE\n" +
        "chr16\t30748691\tC\tT\t225.0\tPASS\t0/1\t40\tSTOPGAIN\tSRCAP:uc002dzg.1:exon29:c.6715C>T:p.R2239*\tSRCAP\t.\t.\t.\t.\t.\t0.0\t.\t.\t.\t0.95\t0.8603835\t0.95\t0.9876266\n" +
        "chr1\t120611963\tG\tC\t76.0\tPASS\t0/1\t42\tMISSENSE\tNOTCH2:uc001eil.3:exon1:c.56C>G:p.C19Q\tNOTCH2\t6.292\t.\t.\t0.0\t.\t0.0\t.\t.\t.\t0.4\t0.7029731\t1.0\t0.9609373\n" + 
        "chr1\t120611964\tG\tC\t76.0\tPASS\t0/1\t42\tMISSENSE\tNOTCH2:uc001eil.3:exon1:c.57C>G:p.C19W\tNOTCH2\t6.292\t.\t.\t0.0\t.\t0.0\t.\t.\t.\t1.0\t0.7029731\t1.0\t0.9609373\n";
    
    private static AccessType open;

    private static AccessType limited;

    private static AccessType priv;

    /** The mocked genotype manager component, initialized once for all tests. */
    private static PatientGenotypeManager genotypeManager;

    /** The mocked exome manager component, initialized once for all tests. */
    private static ExomeManager exomeManager;

    /** The mocked match patient, initialized before each test. */
    private Patient mockMatch;

    /** The mocked reference, initialized before each test. */
    private Patient mockReference;

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
    
    private void setMatchPatientExome() {
        Exome exome = null;
        try {
            exome = new ExomiserExome(new StringReader(EXOME_1));
        } catch (IOException e) {
            Assert.fail("Exomiser file parsing resulted in IOException");
        }
        
        when(exomeManager.getExome(mockMatch)).thenReturn(exome);
    }

    private void setReferencePatientExome() {
        Exome exome = null;
        try {
            exome = new ExomiserExome(new StringReader(EXOME_2));
        } catch (IOException e) {
            Assert.fail("Exomiser file parsing resulted in IOException");
        }
        
        when(exomeManager.getExome(mockReference)).thenReturn(exome);
    }

    private void linkGenetics()
    {
        DefaultPatientGenotype genotype;

        genotype = new DefaultPatientGenotype(mockReference);
        when(genotypeManager.getGenotype(mockReference)).thenReturn(genotype);

        genotype = new DefaultPatientGenotype(mockMatch);
        when(genotypeManager.getGenotype(mockMatch)).thenReturn(genotype);
    }

    /** Candidate genes intersected properly. */
    @Test
    public void testCandidateGeneMatch()
    {
        Collection<String> matchGenes = new ArrayList<String>();
        matchGenes.add("SRCAP");
        matchGenes.add("TTN");
        setPatientCandidateGenes(mockMatch, matchGenes);

        Collection<String> refGenes = new ArrayList<String>();
        refGenes.add("Corf27");
        refGenes.add("HEXA");
        refGenes.add("SRCAP");
        setPatientCandidateGenes(mockReference, refGenes);

        linkGenetics();
        PatientGenotypeSimilarityView o = new RestrictedGenotypeSimilarityView(mockMatch, mockReference, open);

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
        Collection<String> matchGenes = new ArrayList<String>();
        matchGenes.add("SRCAP");
        matchGenes.add("TTN");
        setPatientCandidateGenes(mockMatch, matchGenes);

        Collection<String> refGenes = new ArrayList<String>();
        refGenes.add("Corf27");
        refGenes.add("HEXA");
        setPatientCandidateGenes(mockReference, refGenes);

        linkGenetics();
        PatientGenotypeSimilarityView o = new RestrictedGenotypeSimilarityView(mockMatch, mockReference, open);

        Set<String> genes = o.getGenes();
        Assert.assertTrue(genes.isEmpty());

        JSONArray results = o.toJSON();
        Assert.assertTrue(results.isEmpty());
    }

    /** Candidate genes intersected properly. */
    @Test
    public void testCandidateGeneIgnoreBlanks()
    {
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

        linkGenetics();
        PatientGenotypeSimilarityView o = new RestrictedGenotypeSimilarityView(mockMatch, mockReference, open);

        Set<String> genes = o.getGenes();
        Assert.assertTrue(genes.isEmpty());

        JSONArray results = o.toJSON();
        Assert.assertTrue(results.isEmpty());
    }

    /** Candidate genes intersected properly. */
    @Test
    public void testCandidateGeneIgnoreWhitespace()
    {
        Collection<String> matchGenes = new ArrayList<String>();
        matchGenes.add("SRCAP");
        setPatientCandidateGenes(mockMatch, matchGenes);

        Collection<String> refGenes = new ArrayList<String>();
        refGenes.add("  SRCAP  ");
        setPatientCandidateGenes(mockReference, refGenes);

        linkGenetics();
        PatientGenotypeSimilarityView o = new RestrictedGenotypeSimilarityView(mockMatch, mockReference, open);

        Set<String> genes = o.getGenes();
        Assert.assertEquals(1, genes.size());

        JSONArray results = o.toJSON();
        Assert.assertTrue(!results.isEmpty());
    }

    /** Candidate genes work if match is missing genotype data. */
    @Test
    public void testCandidateGeneEmptyMatch()
    {
        Collection<String> matchGenes = new ArrayList<String>();
        setPatientCandidateGenes(mockMatch, matchGenes);

        Collection<String> refGenes = new ArrayList<String>();
        refGenes.add("Corf27");
        refGenes.add("HEXA");
        setPatientCandidateGenes(mockReference, refGenes);

        linkGenetics();
        PatientGenotypeSimilarityView o = new RestrictedGenotypeSimilarityView(mockMatch, mockReference, open);

        Set<String> genes = o.getGenes();
        Assert.assertTrue(genes.isEmpty());

        JSONArray results = o.toJSON();
        Assert.assertTrue(results == null);
    }

    /** Candidate genes work if ref is missing genotype data. */
    @Test
    public void testCandidateGeneEmptyRef()
    {
        Collection<String> matchGenes = new ArrayList<String>();
        matchGenes.add("SRCAP");
        matchGenes.add("TTN");
        setPatientCandidateGenes(mockMatch, matchGenes);

        Collection<String> refGenes = new ArrayList<String>();
        setPatientCandidateGenes(mockReference, refGenes);

        linkGenetics();
        PatientGenotypeSimilarityView o = new RestrictedGenotypeSimilarityView(mockMatch, mockReference, open);

        Set<String> genes = o.getGenes();
        Assert.assertTrue(genes.isEmpty());

        JSONArray results = o.toJSON();
        Assert.assertTrue(results == null);
    }

    /** Candidate genes affect score of existing variants in gene. */
    @Test
    public void testCandidateGeneVariantInGene()
    {
        Collection<String> matchGenes = new ArrayList<String>();
        matchGenes.add("SRCAP");
        matchGenes.add("TTN");
        setPatientCandidateGenes(mockMatch, matchGenes);
        
        // Include exome with variant in SRCAP in match patient
        setMatchPatientExome();
        
        Collection<String> refGenes = new ArrayList<String>();
        refGenes.add("Corf27");
        refGenes.add("HEXA");
        refGenes.add("SRCAP");
        setPatientCandidateGenes(mockReference, refGenes);

        linkGenetics();
        PatientGenotypeSimilarityView o = new RestrictedGenotypeSimilarityView(mockMatch, mockReference, open);

        // SRCAP (candidate + exome vs. candidate) should match
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

        // Ensure match shows underlying variant details
        JSONObject matchVars = top.getJSONObject("match");
        Assert.assertEquals(1, matchVars.size());
        vars = matchVars.getJSONArray("variants");
        Assert.assertEquals(1, vars.size());
        v = vars.getJSONObject(0);
        Assert.assertTrue(v.getDouble("score") > 0.9);
        // Full variant details
        Assert.assertTrue(v.size() > 4);
        Assert.assertFalse(v.getString("chrom").isEmpty());
    }

    /** Candidate genes work even if no existing variants in gene. */
    @Test
    public void testCandidateGeneNoVariantsInGene()
    {
        Collection<String> matchGenes = new ArrayList<String>();
        matchGenes.add("NOTCH2");
        setPatientCandidateGenes(mockMatch, matchGenes);

        // Include exome with variant in SRCAP in match patient
        setMatchPatientExome();
        
        Collection<String> refGenes = new ArrayList<String>();
        setPatientCandidateGenes(mockReference, refGenes);

        // Include exome with variant in NOTCH2, SRCAP in reference patient
        setReferencePatientExome();
        
        linkGenetics();
        PatientGenotypeSimilarityView o = new RestrictedGenotypeSimilarityView(mockMatch, mockReference, open);

        // Both NOTCH2 (candidate vs. exome) and SRCAP (exome vs. exome) should match.
        Set<String> genes = o.getGenes();
        Assert.assertEquals(2, genes.size());
        Assert.assertTrue(genes.contains("NOTCH2"));
        Assert.assertTrue(genes.contains("SRCAP"));

        JSONArray results = o.toJSON();
        Assert.assertEquals(2, results.size());
    }

    // Only do once, because components are stored statically within various classes,
    // which causes problems with changing genetics mocks
    @BeforeClass
    @SuppressWarnings("unchecked")
    public static void setupComponents() throws ComponentLookupException, CacheException
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

        Cache<Exome> cache = mock(Cache.class);
        doReturn(cache).when(cacheFactory).newCache(Matchers.any(CacheConfiguration.class));
        doReturn(null).when(cache).get(Matchers.anyString());

        // Wire up mocked genetics
        genotypeManager = mock(PatientGenotypeManager.class);
        when(componentManager.getInstance(PatientGenotypeManager.class)).thenReturn(genotypeManager);
        
        exomeManager = mock(ExomeManager.class);
        when(componentManager.getInstance(ExomeManager.class)).thenReturn(exomeManager);
    }

    @Before
    public void setupMockPatients()
    {
        mockMatch = getBasicMockMatch();
        mockReference = getBasicMockReference();
    }

    @After
    public void tearDownComponents()
    {
        mockMatch = null;
        mockReference = null;
    }
}
