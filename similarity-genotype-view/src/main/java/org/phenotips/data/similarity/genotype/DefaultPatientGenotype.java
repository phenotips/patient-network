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
import org.phenotips.data.Patient;
import org.phenotips.data.PatientData;
import org.phenotips.data.similarity.Exome;
import org.phenotips.data.similarity.ExomeManager;
import org.phenotips.data.similarity.PatientGenotype;
import org.phenotips.data.similarity.Variant;

import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.component.manager.ComponentManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
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

    private static final String STATUS_CANDIDATE = "candidate";

    private static final String STATUS_SOLVED = "solved";

    /** Logging helper object. */
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultPatientGenotype.class);

    /** The candidate genes for the patient. */
    protected Set<String> candidateGenes;

    /** The exome data for the patient. */
    protected Exome exome;

    /** The status of genes in genotype information. */
    protected String genesStatus;

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
                LOGGER.error("Unable to look up ExomeManager: " + e.toString());
            }
        }

        if (exomeManager != null) {
            this.exome = exomeManager.getExome(patient);
        }
        this.genesStatus = STATUS_SOLVED;
        this.candidateGenes = new HashSet<>();
        this.candidateGenes.addAll(getManualGeneNames(patient));

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
    private Set<String> getManualGeneNames(Patient patient)
    {
        PatientData<Map<String, String>> allGenes = patient.getData("genes");
        if (allGenes != null && allGenes.isIndexed()) {
            Set<String> geneCandidateNames = new HashSet<>();
            Set<String> geneSolvedNames = new HashSet<>();
            for (Map<String, String> gene : allGenes) {
                String geneName = gene.get("gene");
                if (StringUtils.isBlank(geneName)) {
                    continue;
                }

                geneName = geneName.trim();
                String status = gene.get("status");
                if (StringUtils.isBlank(status) || STATUS_CANDIDATE.equals(status)) {
                    geneCandidateNames.add(geneName);
                } else if (STATUS_SOLVED.equals(status)) {
                    geneSolvedNames.add(geneName);
                }
            }
            if (!geneSolvedNames.isEmpty()) {
                return Collections.unmodifiableSet(geneSolvedNames);
            } else if (!geneCandidateNames.isEmpty()) {
                this.genesStatus = STATUS_CANDIDATE;
                return Collections.unmodifiableSet(geneCandidateNames);
            }

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
        Set<String> genes = new HashSet<>();
        if (this.exome != null) {
            genes.addAll(this.exome.getGenes());
        }
        if (this.candidateGenes != null) {
            genes.addAll(this.candidateGenes);
        }
        return genes;
    }

    @Override
    public List<Variant> getTopVariants(String gene, int k)
    {
        // TODO add manually entered variants
        List<Variant> variants;
        if (this.exome == null) {
            variants = new ArrayList<>();
        } else {
            variants = this.exome.getTopVariants(gene, k);
        }
        return variants;
    }

    @Override
    public Double getGeneScore(String gene)
    {
        // If gene is a candidate gene, score is 1.0
        // Else, score is exomizer score * 0.5
        Double score = null;
        if (this.candidateGenes != null && this.candidateGenes.contains(gene)) {
            score = 1.0;
        } else if (this.exome != null) {
            score = this.exome.getGeneScore(gene);
            if (score != null) {
                score *= 0.5;
            }
        }

        return score;
    }

    @Override
    public String getGenesStatus()
    {
        return this.genesStatus;
    }

    @Override
    public boolean hasExomeData()
    {
        return this.exome != null;
    }
}
