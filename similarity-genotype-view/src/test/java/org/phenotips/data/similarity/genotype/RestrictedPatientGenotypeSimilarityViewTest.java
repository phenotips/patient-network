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
package org.phenotips.data.similarity.genotype;

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

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Matchers;

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
        "#CHROM\tPOS\tREF\tALT\tQUAL\tFILTER\tGENOTYPE\tCOVERAGE\tFUNCTIONAL_CLASS\tHGVS\tEXOMISER_GENE\tCADD(>0.483)"
        + "\tPOLYPHEN(>0.956|>0.446)\tMUTATIONTASTER(>0.94)\tSIFT(<0.06)\tDBSNP_ID\tMAX_FREQUENCY\tDBSNP_FREQUENCY\tE"
        + "VS_EA_FREQUENCY\tEVS_AA_FREQUENCY\tEXOMISER_VARIANT_SCORE\tEXOMISER_GENE_PHENO_SCORE\tEXOMISER_GENE_VARIAN"
        + "T_SCORE\tEXOMISER_GENE_COMBINED_SCORE\nchr16\t30748691\tC\tT\t225.0\tPASS\t0/1\t40\tSTOPGAIN\tSRCAP:uc002d"
        + "zg.1:exon29:c.6715C>T:p.R2239*\tSRCAP\t.\t.\t.\t.\t.\t0.0\t.\t.\t.\t0.95\t0.8603835\t0.95\t0.9876266\nchr6"
        + "\t32628660\tT\tC\t225.0\tPASS\t0/1\t94\tSPLICING\tHLA-DQB1:uc031snx.1:exon5:c.773-1A>G\tHLA-DQB1\t.\t.\t"
        + ".\t.\t.\t0.0\t.\t.\t.\t0.9\t0.612518\t1.0\t0.9057237\nchr6\t32628661\tT\tC\t225.0\tPASS\t0/1\t94\tSPLICI"
        + "NG\tHLA-DQB1:uc031snx.1:exon5:c.773-2A>G\tHLA-DQB1\t.\t.\t.\t.\t.\t0.0\t.\t.\t.\t0.5\t0.612518\t1.0\t0.9"
        + "057237\n";

    /** Sample exome data for reference patient. */
    private static final String EXOME_2 = "#CHROM\tPOS\tREF\tALT\tQUAL\tFILTER\tGENOTYPE\tCOVERAGE\tFUN"
        + "CTIONAL_CLASS\tHGVS\tEXOMISER_GENE\tCADD(>0.483)\tPOLYPHEN(>0.956|>0.446)\tMUTATIONTA"
        + "STER(>0.94)\tSIFT(<0.06)\tDBSNP_ID\tMAX_FREQUENCY\tDBSNP_FREQUENCY\tEVS"
        + "_EA_FREQUENCY\tEVS_AA_FREQUENCY\tEXOMISER_VARIANT_SCORE\tEXOMISER_GENE_PHENO_SCORE\tEXOMISER_GENE_VARIANT_S"
        + "CORE\tEXOMISER_GENE_COMBINED_SCORE\nchr16\t30748691\tC\tT\t225.0\tPASS\t0/1\t40\tSTOPGAIN\tSRCAP:uc002dzg.1"
        + ":exon29:c.6715C>T:p.R2239*\tSRCAP\t.\t.\t.\t.\t.\t0.0\t.\t.\t.\t0.95\t0.8603835\t0.95\t0.9876266\nchr1\t120"
        + "611963\tG\tC\t76.0\tPASS\t0/1\t42\tMISSENSE\tNOTCH2:uc001eil.3:exon1:c.56C>G:p.C19Q\tNOTCH2\t6.292\t.\t.\t0"
        + ".0\t.\t0.0\t.\t.\t.\t0.4\t0.7029731\t1.0\t0.9609373\nchr1\t120611964\tG\tC\t76.0\tPASS\t0/1\t42\tMISSENSE\t"
        + "NOTCH2:uc001eil.3:exon1:c.57C>G:p.C19W\tNOTCH2\t6.292\t.\t.\t0.0\t.\t0.0\t.\t.\t.\t1.0\t0.7029731\t1.0\t0.9"
        + "609373\n";

    private static final String SRCAP = "SRCAP";

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
        when(mockPatient.getDocumentReference()).thenReturn(PATIENT_2);
        when(mockPatient.getId()).thenReturn(PATIENT_2.getName());
        when(mockPatient.getReporter()).thenReturn(USER_1);
        return mockPatient;
    }

    /** Get simple reference patient. */
    private Patient getBasicMockReference()
    {
        Patient mockPatient = mock(Patient.class);
        when(mockPatient.getDocumentReference()).thenReturn(PATIENT_1);
        when(mockPatient.getId()).thenReturn(PATIENT_1.getName());
        when(mockPatient.getReporter()).thenReturn(null);
        return mockPatient;
    }

    private Map<String, String> mockManualGene(String geneName, String status)
    {
        Map<String, String> gene = new HashMap<>();
        gene.put("gene", geneName);
        gene.put("status", status);
        return gene;
    }

    private Map<String, String> mockManualVariant(String geneName, String interpretation)
    {
        Map<String, String> variant = new HashMap<>();
        variant.put("genesymbol", geneName);
        variant.put("interpretation", interpretation);
        return variant;

    }

    private List<Map<String, String>> mockCandidateGenes(Collection<String> geneNames)
    {
        List<Map<String, String>> genes = new ArrayList<>();
        for (String geneName : geneNames) {
            genes.add(mockManualGene(geneName, "candidate"));
        }
        return genes;
    }

    /**
     * Set candidate genes data for mock patient.
     *
     * @param mockPatient the mock patient
     * @param genes candidate gene data
     */
    private void setPatientGenes(Patient mockPatient, List<Map<String, String>> genes)
    {
        PatientData<Map<String, String>> geneData =
            new IndexedPatientData<>("genes", genes);
        doReturn(geneData).when(mockPatient).getData("genes");
    }

    /**
     * Set candidate variant data for mock patient.
     *
     * @param mockPatient the mock patient
     * @param variants manual variant data
     */
    private void setPatientVariants(Patient mockPatient, List<Map<String, String>> variants)
    {
        PatientData<Map<String, String>> variantData =
            new IndexedPatientData<>("variants", variants);
        doReturn(variantData).when(this.mockMatch).getData("variants");
    }

    /**
     * Set exome data for mock patient.
     *
     * @param mockPatient the mock patient
     * @param exome exome data
     */
    private void setPatientExome(Patient mockPatient, String exomeData)
    {
        // Mock exome data
        Exome exome = null;
        try {
            exome = new ExomiserExome(new StringReader(exomeData));
        } catch (IOException e) {
            Assert.fail("Exomiser file parsing resulted in IOException");
        }

        when(exomeManager.getExome(mockPatient)).thenReturn(exome);
    }

    private void assertNoMatch(PatientGenotypeSimilarityView view)
    {
        Assert.assertTrue(view.getMatchingGenes().isEmpty());
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
            Assert.assertEquals(0, result.length());
        } else {
            Assert.assertEquals(1, result.length());
            JSONArray variants = result.getJSONArray("variants");
            // Ensure expected number of variants
            Assert.assertEquals(nVariants, variants.length());

            for (int i = 0; i < nVariants; i++) {
                JSONObject v = variants.getJSONObject(i);
                Assert.assertNotNull(v);

                if (detailLevel.equals(VariantDetailLevel.FULL)) {
                    // Ensure full variant details displayed
                    Assert.assertTrue(v.length() > 4);

                    Assert.assertTrue(v.getDouble("score") > 0);
                    Assert.assertFalse(v.getString("chrom").isEmpty());
                    Assert.assertTrue(v.getInt("position") > 0);
                    Assert.assertFalse(v.getString("ref").isEmpty());
                    Assert.assertFalse(v.getString("alt").isEmpty());
                    Assert.assertFalse(v.getString("type").isEmpty());
                } else if (detailLevel.equals(VariantDetailLevel.LIMITED)) {
                    // Ensure limited variant details displayed
                    Assert.assertEquals(2, v.length());
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
        Collection<String> matchGenes = new ArrayList<>();
        matchGenes.add(SRCAP);
        matchGenes.add("TTN");
        setPatientGenes(this.mockMatch, mockCandidateGenes(matchGenes));

        Collection<String> refGenes = new ArrayList<>();
        refGenes.add("Corf27");
        refGenes.add("HEXA");
        refGenes.add(SRCAP);
        setPatientGenes(this.mockReference, mockCandidateGenes(refGenes));

        PatientGenotypeSimilarityView o =
            new RestrictedPatientGenotypeSimilarityView(this.mockMatch, this.mockReference, open);

        Set<String> genes = o.getMatchingGenes();
        Assert.assertEquals(1, genes.size());
        Assert.assertTrue(genes.contains(SRCAP));

        JSONArray results = o.toJSON();
        Assert.assertEquals(1, results.length());

        JSONObject top = results.getJSONObject(0);
        Assert.assertTrue(top.getString("gene").equals(SRCAP));
        Assert.assertTrue(top.getDouble("score") > 0.7);

        assertVariantDetailLevel(VariantDetailLevel.NONE, top.getJSONObject("reference"), 0);

        // Ensure match only shows score
        assertVariantDetailLevel(VariantDetailLevel.NONE, top.getJSONObject("match"), 0);
    }

    /** Solved genes match candidate genes. */
    @Test
    public void testSolvedGenesMatch()
    {
        List<Map<String, String>> matchGenes = new ArrayList<>();
        matchGenes.add(mockManualGene(SRCAP, "solved"));
        setPatientGenes(this.mockMatch, matchGenes);

        List<Map<String, String>> refGenes = new ArrayList<>();
        refGenes.add(mockManualGene(SRCAP, "candidate"));
        setPatientGenes(this.mockReference, refGenes);

        PatientGenotypeSimilarityView view =
            new RestrictedPatientGenotypeSimilarityView(this.mockMatch, this.mockReference, open);

        Set<String> genes = view.getMatchingGenes();
        Assert.assertEquals(1, genes.size());
        Assert.assertTrue(genes.contains(SRCAP));

        JSONArray results = view.toJSON();
        Assert.assertEquals(1, results.length());
    }

    /** Rejected genes do not match candidate genes. */
    @Test
    public void testRejectedGenesDoNotMatch()
    {
        List<Map<String, String>> matchGenes = new ArrayList<>();
        matchGenes.add(mockManualGene(SRCAP, "rejected"));
        setPatientGenes(this.mockMatch, matchGenes);

        List<Map<String, String>> refGenes = new ArrayList<>();
        refGenes.add(mockManualGene(SRCAP, "candidate"));
        setPatientGenes(this.mockReference, refGenes);

        PatientGenotypeSimilarityView view =
            new RestrictedPatientGenotypeSimilarityView(this.mockMatch, this.mockReference, open);

        assertNoGenetics(view);
    }

    /** Candidate genes intersected properly. */
    @Test
    public void testCandidateGeneNoMatch()
    {
        Collection<String> matchGenes = new ArrayList<>();
        matchGenes.add(SRCAP);
        matchGenes.add("TTN");
        setPatientGenes(this.mockMatch, mockCandidateGenes(matchGenes));

        Collection<String> refGenes = new ArrayList<>();
        refGenes.add("Corf27");
        refGenes.add("HEXA");
        setPatientGenes(this.mockReference, mockCandidateGenes(refGenes));

        PatientGenotypeSimilarityView view =
            new RestrictedPatientGenotypeSimilarityView(this.mockMatch, this.mockReference, open);

        assertNoMatch(view);
    }

    /** Candidate genes intersected properly. */
    @Test
    public void testCandidateGeneNoMatchOnBlanks()
    {
        Collection<String> matchGenes = new ArrayList<>();
        matchGenes.add(SRCAP);
        matchGenes.add("   ");
        matchGenes.add("");
        setPatientGenes(this.mockMatch, mockCandidateGenes(matchGenes));

        Collection<String> refGenes = new ArrayList<>();
        refGenes.add("");
        refGenes.add("HEXA");
        refGenes.add("  ");
        setPatientGenes(this.mockReference, mockCandidateGenes(refGenes));

        PatientGenotypeSimilarityView view =
            new RestrictedPatientGenotypeSimilarityView(this.mockMatch, this.mockReference, open);

        assertNoMatch(view);
    }

    /** Candidate gene whitespace behavior. */
    @Test
    public void testCandidateGeneMatchIgnoreWhitespace()
    {
        Collection<String> matchGenes = new ArrayList<>();
        matchGenes.add(SRCAP);
        setPatientGenes(this.mockMatch, mockCandidateGenes(matchGenes));

        Collection<String> refGenes = new ArrayList<>();
        refGenes.add("  SRCAP  ");
        setPatientGenes(this.mockReference, mockCandidateGenes(refGenes));

        PatientGenotypeSimilarityView view =
            new RestrictedPatientGenotypeSimilarityView(this.mockMatch, this.mockReference, open);

        Set<String> genes = view.getMatchingGenes();
        Assert.assertEquals(1, genes.size());

        JSONArray results = view.toJSON();
        Assert.assertTrue(results.length() > 0);
    }

    /** Candidate genes work if match is missing genotype data. */
    @Test
    public void testCandidateGeneWithoutMatchGenetics()
    {
        Collection<String> refGenes = new ArrayList<>();
        refGenes.add("Corf27");
        refGenes.add("HEXA");
        setPatientGenes(this.mockReference, mockCandidateGenes(refGenes));

        PatientGenotypeSimilarityView view =
            new RestrictedPatientGenotypeSimilarityView(this.mockMatch, this.mockReference, open);

        assertNoGenetics(view);
    }

    /** Candidate genes work if ref is missing genotype data. */
    @Test
    public void testCandidateGeneWithoutRefGenetics()
    {
        Collection<String> matchGenes = new ArrayList<>();
        matchGenes.add(SRCAP);
        matchGenes.add("TTN");
        setPatientGenes(this.mockMatch, mockCandidateGenes(matchGenes));

        PatientGenotypeSimilarityView view =
            new RestrictedPatientGenotypeSimilarityView(this.mockMatch, this.mockReference, open);

        assertNoGenetics(view);
    }

    /** Manual VUS variants should not be used for matchmaking at this time. */
    @Test
    public void testManualVariantsNotUsedInMatching()
    {
        // Candidate variant in match
        List<Map<String, String>> fakeVariants = new ArrayList<>();
        fakeVariants.add(mockManualVariant(SRCAP, "variant_u_s"));
        setPatientVariants(this.mockMatch, fakeVariants);

        // Candidate gene in reference
        Collection<String> refGenes = new ArrayList<>();
        refGenes.add(SRCAP);
        setPatientGenes(this.mockReference, mockCandidateGenes(refGenes));

        PatientGenotypeSimilarityView view =
            new RestrictedPatientGenotypeSimilarityView(this.mockMatch, this.mockReference, open);

        assertNoGenetics(view);
    }

    /** Manual VUS variants in negative genes should not be used for matchmaking. */
    @Test
    public void testManualVUSInNegativeGene()
    {
        // VUS in candidate gene in match
        List<Map<String, String>> fakeGenes = new ArrayList<>();
        fakeGenes.add(mockManualGene(SRCAP, "rejected"));
        setPatientGenes(this.mockMatch, fakeGenes);

        List<Map<String, String>> fakeVariants = new ArrayList<>();
        fakeVariants.add(mockManualVariant(SRCAP, "variant_u_s"));
        setPatientVariants(this.mockMatch, fakeVariants);

        // Candidate gene in reference
        Collection<String> refGenes = new ArrayList<>();
        refGenes.add(SRCAP);
        setPatientGenes(this.mockReference, mockCandidateGenes(refGenes));

        PatientGenotypeSimilarityView view =
            new RestrictedPatientGenotypeSimilarityView(this.mockMatch, this.mockReference, open);

        assertNoGenetics(view);
    }

    /** Candidate genes affect score of existing variants in gene. */
    @Test
    public void testVariantInCandidateGene() throws ComponentLookupException
    {
        Collection<String> matchGenes = new ArrayList<>();
        matchGenes.add(SRCAP);
        matchGenes.add("TTN");
        setPatientGenes(this.mockMatch, mockCandidateGenes(matchGenes));

        Collection<String> refGenes = new ArrayList<>();
        refGenes.add(SRCAP);
        setPatientGenes(this.mockReference, mockCandidateGenes(refGenes));

        // Include exome with variant in SRCAP in match patient
        setPatientExome(this.mockMatch, EXOME_1);
        // No exome for reference patient

        PatientGenotypeSimilarityView view =
            new RestrictedPatientGenotypeSimilarityView(this.mockMatch, this.mockReference, open);

        // SRCAP (candidate + exome vs. candidate) should match
        Set<String> genes = view.getMatchingGenes();
        Assert.assertEquals(1, genes.size());
        Assert.assertTrue(genes.contains(SRCAP));

        JSONArray results = view.toJSON();
        Assert.assertEquals(1, results.length());

        JSONObject top = results.getJSONObject(0);
        Assert.assertTrue(top.getString("gene").equals(SRCAP));
        double score = top.getDouble("score");
        Assert.assertTrue(String.format("Unexpected score: %.4f", score), score > 0.5);

        // Ensure match shows underlying exome variant details
        assertVariantDetailLevel(VariantDetailLevel.FULL, top.getJSONObject("match"), 1);

        // Ensure reference only shows score (candidate gene)
        assertVariantDetailLevel(VariantDetailLevel.NONE, top.getJSONObject("reference"), 0);
    }

    /** Candidate genes work even if no existing variants in gene. */
    @Test
    public void testNoVariantsInCandidateGene()
    {
        Collection<String> matchGenes = new ArrayList<>();
        matchGenes.add("NOTCH2");
        setPatientGenes(this.mockMatch, mockCandidateGenes(matchGenes));

        // Include exome with variant in SRCAP in match patient
        setPatientExome(this.mockMatch, EXOME_1);

        // Include exome with variant in NOTCH2, SRCAP in reference patient
        setPatientExome(this.mockReference, EXOME_2);

        PatientGenotypeSimilarityView view =
            new RestrictedPatientGenotypeSimilarityView(this.mockMatch, this.mockReference, open);

        // NOTCH2 (candidate vs. exome) should match
        // SRCAP (exome vs. exome) should *not* match.
        Set<String> genes = view.getMatchingGenes();
        Assert.assertEquals(1, genes.size());
        Assert.assertTrue(genes.contains("NOTCH2"));

        JSONArray results = view.toJSON();
        Assert.assertEquals(1, results.length());

        JSONObject top = results.getJSONObject(0);
        Assert.assertTrue(top.getString("gene").equals("NOTCH2"));
        Assert.assertTrue(top.getDouble("score") > 0.5);

        // Ensure match doesn't show underlying variant details
        assertVariantDetailLevel(VariantDetailLevel.NONE, top.getJSONObject("match"), 0);

        // Ensure reference shows underlying exome variant details
        assertVariantDetailLevel(VariantDetailLevel.FULL, top.getJSONObject("reference"), 2);
    }

    /** Only score shown for variant when matchable. */
    @Test
    public void testMultipleVariantsMatchVisibility()
    {
        // Include exome with variants in HLA-DQB1 in match patient
        setPatientExome(this.mockMatch, EXOME_1);

        Collection<String> refGenes = new ArrayList<>();
        refGenes.add("HLA-DQB1");
        setPatientGenes(this.mockReference, mockCandidateGenes(refGenes));

        PatientGenotypeSimilarityView view =
            new RestrictedPatientGenotypeSimilarityView(this.mockMatch, this.mockReference, limited);

        // HLA-DQB1 (candidate vs. exome) should match.
        Set<String> genes = view.getMatchingGenes();
        Assert.assertEquals(1, genes.size());
        Assert.assertTrue(genes.contains("HLA-DQB1"));

        Collection<String> candidateGenes = view.getCandidateGenes();
        Assert.assertTrue(candidateGenes.isEmpty());

        JSONArray results = view.toJSON();
        Assert.assertEquals(1, results.length());

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
        this.mockMatch = getBasicMockMatch();
        this.mockReference = getBasicMockReference();
    }

    @After
    public void tearDownComponents()
    {
        this.mockMatch = null;
        this.mockReference = null;
    }
}
