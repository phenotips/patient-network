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
import org.phenotips.data.PatientData;
import org.phenotips.data.similarity.Exome;
import org.phenotips.data.similarity.ExomeManager;
import org.phenotips.data.similarity.PatientGenotype;
import org.phenotips.data.similarity.Variant;

import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.component.manager.ComponentManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class represents the collective genotype information in a patient, both from candidate genes and from exome
 * data.
 *
 * @version $Id$
 * @since 1.0M6
 */
public class DefaultPatientGenotype extends AbstractExome implements PatientGenotype
{

    /** Factory for loading exome data. */
    protected static ExomeManager exomeManager;

    /** List of genes variant interpretations of pathogenic status 3-5 (VUS or higher). */
    private static final String[] TOP_INTERPRETATIONS =
    {"pathogenic", "likely_pathogenic", "variant_u_s"};

    /** Logging helper object. */
    private static Logger logger = LoggerFactory.getLogger(DefaultPatientGenotype.class);

    /** The candidate genes for the patient. */
    protected Set<String> candidateGenes;

    /** The exome data for the patient. */
    protected Exome exome;

    /**
     * Constructor for a {@link PatientGenotype} object, representing the candidate genes and exome sequence data for
     * the given {@link Patient}.
     *
     * @param patient the patient object
     */
    public DefaultPatientGenotype(Patient patient)
    {
        // Initialize static exome manager
        if (exomeManager == null) {
            ComponentManager componentManager = ComponentManagerRegistry.getContextComponentManager();
            try {
                exomeManager = componentManager.getInstance(ExomeManager.class, "exomiser");
            } catch (ComponentLookupException e) {
                logger.error("Unable to look up ExomeManager: " + e.toString());
            }
        }

        if (exomeManager != null) {
            this.exome = exomeManager.getExome(patient);
        }
        this.candidateGenes = new HashSet<String>();
        this.candidateGenes.addAll(getManualGeneNames(patient));
        this.candidateGenes.addAll(getManualVariantGeneNames(patient));

        // Score all genes
        for (String gene : this.getGenes()) {
            this.geneScores.put(gene, this.getGeneScore(gene));
        }
    }

    /**
     * Return a set of the names of solved (candidate if no solved) genes listed for the given patient.
     *
     * @param p the {@link Patient}
     * @return a (potentially-empty) unmodifiable set of the names of genes
     */
    private static Set<String> getManualGeneNames(Patient p)
    {
        PatientData<Map<String, String>> genesData = null;
        Set<String> geneNames = new HashSet<String>();
        if (p != null) {
            genesData = p.getData("genes");
        }
        if (genesData != null) {
            Set<String> geneCandidateNames = new HashSet<String>();
            Iterator<Map<String, String>> iterator = genesData.iterator();
            while (iterator.hasNext()) {
                Map<String, String> geneInfo = iterator.next();
                String geneName = geneInfo.get("gene");
                if (geneName == null) {
                    continue;
                }
                geneName = geneName.trim();
                if (geneName.isEmpty()) {
                    continue;
                }
                //get classification
                String status = geneInfo.get("status");
                if ("solved".equals(status)) {
                    geneNames.add(geneName);
                }
                if ("candidate".equals(status)) {
                    geneCandidateNames.add(geneName);
                }
            }
            if (geneNames.isEmpty() && !geneCandidateNames.isEmpty()) {
                geneNames.addAll(geneCandidateNames);
            }
            return Collections.unmodifiableSet(geneNames);
        }
        return Collections.emptySet();
    }

    /**
     * Return a set of the gene symbols of variants with pathogenic status 3-5 (VUS or higher).
     *
     * @param p the {@link Patient}
     * @return a (potentially-empty) unmodifiable set of the names of genes
     */
    private static Set<String> getManualVariantGeneNames(Patient p)
    {
        PatientData<Map<String, String>> variants = null;
        Set<String> geneNames = new HashSet<String>();
        if (p != null) {
            variants = p.getData("variants");
        }
        if (variants != null && variants.isIndexed()) {
            for (Map<String, String> variant : variants) {
                String variantName = variant.get("cdna");
                if (variantName == null) {
                    continue;
                }
                String geneSymbol = variant.get("genesymbol");
                if (geneSymbol == null) {
                    continue;
                }
                String interpretation = variant.get("interpretation");
                if (interpretation != null && Arrays.asList(TOP_INTERPRETATIONS).contains(interpretation)) {
                    geneNames.add(geneSymbol);
                }
            }
            return Collections.unmodifiableSet(geneNames);
        }
        return Collections.emptySet();
    }

    @Override
    public boolean hasGenotypeData()
    {
        return (this.candidateGenes != null && !this.candidateGenes.isEmpty()) || this.exome != null;
    }

    @Override
    public Set<String> getCandidateGenes()
    {
        if (this.candidateGenes == null) {
            return Collections.emptySet();
        } else {
            return Collections.unmodifiableSet(this.candidateGenes);
        }
    }

    @Override
    public Set<String> getGenes()
    {
        Set<String> genes = new HashSet<String>();
        if (this.exome != null) {
            genes.addAll(this.exome.getGenes());
        }
        if (this.candidateGenes != null) {
            genes.addAll(this.candidateGenes);
        }
        return genes;
    }

    @Override
    public List<Variant> getTopVariants(String gene)
    {
        //TODO add manually entered variants
        List<Variant> variants;
        if (this.exome == null) {
            variants = new ArrayList<Variant>();
        } else {
            variants = this.exome.getTopVariants(gene);
        }
        return variants;
    }

    @Override
    public Double getGeneScore(String gene)
    {
        // If gene is a candidate gene, reduce distance of score to 1.0
        // by 1 / (# of candidate genes).
        // If variant is not found in exome, score is 0.9 ** (# candidate genes - 1)
        Double score = null;
        if (this.exome != null) {
            score = this.exome.getGeneScore(gene);
        }
        // Boost score if candidate gene
        if (this.candidateGenes != null && this.candidateGenes.contains(gene)) {
            if (score == null) {
                score = Math.pow(0.9, this.candidateGenes.size() - 1);
            } else {
                double candidateGeneBoost = (1.0 - score) / this.candidateGenes.size();
                score += candidateGeneBoost;
            }
        }
        return score;
    }
}
