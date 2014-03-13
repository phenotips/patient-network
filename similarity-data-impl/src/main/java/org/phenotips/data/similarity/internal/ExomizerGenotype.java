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
import org.phenotips.data.similarity.Variant;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

/**
 * This class represents the genotype information from an exomizer-annotated VCF file. Specifically, a collection of
 * variants annotated with genes, phenotype scores, and harmfulness scores.
 * 
 * @version $Id$
 * @since
 */
public class ExomizerGenotype implements Genotype
{
    /** The phenotype score for each gene with a variant. */
    private Map<String, Double> geneScores;

    /** The variants in each gene. */
    private Map<String, List<Variant>> variants;

    /**
     * Create a Genotype object from an Exomizer output file.
     * 
     * @param exomizerOutput an exomizer-annotated VCF file
     * @throws FileNotFoundException if the file does not exist
     */
    ExomizerGenotype(File exomizerOutput) throws FileNotFoundException
    {
        this.variants = new HashMap<String, List<Variant>>();
        this.geneScores = new HashMap<String, Double>();
        Scanner fileScan = new Scanner(exomizerOutput);
        while (fileScan.hasNextLine()) {
            String line = fileScan.nextLine();
            if (line.startsWith("#")) {
                continue;
            }

            Variant variant = new ExomizerVariant(line);

            String gene = variant.getAnnotation("GENE");
            Double geneScore = Double.parseDouble(variant.getAnnotation("PHENO_SCORE"));
            geneScores.put(gene, geneScore);

            if (gene != null && !gene.isEmpty()) {
                List<Variant> geneMutations = this.variants.get(gene);
                if (geneMutations == null) {
                    geneMutations = new ArrayList<Variant>();
                    this.variants.put(gene, geneMutations);
                }

                geneMutations.add(variant);
                // Represent homozygous variants as two separate mutations
                if (variant.isHomozygous()) {
                    geneMutations.add(variant);
                }
            }
        }
        fileScan.close();

        // Sort variants within each gene by harmfulness score
        // TODO: have lists maintain sorted order, instead of sorting at the end
        for (List<Variant> vs : this.variants.values()) {
            Collections.sort(vs);
        }
    }

    @Override
    public Set<String> getGenes()
    {
        return Collections.unmodifiableSet(this.variants.keySet());
    }

    @Override
    public Double getGeneScore(String gene)
    {
        return this.geneScores.get(gene);
    }

    @Override
    public Variant getTopVariant(String gene, int k)
    {
        List<Variant> vs = this.variants.get(gene);
        if (k < vs.size()) {
            return vs.get(k);
        } else {
            return null;
        }
    }

    @Override
    public JSONArray toJSON()
    {
        JSONArray geneList = new JSONArray();
        for (String geneName : this.variants.keySet()) {
            JSONObject gene = new JSONObject();
            gene.element("gene", geneName);
            gene.element("score", getGeneScore(geneName));

            JSONArray variantList = new JSONArray();
            List<Variant> vs = this.variants.get(geneName);
            for (Variant v : vs) {
                variantList.add(v.toJSON());
            }
            gene.element("variants", variantList);

            geneList.add(gene);
        }
        return geneList;
    }

}
