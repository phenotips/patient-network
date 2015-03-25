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
import org.phenotips.data.similarity.AccessType;
import org.phenotips.data.similarity.PatientGenotype;
import org.phenotips.data.similarity.PatientGenotypeManager;
import org.phenotips.data.similarity.PatientGenotypeSimilarityView;
import org.phenotips.data.similarity.Variant;

import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.component.manager.ComponentManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

/**
 * Implementation of {@link PatientGenotypeSimilarityView} that reveals the full patient information if the user has
 * full access to the patient, and only matching reference information if the patient is matchable.
 *
 * @version $Id$
 * @since
 */
public class RestrictedGenotypeSimilarityView implements PatientGenotypeSimilarityView
{
    /** The number of genes to show in the JSON output. */
    private static final int MAX_GENES_SHOWN = 10;

    /** Logging helper object. */
    private static Logger logger = LoggerFactory.getLogger(RestrictedGenotypeSimilarityView.class);

    /** Manager to allow access to underlying genotype data. */
    private static PatientGenotypeManager genotypeManager;

    /** The matched patient to represent. */
    private Patient match;

    /** The reference patient against which to compare. */
    private Patient reference;

    /** The matched genotype to represent. */
    private PatientGenotype matchGenotype;

    /** The reference genotype against which to compare. */
    private PatientGenotype refGenotype;

    /** The access type the user has to the match patient. */
    private AccessType access;

    /** The similarity score for all genes. */
    private Map<String, Double> geneScores;

    /**
     * Simple constructor passing the {@link #match matched patient}, the {@link #reference reference patient}, and the
     * {@link #access patient access type}.
     *
     * @param match the matched patient, which is represented by this match
     * @param reference the reference patient, which the match is against
     * @param access the access type the user has to the match patient
     * @throws IllegalArgumentException if one of the patients is {@code null}
     */
    public RestrictedGenotypeSimilarityView(Patient match, Patient reference, AccessType access)
        throws IllegalArgumentException
    {
        if (match == null || reference == null) {
            throw new IllegalArgumentException("Similar patients require both a match and a reference");
        }
        this.match = match;
        this.reference = reference;
        this.access = access;

        if (genotypeManager == null) {
            genotypeManager = getGenotypeManager();
        }

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
     * Lookup component genotype manager.
     * 
     * @return {@link #PatientGenotypeManager} component
     */
    private PatientGenotypeManager getGenotypeManager()
    {
        ComponentManager componentManager = ComponentManagerRegistry.getContextComponentManager();
        try {
            return componentManager.getInstance(PatientGenotypeManager.class);
        } catch (ComponentLookupException e) {
            logger.error("Unable to look up PatientGenotypeManager: " + e.toString());
        }
        return null;
    }

    /**
     * Score genes with variants in both patients, and sets 'geneScores' and 'geneVariants' fields accordingly.
     */
    private void matchGenes()
    {
        this.geneScores = new HashMap<String, Double>();
        for (String gene : getGenes()) {
            // Compute gene score based on the genotype scores for the two patients
            double geneScore = (this.refGenotype.getGeneScore(gene)
                + this.matchGenotype.getGeneScore(gene)) / 2.0;

            // Save score and variants for display
            this.geneScores.put(gene, geneScore);
        }
    }

    @Override
    public Set<String> getGenes()
    {
        if (this.matchGenotype == null || this.refGenotype == null) {
            return Collections.emptySet();
        } else {
            // Get intersection of genes mutated/candidate in both patients
            Set<String> sharedGenes = new HashSet<String>(this.refGenotype.getGenes());
            sharedGenes.retainAll(this.matchGenotype.getGenes());
            return Collections.unmodifiableSet(sharedGenes);
        }
    }

    @Override
    public Double getGeneScore(String gene)
    {
        return this.matchGenotype == null ? null : this.matchGenotype.getGeneScore(gene);
    }

    @Override
    public Variant getTopVariant(String gene, int k)
    {
        return this.matchGenotype == null ? null : this.matchGenotype.getTopVariant(gene, k);
    }

    @Override
    public List<Variant> getTopVariants(String gene)
    {
        if (this.matchGenotype == null) {
            List<Variant> topVariants = Collections.emptyList();
            return topVariants;
        } else {
            return this.matchGenotype.getTopVariants(gene);
        }
    }

    @Override
    public double getScore()
    {
        if (this.geneScores == null || this.geneScores.isEmpty()) {
            return 0.0;
        } else {
            return Collections.max(this.geneScores.values());
        }
    }

    @Override
    public boolean hasGenotypeData()
    {
        return this.refGenotype != null && this.matchGenotype != null && this.refGenotype.hasGenotypeData()
            && this.matchGenotype.hasGenotypeData();
    }

    /**
     * Return the candidate genes shared by both patients in the view.
     * 
     * @see org.phenotips.data.similarity.PatientGenotype#getCandidateGenes()
     */
    @Override
    public Collection<String> getCandidateGenes()
    {
        Set<String> sharedGenes = new HashSet<String>();
        if (this.matchGenotype != null && this.refGenotype != null) {
            Collection<String> matchGenes = this.matchGenotype.getCandidateGenes();
            Collection<String> refGenes = this.refGenotype.getCandidateGenes();
            sharedGenes.addAll(matchGenes);
            sharedGenes.retainAll(refGenes);
        }
        return Collections.unmodifiableCollection(sharedGenes);
    }

    /**
     * Get a JSON representation of the genotype of a patient with a candidate gene.
     *
     * @return a JSON array of one variant corresponding to a candidate gene.
     */
    private JSONArray getCandidateGeneJSON()
    {
        JSONArray result = new JSONArray();
        JSONObject variant = new JSONObject();
        variant.element("score", 1.0);
        result.add(variant);
        return result;
    }

    /**
     * Get the JSON for a patient's variants in a protected manner.
     *
     * @param gene the gene to display variants for.
     * @param genotype the patient genotype.
     * @param restricted if false, the variants are displayed regardless of the current accessType
     * @return JSON for the patient's variants, an empty array if there are no variants
     */
    private JSONObject getPatientVariantsJSON(String gene, PatientGenotype genotype, boolean restricted)
    {
        JSONObject patientJSON = new JSONObject();

        List<Variant> variants = genotype.getTopVariants(gene);
        Collection<String> candidateGenes = genotype.getCandidateGenes();

        JSONArray variantsJSON = new JSONArray();
        if (variants != null && !variants.isEmpty()) {
            // Show the top variants in the patient, according to access level
            for (Variant v : variants) {
                if (!restricted || this.access.isOpenAccess()) {
                    variantsJSON.add(v.toJSON());
                } else if (this.access.isLimitedAccess()) {
                    // Only show score if limited access
                    JSONObject varJSON = new JSONObject();
                    varJSON.element("score", v.getScore());
                    variantsJSON.add(varJSON);
                }
            }
        } else if (candidateGenes != null && candidateGenes.contains(gene)) {
            // Show candidate gene if no variants in gene
            variantsJSON = getCandidateGeneJSON();
        }

        // Only add element if there are variants
        if (!variantsJSON.isEmpty()) {
            patientJSON.element("variants", variantsJSON);
        }
        return patientJSON;
    }

    /**
     * Serialize the variants in both patients in a single gene.
     *
     * @param gene the gene to serialize variants for.
     * @return the JSON for the variants in the given gene
     */
    private JSONObject getGeneVariantsJSON(String gene)
    {
        JSONObject variantsJSON = new JSONObject();
        if (this.refGenotype != null) {
            variantsJSON.element("reference", getPatientVariantsJSON(gene, this.refGenotype, false));
        }
        if (this.matchGenotype != null) {
            variantsJSON.element("match", getPatientVariantsJSON(gene, this.matchGenotype, true));
        }
        return variantsJSON;
    }

    @Override
    public JSONArray toJSON()
    {
        if (this.geneScores == null || this.access.isPrivateAccess()) {
            return null;
        }

        JSONArray genesJSON = new JSONArray();
        // Gene genes, in order of decreasing score
        List<Map.Entry<String, Double>> genes = new ArrayList<Map.Entry<String, Double>>(this.geneScores.entrySet());
        Collections.sort(genes, new Comparator<Map.Entry<String, Double>>()
        {
            @Override
            public int compare(Map.Entry<String, Double> e1, Map.Entry<String, Double> e2)
            {
                return Double.compare(e2.getValue(), e1.getValue());
            }
        });

        int iGene = 0;
        for (Map.Entry<String, Double> geneEntry : genes) {
            // Include at most top N genes
            if (iGene >= MAX_GENES_SHOWN) {
                break;
            }
            String gene = geneEntry.getKey();
            Double score = geneEntry.getValue();

            JSONObject geneObject = new JSONObject();
            geneObject.element("gene", gene);
            geneObject.element("score", score);
            geneObject.accumulateAll(getGeneVariantsJSON(gene));
            genesJSON.add(geneObject);

            iGene++;
        }
        return genesJSON;
    }

}
