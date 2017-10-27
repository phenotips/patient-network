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
import org.phenotips.data.Patient;
import org.phenotips.data.similarity.AccessType;
import org.phenotips.data.similarity.PatientGenotypeManager;
import org.phenotips.data.similarity.Variant;
import org.phenotips.vocabulary.Vocabulary;
import org.phenotips.vocabulary.VocabularyManager;
import org.phenotips.vocabulary.VocabularyTerm;

import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.component.manager.ComponentManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of {@link org.phenotips.data.similarity.PatientGenotypeSimilarityView} that always reveals the full
 * patient information.
 *
 * @version $Id$
 * @since 1.0M6
 */
public class DefaultPatientGenotypeSimilarityView extends AbstractPatientGenotypeSimilarityView
{
    /** Manager to allow access to underlying genotype data. */
    protected static PatientGenotypeManager genotypeManager;

    /** Manager to allow access to HGNC vocabulary gene data. */
    protected static VocabularyManager vocabularyManager;

    /** Logging helper object. */
    private static Logger logger = LoggerFactory.getLogger(DefaultPatientGenotypeSimilarityView.class);

    private static Double lowerGeneScoreThreshold = 0.0001;

    /**
     * Maximum number of genes to report in JSON (0 for all). TODO: pull out into configuration.
     */
    private static final int MAX_GENES_REPORTED_IN_JSON = 20;

    /**
     * Maximum number of variants to report per gene in JSON (0 for all). TODO: pull out into configuration.
     */
    private static final int MAX_VARIANTS_PER_GENE_REPORTED_IN_JSON = 10;

    static {
        PatientGenotypeManager gm = null;
        VocabularyManager vm = null;
        try {
            ComponentManager ccm = ComponentManagerRegistry.getContextComponentManager();
            gm = ccm.getInstance(PatientGenotypeManager.class);
            vm = ccm.getInstance(VocabularyManager.class);
        } catch (ComponentLookupException e) {
            logger.error("Error loading static components: {}", e.getMessage(), e);
        }
        genotypeManager = gm;
        vocabularyManager = vm;
    }

    /**
     * Simple constructor passing the {@link #match matched patient}, the {@link #reference reference patient}, and the
     * {@link #access patient access type}.
     *
     * @param match the matched patient, which is represented by this match
     * @param reference the reference patient, which the match is against
     * @param access the access type the user has to the match patient
     * @throws IllegalArgumentException if one of the patients is {@code null}
     */
    public DefaultPatientGenotypeSimilarityView(Patient match, Patient reference, AccessType access)
        throws IllegalArgumentException
    {
        super(match, reference, access);

        if (genotypeManager != null) {
            // Store genotype information
            this.matchGenotype = genotypeManager.getGenotype(this.match);
            this.refGenotype = genotypeManager.getGenotype(this.reference);
        }

        // Match candidate genes and any genotype available
        if (this.matchGenotype != null && this.refGenotype != null && this.matchGenotype.hasGenotypeData()
            && this.refGenotype.hasGenotypeData()) {
            matchGenes();
        }
    }

    /**
     * Return the set of candidate genes found in either the match or reference patient.
     *
     * @return a (potentially-empty) set of gene names
     */
    private Set<String> getCandidateGeneUnion()
    {
        Set<String> sharedGenes = new HashSet<>();
        if (this.matchGenotype != null && this.refGenotype != null) {
            Set<String> matchGenes = this.matchGenotype.getCandidateGenes();
            Set<String> refGenes = this.refGenotype.getCandidateGenes();
            sharedGenes.addAll(matchGenes);
            sharedGenes.addAll(refGenes);
        }
        return sharedGenes;
    }

    @Override
    public Set<String> getMatchingGenes()
    {
        // Assumes this.geneScores was initialized via {@link matchGenes()}
        if (this.geneScores == null) {
            return Collections.emptySet();
        } else {
            return Collections.unmodifiableSet(this.geneScores.keySet());
        }
    }

    /**
     * Score gene matches in both patients, and sets 'geneScores' accordingly.
     */
    private void matchGenes()
    {
        // Only consider genes listed as a candidate in at least one of the two patients
        for (String gene : getCandidateGeneUnion()) {
            // Compute gene score based on the genotype scores for the two patients
            Double refScore = this.refGenotype.getGeneScore(gene);
            Double matchScore = this.matchGenotype.getGeneScore(gene);
            double geneScore;
            // Average the scores as long as the gene is listed for both patients (candidate or exome)
            if (refScore == null || matchScore == null
                || refScore < lowerGeneScoreThreshold || matchScore < lowerGeneScoreThreshold) {
                geneScore = 0.0;
            } else {
                geneScore = (refScore + matchScore) / 2.0;
            }

            if (geneScore > 0) {
                // Save score and variants for display
                this.geneScores.put(gene, geneScore);
            }
        }
    }

    @Override
    public double getScore()
    {
        if (this.geneScores.isEmpty()) {
            return 0.0;
        } else {
            double maxScore = 0.0;
            for (String gene : getMatchingGenes()) {
                Double score = this.geneScores.get(gene);
                if (score != null && score > maxScore) {
                    maxScore = score;
                }
            }
            return maxScore;
        }
    }

    /**
     * Serialize the variants in a particular patient in a single gene.
     *
     * @param variants a list of {@link Variant}s to serialize
     * @return JSON for the patient's variants, an empty object if there are no variants
     */
    protected JSONObject getVariantsJSON(List<Variant> variants)
    {
        JSONObject geneJSON = new JSONObject();

        JSONArray variantsJSON = new JSONArray();
        if (variants != null && !variants.isEmpty()) {
            // Show the top variants in the patient
            for (Variant v : variants) {
                variantsJSON.put(v.toJSON());
            }
        }

        // Only add element if there are variants
        if (variantsJSON.length() > 0) {
            geneJSON.put("variants", variantsJSON);
        }
        return geneJSON;
    }

    /**
     * Serialize the variants in both patients in a single gene.
     *
     * @param gene the gene to serialize variants for
     * @return the JSON for the variants in the given gene
     */
    protected JSONObject getGeneJSON(String gene)
    {
        JSONObject variantsJSON = new JSONObject();
        if (this.refGenotype != null) {
            List<Variant> variants = this.refGenotype.getTopVariants(gene, MAX_VARIANTS_PER_GENE_REPORTED_IN_JSON);
            variantsJSON.put("reference", getVariantsJSON(variants));
        }
        if (this.matchGenotype != null) {
            // Use potentially access-controlled method to try to get variants
            List<Variant> variants = this.getTopVariants(gene, MAX_VARIANTS_PER_GENE_REPORTED_IN_JSON);
            variantsJSON.put("match", getVariantsJSON(variants));
        }
        return variantsJSON;
    }

    @Override
    public JSONArray toJSON()
    {
        if (!this.matchGenotype.hasGenotypeData() || !this.refGenotype.hasGenotypeData()) {
            return null;
        }

        JSONArray genesJSON = new JSONArray();
        // Gene genes, in order of decreasing score
        List<Map.Entry<String, Double>> genes = new ArrayList<>(this.geneScores.entrySet());
        Collections.sort(genes, new Comparator<Map.Entry<String, Double>>()
        {
            @Override
            public int compare(Map.Entry<String, Double> e1, Map.Entry<String, Double> e2)
            {
                return Double.compare(e2.getValue(), e1.getValue());
            }
        });

        int numGenesReported = 0;
        for (Map.Entry<String, Double> geneEntry : genes) {
            String gene = geneEntry.getKey();
            String symbol = getGeneSymbol(gene);
            Double score = geneEntry.getValue();

            JSONObject geneObject = new JSONObject();
            geneObject.put("gene", gene);
            if (StringUtils.isNotBlank(symbol)) {
                geneObject.put("symbol", symbol);
            }
            geneObject.put("score", score);
            JSONObject geneJSON = getGeneJSON(gene);
            for (String key : geneJSON.keySet()) {
                geneObject.put(key, geneJSON.get(key));
            }
            genesJSON.put(geneObject);

            numGenesReported++;
            if (numGenesReported > MAX_GENES_REPORTED_IN_JSON && MAX_GENES_REPORTED_IN_JSON > 0) {
                break;
            }
        }
        return genesJSON;
    }

    private String getGeneSymbol(String geneEnsemblId)
    {
        String symbol = null;
        if (vocabularyManager != null) {
            Vocabulary hgnc = vocabularyManager.getVocabulary("HGNC");
            VocabularyTerm term = hgnc.getTerm(geneEnsemblId);
            symbol = (term != null) ? (String) term.get("symbol") : null;
        }
        return symbol;
    }
}
