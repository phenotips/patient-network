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

import org.phenotips.data.similarity.Genotype;
import org.phenotips.data.similarity.Variant;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

/**
 * This class represents the genotype information from an Exomiser-annotated TSV file. Specifically, a collection of
 * variants annotated with genes, phenotype scores, and harmfulness scores.
 *
 * @version $Id$
 */
public class ExomiserGenotype implements Genotype
{
    /** Info field key for the variant's gene. */
    private static final String GENE_KEY = "EXOMISER_GENE";

    /** Info field key for gene score. */
    private static final String GENE_SCORE_KEY = "EXOMISER_GENE_COMBINED_SCORE";

    private static final String FIELD_DELIMITER = "\t";

    /** The overall score for each gene with a variant. */
    private Map<String, Double> geneScores;

    /** The variants in each gene. */
    private Map<String, List<Variant>> variants;

    /**
     * Constructor for empty Genotype object.
     */
    ExomiserGenotype()
    {
        this.variants = new HashMap<String, List<Variant>>();
        this.geneScores = new HashMap<String, Double>();
    }

    /**
     * Create a Genotype object from an Exomiser output file.
     *
     * @param exomiserOutput an exomiser-annotated VCF file
     * @throws IOException if the file does not follow the Exomiser format
     */
    ExomiserGenotype(Reader exomiserOutput) throws IOException
    {
        this.variants = new HashMap<String, List<Variant>>();
        this.geneScores = new HashMap<String, Double>();
        BufferedReader reader = new BufferedReader(exomiserOutput);

        // Parse column names from header
        String header = reader.readLine();
        if (header == null || !header.startsWith("#")) {
            throw new IOException("Missing header in Exomiser file");
        }
        List<String> columns = Arrays.asList(header.substring(1).trim().split(FIELD_DELIMITER));

        while (true) {
            String line = reader.readLine();
            if (line == null) {
                break;
            }

            String[] values = line.split(FIELD_DELIMITER);
            Variant variant = parseVariant(columns, values);

            String gene = getRequiredAnnotation(variant, GENE_KEY);
            String rawGeneScore = getRequiredAnnotation(variant, GENE_SCORE_KEY);
            Double geneScore = Double.parseDouble(rawGeneScore);
            this.geneScores.put(gene, geneScore);

            // Add variant to gene without sorting (save sorting for end)
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
        reader.close();

        // Sort variants within each gene by harmfulness score
        // TODO: have lists maintain sorted order, instead of sorting at the end
        for (List<Variant> vs : this.variants.values()) {
            if (vs.size() > 1) {
                Collections.sort(vs);
            }
        }
    }

    /**
     * Parse a variant from an Exomiser TSV line.
     *
     * @param columns the column headers for the TSV file
     * @param values the values in each column for the current row
     * @return a Variant with the data from the row
     * @throws IOException if there is an error parsing the row
     */
    private Variant parseVariant(List<String> columns, String[] values) throws IOException
    {
        try {
            return new ExomiserVariant(columns, values);
        } catch (IllegalArgumentException e) {
            throw new IOException("Error parsing variant line: " + Arrays.toString(values));
        }
    }

    private String getRequiredAnnotation(Variant variant, String key) throws IOException
    {
        String value = variant.getAnnotation(key);
        if (value == null) {
            throw new IOException("Exomiser variant missing required field: " + key);
        }
        return value;
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
        if (vs != null && k < vs.size()) {
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
