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
import org.phenotips.data.similarity.PatientGenotypeManager;
import org.phenotips.data.similarity.PatientGenotypeSimilarityView;
import org.phenotips.data.similarity.Variant;

import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.component.manager.ComponentManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

/**
 * Implementation of {@link PatientGenotypeSimilarityView} that always reveals the full patient information.
 *
 * @version $Id$
 * @since 1.0M6
 */
public class DefaultGenotypeSimilarityView extends AbstractGenotypeSimilarityView
{
    /** Logging helper object. */
    private static Logger logger = LoggerFactory.getLogger(DefaultGenotypeSimilarityView.class);

    /** Manager to allow access to underlying genotype data. */
    protected static PatientGenotypeManager genotypeManager;

    /** The similarity score for all genes. */
    protected Map<String, Double> geneScores;

    /**
     * Simple constructor passing the {@link #match matched patient}, the {@link #reference reference patient}, and the
     * {@link #access patient access type}.
     *
     * @param match the matched patient, which is represented by this match
     * @param reference the reference patient, which the match is against
     * @param access the access type the user has to the match patient
     * @throws IllegalArgumentException if one of the patients is {@code null}
     */
    public DefaultGenotypeSimilarityView(Patient match, Patient reference, AccessType access)
        throws IllegalArgumentException
    {
        super(match, reference, access);

        if (genotypeManager == null) {
            genotypeManager = getGenotypeManager();
        }

        if (genotypeManager != null) {
            // Store genotype information
            this.matchGenotype = genotypeManager.getGenotype(this.match);
            this.refGenotype = genotypeManager.getGenotype(this.reference);
        }

        // Match candidate genes and any genotype available
        this.geneScores = new HashMap<String, Double>();
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
            Double refScore = this.refGenotype.getGeneScore(gene);
            Double matchScore = this.matchGenotype.getGeneScore(gene);
            if (refScore == null) {
                refScore = 0.0;
            }
            if (matchScore == null) {
                matchScore = 0.0;
            }
            double geneScore = (refScore + matchScore) / 2.0;

            // Save score and variants for display
            this.geneScores.put(gene, geneScore);
        }
    }

    @Override
    public double getScore()
    {
        if (this.geneScores.isEmpty()) {
            return 0.0;
        } else {
            return Collections.max(this.geneScores.values());
        }
    }

    @Override
    public Iterable<String> iterTopGenes()
    {
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

        List<String> geneNames = new ArrayList<String>(genes.size());
        for (int i = 0; i < genes.size(); i++) {
            geneNames.add(genes.get(i).getKey());
        }

        return geneNames;
    }

    /**
     * Serialize the variants in a particular patient in a single gene.
     *
     * @param variants a list of {@link #Variant}s to serialize
     * @return JSON for the patient's variants, an empty object if there are no variants
     */
    protected JSONObject getVariantsJSON(List<Variant> variants)
    {
        JSONObject geneJSON = new JSONObject();

        JSONArray variantsJSON = new JSONArray();
        if (variants != null && !variants.isEmpty()) {
            // Show the top variants in the patient
            for (Variant v : variants) {
                variantsJSON.add(v.toJSON());
            }
        }

        // Only add element if there are variants
        if (!variantsJSON.isEmpty()) {
            geneJSON.element("variants", variantsJSON);
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
            List<Variant> variants = this.refGenotype.getTopVariants(gene);
            variantsJSON.element("reference", getVariantsJSON(variants));
        }
        if (this.matchGenotype != null) {
            // Use potentially access-controlled method to try to get variants
            List<Variant> variants = this.getTopVariants(gene);
            variantsJSON.element("match", getVariantsJSON(variants));
        }
        return variantsJSON;
    }

    @Override
    public JSONArray toJSON()
    {
        if (!this.hasGenotypeData()) {
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

        for (Map.Entry<String, Double> geneEntry : genes) {
            String gene = geneEntry.getKey();
            Double score = geneEntry.getValue();

            JSONObject geneObject = new JSONObject();
            geneObject.element("gene", gene);
            geneObject.element("score", score);
            geneObject.accumulateAll(getGeneJSON(gene));
            genesJSON.add(geneObject);
        }
        return genesJSON;
    }
}
