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
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.sf.json.JSONArray;

/**
 * This class represents the collective genotype information in a patient, both from candidate genes and from exome
 * data.
 *
 * @version $Id$
 */
public class DefaultPatientGenotype implements PatientGenotype
{
    /** Factory for loading exome data. */
    protected static ExomeManager exomeManager;

    /** Logging helper object. */
    private static Logger logger = LoggerFactory.getLogger(DefaultPatientGenotype.class);

    /** The candidate genes for the patient. */
    protected Set<String> candidateGenes;

    /** The exome data for the patient. */
    protected Exome exome;

    /**
     * Constructor for a {@link #PatientGenotype} object, representing the candidate genes and exome sequence data for
     * the given Patient.
     *
     * @param patient the patient object
     */
    public DefaultPatientGenotype(Patient patient)
    {
        // Initialize static exome manager
        if (exomeManager == null) {
            ComponentManager componentManager = ComponentManagerRegistry.getContextComponentManager();
            try {
                exomeManager = componentManager.getInstance(ExomeManager.class);
            } catch (ComponentLookupException e) {
                logger.error("Unable to look up ExomeManager: " + e.toString());
            }
        }

        if (exomeManager != null) {
            this.exome = exomeManager.getExome(patient);
        }
        this.candidateGenes = getPatientCandidateGeneNames(patient);
    }

    /**
     * Return a set of the names of candidate genes listed for the given patient.
     *
     * @param p the {@link #Patient}
     * @return a (potentially-empty) unmodifiable set of the names of candidate genes
     */
    private static Set<String> getPatientCandidateGeneNames(Patient p)
    {
        PatientData<Map<String, String>> genesData = null;
        if (p != null) {
            genesData = p.getData("genes");
        }
        if (genesData != null) {
            Set<String> geneNames = new HashSet<String>();
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
                geneNames.add(geneName);
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
        List<Variant> variants;
        if (this.exome == null) {
            variants = new ArrayList<Variant>();
        } else {
            variants = this.exome.getTopVariants(gene);
        }
        return variants;
    }

    @Override
    public Iterable<String> iterTopGenes()
    {
        // Get score for every gene
        Map<String, Double> geneScores = new HashMap<String, Double>();
        for (String gene : this.getGenes()) {
            geneScores.put(gene, this.getGeneScore(gene));
        }

        // Gene genes, in order of decreasing score
        List<Map.Entry<String, Double>> sortedGenes = new ArrayList<Map.Entry<String, Double>>(geneScores.entrySet());
        Collections.sort(sortedGenes, new Comparator<Map.Entry<String, Double>>()
        {
            @Override
            public int compare(Map.Entry<String, Double> e1, Map.Entry<String, Double> e2)
            {
                return Double.compare(e2.getValue(), e1.getValue());
            }
        });

        // Get gene names from sorted list of map entries
        List<String> geneNames = new ArrayList<String>(sortedGenes.size());
        for (Map.Entry<String, Double> entry : sortedGenes) {
            geneNames.add(entry.getKey());
        }

        return geneNames;
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

    @Override
    public JSONArray toJSON()
    {
        // TODO: add candidate genes
        if (this.exome != null) {
            return this.exome.toJSON();
        } else {
            return new JSONArray();
        }
    }
}
