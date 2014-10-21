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

import org.phenotips.data.similarity.Genotype;

import java.io.IOException;
import java.io.StringReader;

import org.junit.Assert;
import org.junit.Test;

/**
 * Tests for the {@link ExomiserGenotype} implementation based on the latest Exomiser-3.0.2 output file format
 *
 * @version $Id$
 */
public class ExomiserGenotypeTest
{
    private static final String TEST_FILE =
        "##fileformat=VCFv4.1\n"
            + "#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\tFORMAT\tGENOTYPE\n"
            + "chr16\t30748691\t.\tC\tT\t225.0\tPASS\tDP=40;VDB=0.0403;AF1=0.5;AC1=1;DP4=0,16,4,18;MQ=59;FQ=133;PV4=0.12,0.13,1,1;EXOMISER_GENE=SRCAP;EXOMISER_VARIANT_SCORE=0.95;EXOMISER_GENE_PHENO_SCORE=0.8331819;EXOMISER_GENE_VARIANT_SCORE=0.95;EXOMISER_GENE_COMBINED_SCORE=0.98364943;EXOMISER_EFFECT=STOPGAIN\tGT\t0/1\n"
            + "chrX\t70349991\t.\tG\tT\t4.13\tPASS\tDP=14;VDB=0.0102;AF1=0.4998;AC1=1;DP4=6,6,1,1;MQ=60;FQ=6.2;PV4=1,1,1,0.43;EXOMISER_GENE=MED12;EXOMISER_VARIANT_SCORE=1.0;EXOMISER_GENE_PHENO_SCORE=0.63577175;EXOMISER_GENE_VARIANT_SCORE=1.0;EXOMISER_GENE_COMBINED_SCORE=0.9244368;EXOMISER_EFFECT=MISSENSE\tGT\t0/1\n"
            + "chr6\t32629935\t.\tC\tG\t27.0\tPASS\tDP=85;VDB=0.0342;AF1=0.5;AC1=1;DP4=45,21,6,12;MQ=39;FQ=30;PV4=0.013,0.016,0.0014,1;EXOMISER_GENE=HLA-DQB1;EXOMISER_VARIANT_SCORE=0.96;EXOMISER_GENE_PHENO_SCORE=0.6184042;EXOMISER_GENE_VARIANT_SCORE=1.0;EXOMISER_GENE_COMBINED_SCORE=0.91082;EXOMISER_EFFECT=MISSENSE\tGT\t0/1\n";

    /** Basic test for Exomiser output file parsing. */
    @Test
    public void testParseExomiser()
    {
        Genotype genotype = null;
        try {
            genotype = new ExomiserGenotype(new StringReader(TEST_FILE));
        } catch (IOException e) {
            Assert.fail("Exomiser file parsing resulted in IOException");
        }
        
        Assert.assertEquals(0.8331819, genotype.getGeneScore("SRCAP"), 0.00001);
        Assert.assertEquals(3, genotype.getGenes().size());
    }
}
