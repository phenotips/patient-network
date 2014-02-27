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

import org.apache.commons.lang3.tuple.Pair;
import org.phenotips.data.similarity.Genotype;
import org.phenotips.data.similarity.Variant;

/**
 * TODO
 * 
 * @version $Id$
 * @since
 */
public class ExomizerGenotype implements Genotype
{
    private Map<String, Double> geneScores;
    private Map<String, List<Variant>> variants;
    
    ExomizerGenotype() {
        this.variants = new HashMap<String, List<Variant>>();
        this.geneScores = new HashMap<String, Double>();
    }
    
    /**
     * Create a Genotype object from an Exomizer output file.
     * 
     * @param exomizerOutput an exomizer-annotated VCF file 
     * @throws FileNotFoundException if the file does not exist
     */
    ExomizerGenotype(File exomizerOutput) throws FileNotFoundException
    {
        this();
        Scanner fileScan = new Scanner(exomizerOutput);
        while (fileScan.hasNextLine()) {
            String line = fileScan.nextLine();
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
        for (List<Variant> variants : this.variants.values()) {
            Collections.sort(variants);
        }
    }
    
    @Override
    public Set<String> getGenes() {
        return Collections.unmodifiableSet(this.variants.keySet());
    }
    
    @Override
    public double getGeneScore(String gene) {
        Double geneScore = this.geneScores.get(gene);
        if (geneScore == null) {
            return 0.0;
        } else {
            return geneScore;
        }
    }

    @Override
    public Pair<Double, Double> getTopVariantScores(String gene) {
        List<Variant> variants = this.variants.get(gene);
        double first = 0.0;
        double second = 0.0;
        if (variants.size() >= 1) {
            first = variants.get(0).getScore();
        } 
        if (variants.size() >= 2) {
            second = variants.get(1).getScore();
        }
        return Pair.of(first, second);
    }
    
    @Override
    public void addGene(String gene, double score, List<Variant> variants)
    {
        this.geneScores.put(gene, score);
        this.variants.put(gene, variants);
    }
    
    @Override
    public JSONObject toJSON()
    {
        JSONObject result = new JSONObject();
        JSONArray geneList = new JSONArray();
        for (String geneName : this.variants.keySet()) {
            JSONObject gene = new JSONObject();
            gene.element("gene", geneName);
            gene.element("score", getGeneScore(geneName));
            
            JSONArray variantList = new JSONArray();
            List<Variant> variants = this.variants.get(geneName);
            for (Variant variant : variants) {
                variantList.add(variant.toJSON());
            }
            gene.element("variants", variantList);
            
            geneList.add(gene);
        }
        result.element("genes", geneList);
        return result;
    }

}
