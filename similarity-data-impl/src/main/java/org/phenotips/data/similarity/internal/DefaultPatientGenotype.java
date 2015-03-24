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

import org.phenotips.data.Patient;
import org.phenotips.data.PatientData;
import org.phenotips.data.similarity.Genotype;
import org.phenotips.data.similarity.PatientGenotype;
import org.phenotips.data.similarity.Variant;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
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
    /** Logging helper object. */
    private static Logger logger = LoggerFactory.getLogger(DefaultPatientGenotype.class);

    /** The candidate genes for the patient. */
    private Collection<String> candidateGenes;

    /** The exome data for the patient. */
    private Genotype genotype;

    /**
     * Constructor for a PatientGenotype object, representing the candidate genes and exome sequence data for the given
     * Patient.
     *
     * @param patient the patient object
     */
    public DefaultPatientGenotype(Patient patient)
    {
        this.genotype = genotype;
        this.candidateGenes = getPatientCandidateGeneNames(patient);
    }

    /**
     * Return a collection of the names of candidate genes listed for the given patient.
     *
     * @param p the patient
     * @return a (potentially-empty) unmodifiable collection of the names of candidate genes
     */
    private static Collection<String> getPatientCandidateGeneNames(Patient p)
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
        return (candidateGenes != null && !candidateGenes.isEmpty()) || genotype != null;
    }

    @Override
    public Collection<String> getCandidateGenes()
    {
        if (candidateGenes == null) {
            return Collections.emptySet();
        } else {
            return Collections.unmodifiableCollection(candidateGenes);
        }
    }

    @Override
    public Set<String> getGenes()
    {
        Set<String> genes = new HashSet<String>();
        if (genotype != null) {
            genes.addAll(genotype.getGenes());
        }
        if (candidateGenes != null) {
            genes.addAll(candidateGenes);
        }
        return Collections.unmodifiableSet(genes);
    }

    @Override
    public Variant getTopVariant(String gene, int k)
    {
        return genotype == null ? null : genotype.getTopVariant(gene, k);
    }

    @Override
    public List<Variant> getTopVariants(String gene)
    {
        List<Variant> variants;
        if (genotype == null) {
            variants = new ArrayList<Variant>();
        } else {
            variants = genotype.getTopVariants(gene);
        }
        return variants;
    }

    @Override
    public Double getGeneScore(String gene)
    {
        double score = 0.0;
        if (genotype != null) {
            score = genotype.getGeneScore(gene);
        }
        // Potentially boost score if candidate gene
        if (candidateGenes != null && candidateGenes.contains(gene)) {
            // Score of 1.0 for 1 candidate gene, 0.95 for each of 2, exponentially decreasing
            score = Math.max(score, Math.pow(0.95, candidateGenes.size() - 1));
        }
        return score;
    }

    /**
     * {@inheritDoc}
     * 
     * @see org.phenotips.data.similarity.Genotype#toJSON()
     */
    @Override
    public JSONArray toJSON()
    {
        // TODO: add candidate genes
        if (this.genotype != null) {
            return this.genotype.toJSON();
        } else {
            return new JSONArray();
        }
    }
}
