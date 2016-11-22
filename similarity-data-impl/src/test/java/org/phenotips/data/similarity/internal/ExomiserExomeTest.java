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

import org.phenotips.data.similarity.Exome;
import org.phenotips.data.similarity.Variant;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

/**
 * Tests for the {@link ExomiserExome} implementation based on the latest Exomiser-3.0.2 output file format
 *
 * @version $Id$
 */
public class ExomiserExomeTest
{
    // Lines intentionally shuffled
    private static final String TEST_FILE =
        "#CHROM\tPOS\tREF\tALT\tQUAL\tFILTER\tGENOTYPE\tCOVERAGE\tFUNCTIONAL_CLASS\tHGVS\tEXOMISER_GENE\tCADD(>0.483)\tPOLYPHEN(>0.956|>0.446)\tMUTATIONTASTER(>0.94)\tSIFT(<0.06)\tDBSNP_ID\tMAX_FREQUENCY\tDBSNP_FREQUENCY\tEVS_EA_FREQUENCY\tEVS_AA_FREQUENCY\tEXOMISER_VARIANT_SCORE\tEXOMISER_GENE_PHENO_SCORE\tEXOMISER_GENE_VARIANT_SCORE\tEXOMISER_GENE_COMBINED_SCORE\n"
            + "chr6\t32628660\tT\tC\t225.0\tPASS\t0/1\t94\tSPLICING\tHLA-DQB1:uc031snx.1:exon5:c.773-1A>G\tHLA-DQB1\t.\t.\t.\t.\t.\t0.0\t.\t.\t.\t0.9\t0.612518\t1.0\t0.9057237\n"
            + "chr16\t30748691\tC\tT\t225.0\tPASS\t0/1\t40\tSTOPGAIN\tSRCAP:uc002dzg.1:exon29:c.6715C>T:p.R2239*\tSRCAP\t.\t.\t.\t.\t.\t0.0\t.\t.\t.\t0.95\t0.8603835\t0.95\t0.9876266\n"
            + "chr1\t120611962\tG\tC\t76.0\tPASS\t0/1\t42\tMISSENSE\tNOTCH2:uc001eil.3:exon1:c.55C>G:p.C19Q\tNOTCH2\t6.292\t.\t.\t0.0\t.\t0.0\t.\t.\t.\t0.6\t0.7029731\t1.0\t0.9609373\n"
            + "chr1\t120611963\tG\tC\t76.0\tPASS\t0/1\t42\tMISSENSE\tNOTCH2:uc001eil.3:exon1:c.56C>G:p.C19Q\tNOTCH2\t6.292\t.\t.\t0.0\t.\t0.0\t.\t.\t.\t0.4\t0.7029731\t1.0\t0.9609373\n"
            + "chr1\t120611964\tG\tC\t76.0\tPASS\t0/1\t42\tMISSENSE\tNOTCH2:uc001eil.3:exon1:c.57C>G:p.C19W\tNOTCH2\t6.292\t.\t.\t0.0\t.\t0.0\t.\t.\t.\t1.0\t0.7029731\t1.0\t0.9609373\n";

    /** Parse an {@link Exome} object from a raw String */
    private Exome parseExomeFromString(String exomeString)
    {
        Exome exome = null;
        try {
            exome = new ExomiserExome(new StringReader(exomeString));
        } catch (IOException e) {
            Assert.fail("Exomiser file parsing resulted in IOException");
        }
        return exome;
    }

    /** Basic test for Exomiser output file parsing. */
    @Test
    public void testParseExomiser()
    {
        Exome exome = parseExomeFromString(TEST_FILE);

        Assert.assertEquals(3, exome.getGenes().size());
    }

    /** Ensure variants are parsed properly. */
    @Test
    public void testVariantDetails()
    {
        Exome exome = parseExomeFromString(TEST_FILE);

        Assert.assertEquals(0.9876266, exome.getGeneScore("SRCAP"), 0.00001);

        // Get top variant and make sure it was parsed correctly
        List<Variant> vs = exome.getTopVariants("SRCAP", 99);
        Assert.assertEquals(1, vs.size());

        Variant v1 = vs.get(0);
        Assert.assertEquals("STOPGAIN", v1.getEffect());
        Assert.assertEquals("16", v1.getChrom());
        Assert.assertEquals((Integer) 30748691, v1.getPosition());
        Assert.assertEquals("C", v1.getRef());
        Assert.assertEquals("T", v1.getAlt());
        Assert.assertEquals("40", v1.getAnnotation("COVERAGE"));
        Assert.assertFalse(v1.isHomozygous());
    }

    /** Multiple variants in same gene must be sorted by score. */
    @Test
    public void testMultipleVariantsInGene()
    {
        Exome exome = parseExomeFromString(TEST_FILE);

        List<Variant> vs = exome.getTopVariants("NOTCH2", 99);
        Assert.assertEquals(3, vs.size());
        Assert.assertTrue(vs.get(0).getScore() > vs.get(1).getScore() &&
            vs.get(1).getScore() > vs.get(2).getScore());
    }

    /** Unknown genes should have null score, empty variants. */
    @Test
    public void testUnknownGene()
    {
        Exome exome = parseExomeFromString(TEST_FILE);
        String geneName = "Unknown gene";
        Assert.assertNull(exome.getGeneScore(geneName));
        Assert.assertTrue(exome.getTopVariants(geneName, 99).isEmpty());
    }

    /** Genes should iterate in decreasing order of score. */
    @Test
    public void testGetTopGenes()
    {
        Exome exome = parseExomeFromString(TEST_FILE);
        List<String> geneNames = new ArrayList<String>();
        for (String gene : exome.getTopGenes(0)) {
            geneNames.add(gene);
        }
        Assert.assertEquals(3, geneNames.size());
        Assert.assertEquals("SRCAP", geneNames.get(0));
        Assert.assertEquals("NOTCH2", geneNames.get(1));
        Assert.assertEquals("HLA-DQB1", geneNames.get(2));

        Assert.assertEquals(3, exome.getTopGenes(5).size());
    }
}
