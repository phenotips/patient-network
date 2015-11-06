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
 * Tests for the "restricted" {@link PatientGenotypeSimilarityView} implementation,
 * {@link RestrictedPatientGenotypeSimilarityView}.
 *
 * @version $Id$
 */
public class RestrictedPatientGenotypeSimilarityViewTest
{
    /** The reference patient document. */
    private static final DocumentReference PATIENT_1 = new DocumentReference("xwiki", "data", "P0000001");

    /** The matched patient document. */
    private static final DocumentReference PATIENT_2 = new DocumentReference("xwiki", "data", "P0000002");

    /** The default user used as the referrer of the matched patient, and of the reference patient for public access. */
    private static final DocumentReference USER_1 = new DocumentReference("xwiki", "XWiki", "padams");

    /** Sample exome data for match patient. */
    private static final String EXOME_1 =
        "#CHROM\tPOS\tREF\tALT\tQUAL\tFILTER\tGENOTYPE\tCOVERAGE\tFUNCTIONAL_CLASS\tHGVS\tEXOMISER_GENE\tCADD(>0.483)\tPOLYPHEN(>0.956|>0.446)\tMUTATIONTASTER(>0.94)\tSIFT(<0.06)\tDBSNP_ID\tMAX_FREQUENCY\tDBSNP_FREQUENCY\tEVS_EA_FREQUENCY\tEVS_AA_FREQUENCY\tEXOMISER_VARIANT_SCORE\tEXOMISER_GENE_PHENO_SCORE\tEXOMISER_GENE_VARIANT_SCORE\tEXOMISER_GENE_COMBINED_SCORE\n"
            +
            "chr16\t30748691\tC\tT\t225.0\tPASS\t0/1\t40\tSTOPGAIN\tSRCAP:uc002dzg.1:exon29:c.6715C>T:p.R2239*\tSRCAP\t.\t.\t.\t.\t.\t0.0\t.\t.\t.\t0.95\t0.8603835\t0.95\t0.9876266\n"
            +
            "chr6\t32628660\tT\tC\t225.0\tPASS\t0/1\t94\tSPLICING\tHLA-DQB1:uc031snx.1:exon5:c.773-1A>G\tHLA-DQB1\t.\t.\t.\t.\t.\t0.0\t.\t.\t.\t0.9\t0.612518\t1.0\t0.9057237\n"
            +
            "chr6\t32628661\tT\tC\t225.0\tPASS\t0/1\t94\tSPLICING\tHLA-DQB1:uc031snx.1:exon5:c.773-2A>G\tHLA-DQB1\t.\t.\t.\t.\t.\t0.0\t.\t.\t.\t0.5\t0.612518\t1.0\t0.9057237\n";

    /** Sample exome data for reference patient. */
    private static final String EXOME_2 =
        "#CHROM\tPOS\tREF\tALT\tQUAL\tFILTER\tGENOTYPE\tCOVERAGE\tFUNCTIONAL_CLASS\tHGVS\tEXOMISER_GENE\tCADD(>0.483)\tPOLYPHEN(>0.956|>0.446)\tMUTATIONTASTER(>0.94)\tSIFT(<0.06)\tDBSNP_ID\tMAX_FREQUENCY\tDBSNP_FREQUENCY\tEVS_EA_FREQUENCY\tEVS_AA_FREQUENCY\tEXOMISER_VARIANT_SCORE\tEXOMISER_GENE_PHENO_SCORE\tEXOMISER_GENE_VARIANT_SCORE\tEXOMISER_GENE_COMBINED_SCORE\n"
            +
            "chr16\t30748691\tC\tT\t225.0\tPASS\t0/1\t40\tSTOPGAIN\tSRCAP:uc002dzg.1:exon29:c.6715C>T:p.R2239*\tSRCAP\t.\t.\t.\t.\t.\t0.0\t.\t.\t.\t0.95\t0.8603835\t0.95\t0.9876266\n"
            +
            "chr1\t120611963\tG\tC\t76.0\tPASS\t0/1\t42\tMISSENSE\tNOTCH2:uc001eil.3:exon1:c.56C>G:p.C19Q\tNOTCH2\t6.292\t.\t.\t0.0\t.\t0.0\t.\t.\t.\t0.4\t0.7029731\t1.0\t0.9609373\n"
            +
            "chr1\t120611964\tG\tC\t76.0\tPASS\t0/1\t42\tMISSENSE\tNOTCH2:uc001eil.3:exon1:c.57C>G:p.C19W\tNOTCH2\t6.292\t.\t.\t0.0\t.\t0.0\t.\t.\t.\t1.0\t0.7029731\t1.0\t0.9609373\n";

    private static AccessType open;

    private static AccessType limited;

    private static AccessType priv;

    /** The mocked exome manager component, initialized once for all tests. */
    private static ExomeManager exomeManager;

    /** The mocked match patient, initialized before each test. */
    private Patient mockMatch;

    /** The mocked reference, initialized before each test. */
    private Patient mockReference;

    private enum VariantDetailLevel
    {
        FULL,
        LIMITED,
        NONE
    }

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
        when(mockPatient.getDocument()).thenReturn(PATIENT_2);
        when(mockPatient.getId()).thenReturn(PATIENT_2.getName());
        when(mockPatient.getReporter()).thenReturn(USER_1);
        return mockPatient;
    }

    /** Get simple reference patient. */
    private Patient getBasicMockReference()
    {
        Patient mockPatient = mock(Patient.class);
        when(mockPatient.getDocument()).thenReturn(PATIENT_1);
        when(mockPatient.getId()).thenReturn(PATIENT_1.getName());
        when(mockPatient.getReporter()).thenReturn(null);
        return mockPatient;
    }

    /**
     * Set candidate genes and exome data for mock patient.
     *
     * @param mockPatient
     * @param geneNames
     * @param exomeData
     */
    private void setupPatientGenetics(Patient mockPatient, Collection<String> geneNames, String exomeData)
    {
        // Mock candidate genes
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

        // Mock exome data
        if (exomeData != null) {
            Exome exome = null;
            try {
                exome = new ExomiserExome(new StringReader(exomeData));
            } catch (IOException e) {
                Assert.fail("Exomiser file parsing resulted in IOException");
            }

            when(exomeManager.getExome(mockPatient)).thenReturn(exome);
        }
    }

    private void setupPatientGenetics(Patient mockPatient, Collection<String> geneNames)
    {
        setupPatientGenetics(mockPatient, geneNames, null);
    }

    private void assertNoMatch(PatientGenotypeSimilarityView view)
    {
        Assert.assertTrue(view.getGenes().isEmpty());
        Assert.assertEquals(0, view.getScore(), 0.0001);
    }

    private void assertNoGenetics(PatientGenotypeSimilarityView view)
    {
        assertNoMatch(view);
        Assert.assertTrue(view.toJSON() == null);
    }

    private void assertVariantDetailLevel(VariantDetailLevel detailLevel, JSONObject result, int nVariants)
    {
        if (detailLevel.equals(VariantDetailLevel.NONE)) {
            Assert.assertTrue(result.isEmpty());
        } else {
            Assert.assertEquals(1, result.size());
            JSONArray variants = result.getJSONArray("variants");
            // Ensure expected number of variants
            Assert.assertEquals(nVariants, variants.size());

            for (int i = 0; i < nVariants; i++) {
                JSONObject v = variants.getJSONObject(i);
                Assert.assertNotNull(v);

                if (detailLevel.equals(VariantDetailLevel.FULL)) {
                    // Ensure full variant details displayed
                    Assert.assertTrue(v.size() > 4);

                    Assert.assertTrue(v.getDouble("score") > 0);
                    Assert.assertFalse(v.getString("chrom").isEmpty());
                    Assert.assertTrue(v.getInt("position") > 0);
                    Assert.assertFalse(v.getString("ref").isEmpty());
                    Assert.assertFalse(v.getString("alt").isEmpty());
                    Assert.assertFalse(v.getString("type").isEmpty());
                } else if (detailLevel.equals(VariantDetailLevel.LIMITED)) {
                    // Ensure limited variant details displayed
                    Assert.assertEquals(2, v.size());
                    Assert.assertTrue(v.getDouble("score") > 0);
                    Assert.assertFalse(v.getString("type").isEmpty());
                }
            }
        }
    }

    /** Candidate genes intersected properly. */
    @Test
    public void testCandidateGeneMatch()
    {
        Collection<String> matchGenes = new ArrayList<String>();
        matchGenes.add("SRCAP");
        matchGenes.add("TTN");
        setupPatientGenetics(mockMatch, matchGenes);

        Collection<String> refGenes = new ArrayList<String>();
        refGenes.add("Corf27");
        refGenes.add("HEXA");
        refGenes.add("SRCAP");
        setupPatientGenetics(mockReference, refGenes);

        PatientGenotypeSimilarityView o =
            new RestrictedPatientGenotypeSimilarityView(this.mockMatch, this.mockReference, open);

        Set<String> genes = o.getGenes();
        Assert.assertEquals(1, genes.size());
        Assert.assertTrue(genes.contains("SRCAP"));

        JSONArray results = o.toJSON();
        Assert.assertEquals(1, results.size());

        JSONObject top = results.getJSONObject(0);
        Assert.assertTrue(top.getString("gene").equals("SRCAP"));
        Assert.assertTrue(top.getDouble("score") > 0.7);

        assertVariantDetailLevel(VariantDetailLevel.NONE, top.getJSONObject("reference"), 0);

        // Ensure match only shows score
        assertVariantDetailLevel(VariantDetailLevel.NONE, top.getJSONObject("match"), 0);
    }

    /** Candidate genes intersected properly. */
    @Test
    public void testCandidateGeneNoMatch()
    {
        Collection<String> matchGenes = new ArrayList<String>();
        matchGenes.add("SRCAP");
        matchGenes.add("TTN");
        setupPatientGenetics(mockMatch, matchGenes);

        Collection<String> refGenes = new ArrayList<String>();
        refGenes.add("Corf27");
        refGenes.add("HEXA");
        setupPatientGenetics(mockReference, refGenes);

        PatientGenotypeSimilarityView view =
            new RestrictedPatientGenotypeSimilarityView(this.mockMatch, this.mockReference, open);

        assertNoMatch(view);
    }

    /** Candidate genes intersected properly. */
    @Test
    public void testCandidateGeneNoMatchOnBlanks()
    {
        Collection<String> matchGenes = new ArrayList<String>();
        matchGenes.add("SRCAP");
        matchGenes.add("   ");
        matchGenes.add("");
        setupPatientGenetics(mockMatch, matchGenes);

        Collection<String> refGenes = new ArrayList<String>();
        refGenes.add("");
        refGenes.add("HEXA");
        refGenes.add("  ");
        setupPatientGenetics(mockReference, refGenes);

        PatientGenotypeSimilarityView view =
            new RestrictedPatientGenotypeSimilarityView(this.mockMatch, this.mockReference, open);

        assertNoMatch(view);
    }

    /** Candidate genes intersected properly. */
    @Test
    public void testCandidateGeneMatchIgnoreWhitespace()
    {
        Collection<String> matchGenes = new ArrayList<String>();
        matchGenes.add("SRCAP");
        setupPatientGenetics(mockMatch, matchGenes);

        Collection<String> refGenes = new ArrayList<String>();
        refGenes.add("  SRCAP  ");
        setupPatientGenetics(mockReference, refGenes);

        PatientGenotypeSimilarityView view =
            new RestrictedPatientGenotypeSimilarityView(this.mockMatch, this.mockReference, open);

        Set<String> genes = view.getGenes();
        Assert.assertEquals(1, genes.size());

        JSONArray results = view.toJSON();
        Assert.assertTrue(!results.isEmpty());
    }

    /** Candidate genes work if match is missing genotype data. */
    @Test
    public void testCandidateGeneWithoutMatchGenetics()
    {
        setupPatientGenetics(mockMatch, null);

        Collection<String> refGenes = new ArrayList<String>();
        refGenes.add("Corf27");
        refGenes.add("HEXA");
        setupPatientGenetics(mockReference, refGenes);

        PatientGenotypeSimilarityView view =
            new RestrictedPatientGenotypeSimilarityView(this.mockMatch, this.mockReference, open);

        assertNoGenetics(view);
    }

    /** Candidate genes work if ref is missing genotype data. */
    @Test
    public void testCandidateGeneWithoutRefGenetics()
    {
        Collection<String> matchGenes = new ArrayList<String>();
        matchGenes.add("SRCAP");
        matchGenes.add("TTN");
        setupPatientGenetics(mockMatch, matchGenes);

        setupPatientGenetics(mockReference, null);

        PatientGenotypeSimilarityView view =
            new RestrictedPatientGenotypeSimilarityView(this.mockMatch, this.mockReference, open);

        assertNoGenetics(view);
    }

    /** Candidate genes affect score of existing variants in gene. */
    @Test
    public void testVariantInCandidateGene()
    {
        Collection<String> matchGenes = new ArrayList<String>();
        matchGenes.add("SRCAP");
        matchGenes.add("TTN");
        // Include exome with variant in SRCAP in match patient
        setupPatientGenetics(mockMatch, matchGenes, EXOME_1);

        Collection<String> refGenes = new ArrayList<String>();
        refGenes.add("Corf27");
        refGenes.add("HEXA");
        refGenes.add("SRCAP");
        // No exome for reference patient
        setupPatientGenetics(mockReference, refGenes, null);

        PatientGenotypeSimilarityView view =
            new RestrictedPatientGenotypeSimilarityView(this.mockMatch, this.mockReference, open);

        // SRCAP (candidate + exome vs. candidate) should match
        Set<String> genes = view.getGenes();
        Assert.assertEquals(1, genes.size());
        Assert.assertTrue(genes.contains("SRCAP"));

        JSONArray results = view.toJSON();
        Assert.assertEquals(1, results.size());

        JSONObject top = results.getJSONObject(0);
        Assert.assertTrue(top.getString("gene").equals("SRCAP"));
        double score = top.getDouble("score");
        Assert.assertTrue(String.format("Unexpected score: %.4f", score), score > 0.7);

        // Ensure match shows underlying exome variant details
        assertVariantDetailLevel(VariantDetailLevel.FULL, top.getJSONObject("match"), 1);

        // Ensure reference only shows score (candidate gene)
        assertVariantDetailLevel(VariantDetailLevel.NONE, top.getJSONObject("reference"), 0);
    }

    /** Candidate genes work even if no existing variants in gene. */
    @Test
    public void testNoVariantsInCandidateGene()
    {
        Collection<String> matchGenes = new ArrayList<String>();
        matchGenes.add("NOTCH2");
        // Include exome with variant in SRCAP in match patient
        setupPatientGenetics(mockMatch, matchGenes, EXOME_1);

        // Include exome with variant in NOTCH2, SRCAP in reference patient
        setupPatientGenetics(mockReference, null, EXOME_2);

        PatientGenotypeSimilarityView view =
            new RestrictedPatientGenotypeSimilarityView(this.mockMatch, this.mockReference, open);

        // Both NOTCH2 (candidate vs. exome) and SRCAP (exome vs. exome) should match.
        Set<String> genes = view.getGenes();
        Assert.assertEquals(2, genes.size());
        Assert.assertTrue(genes.contains("NOTCH2"));
        Assert.assertTrue(genes.contains("SRCAP"));

        JSONArray results = view.toJSON();
        Assert.assertEquals(2, results.size());

        JSONObject top = results.getJSONObject(0);
        Assert.assertTrue(top.getString("gene").equals("NOTCH2"));
        Assert.assertTrue(top.getDouble("score") > 0.7);

        assertVariantDetailLevel(VariantDetailLevel.NONE, top.getJSONObject("match"), 0);

        // Ensure reference shows underlying exome variant details
        assertVariantDetailLevel(VariantDetailLevel.FULL, top.getJSONObject("reference"), 2);

        top = results.getJSONObject(1);
        Assert.assertTrue(top.getString("gene").equals("SRCAP"));
        Assert.assertTrue(top.getDouble("score") < 0.7);

        // Ensure match shows candidate gene level
        assertVariantDetailLevel(VariantDetailLevel.FULL, top.getJSONObject("match"), 1);

        // Ensure reference shows underlying exome variant details
        assertVariantDetailLevel(VariantDetailLevel.FULL, top.getJSONObject("reference"), 1);
    }

    /** Only score shown for variant when matchable. */
    @Test
    public void testMultipleVariantsMatchVisibility()
    {
        // Include exome with variants in HLA-DQB1 in match patient
        setupPatientGenetics(mockMatch, null, EXOME_1);

        Collection<String> refGenes = new ArrayList<String>();
        refGenes.add("HLA-DQB1");
        setupPatientGenetics(mockReference, refGenes);

        PatientGenotypeSimilarityView view =
            new RestrictedPatientGenotypeSimilarityView(this.mockMatch, this.mockReference, limited);

        // HLA-DQB1 (candidate vs. exome) should match.
        Set<String> genes = view.getGenes();
        Assert.assertEquals(1, genes.size());
        Assert.assertTrue(genes.contains("HLA-DQB1"));

        Collection<String> candidateGenes = view.getCandidateGenes();
        Assert.assertEquals(1, candidateGenes.size());
        Assert.assertTrue(candidateGenes.contains("HLA-DQB1"));

        JSONArray results = view.toJSON();
        Assert.assertEquals(1, results.size());

        JSONObject top = results.getJSONObject(0);
        Assert.assertTrue(top.getString("gene").equals("HLA-DQB1"));
        Assert.assertTrue(top.getDouble("score") > 0.5);

        // Ensure match has limited view for variants
        assertVariantDetailLevel(VariantDetailLevel.LIMITED, top.getJSONObject("match"), 2);

        // Ensure reference shows candidate gene score
        assertVariantDetailLevel(VariantDetailLevel.NONE, top.getJSONObject("reference"), 0);
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
        exomeManager = mock(ExomeManager.class);
        when(componentManager.getInstance(ExomeManager.class, "exomiser")).thenReturn(exomeManager);

        // Use a real GenotypeManager
        PatientGenotypeManager genotypeManager = new DefaultPatientGenotypeManager();
        when(componentManager.getInstance(PatientGenotypeManager.class)).thenReturn(genotypeManager);
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
