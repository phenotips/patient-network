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

import org.phenotips.data.similarity.Exome;
import org.phenotips.data.similarity.Variant;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

/**
 * Tests for the {@link ExomiserExome} implementation based on the latest Exomiser-3.0.2 output file format
 *
 * @version $Id$
 */
public class ExomiserGenotypeTest
{
    private static final String TEST_FILE =
        "#CHROM\tPOS\tREF\tALT\tQUAL\tFILTER\tGENOTYPE\tCOVERAGE\tFUNCTIONAL_CLASS\tHGVS\tEXOMISER_GENE\tCADD(>0.483)\tPOLYPHEN(>0.956|>0.446)\tMUTATIONTASTER(>0.94)\tSIFT(<0.06)\tDBSNP_ID\tMAX_FREQUENCY\tDBSNP_FREQUENCY\tEVS_EA_FREQUENCY\tEVS_AA_FREQUENCY\tEXOMISER_VARIANT_SCORE\tEXOMISER_GENE_PHENO_SCORE\tEXOMISER_GENE_VARIANT_SCORE\tEXOMISER_GENE_COMBINED_SCORE\n" +
        "chr16\t30748691\tC\tT\t225.0\tPASS\t0/1\t40\tSTOPGAIN\tSRCAP:uc002dzg.1:exon29:c.6715C>T:p.R2239*\tSRCAP\t.\t.\t.\t.\t.\t0.0\t.\t.\t.\t0.95\t0.8603835\t0.95\t0.9876266\n" +
        "chr1\t120611963\tG\tC\t76.0\tPASS\t0/1\t42\tMISSENSE\tNOTCH2:uc001eil.3:exon1:c.56C>G:p.C19Q\tNOTCH2\t6.292\t.\t.\t0.0\t.\t0.0\t.\t.\t.\t0.4\t0.7029731\t1.0\t0.9609373\n" +
        "chr1\t120611964\tG\tC\t76.0\tPASS\t0/1\t42\tMISSENSE\tNOTCH2:uc001eil.3:exon1:c.57C>G:p.C19W\tNOTCH2\t6.292\t.\t.\t0.0\t.\t0.0\t.\t.\t.\t1.0\t0.7029731\t1.0\t0.9609373\n" +
        "chr6\t32628660\tT\tC\t225.0\tPASS\t0/1\t94\tSPLICING\tHLA-DQB1:uc031snx.1:exon5:c.773-1A>G\tHLA-DQB1\t.\t.\t.\t.\t.\t0.0\t.\t.\t.\t0.9\t0.612518\t1.0\t0.9057237\n";
    /** Basic test for Exomiser output file parsing. */
    @Test
    public void testParseExomiser()
    {
        Exome genotype = null;
        try {
            genotype = new ExomiserExome(new StringReader(TEST_FILE));
        } catch (IOException e) {
            Assert.fail("Exomiser file parsing resulted in IOException");
        }
        
        Assert.assertEquals(0.9876266, genotype.getGeneScore("SRCAP"), 0.00001);
        
        // Get top variant and make sure it was parsed correctly
        Variant v1 = genotype.getTopVariant("SRCAP", 0);
        Assert.assertEquals("STOPGAIN", v1.getEffect());
        Assert.assertEquals("16", v1.getChrom());
        Assert.assertEquals((Integer)30748691, v1.getPosition());
        Assert.assertEquals("C", v1.getRef());
        Assert.assertEquals("T", v1.getAlt());
        Assert.assertEquals("40", v1.getAnnotation("COVERAGE"));
        Assert.assertFalse(v1.isHomozygous());

        // Try to get second (non-existent) variant
        Variant v2 = genotype.getTopVariant("SRCAP", 1);
        Assert.assertNull(v2);
        
        Assert.assertEquals(3, genotype.getGenes().size());
        
        // Ensure multiple variants in same gene are sorted by score
        List<Variant> vs = genotype.getTopVariants("NOTCH2");
        Assert.assertEquals(2, vs.size());
        Assert.assertTrue(vs.get(0).getScore() > vs.get(1).getScore()); 
    }
}
