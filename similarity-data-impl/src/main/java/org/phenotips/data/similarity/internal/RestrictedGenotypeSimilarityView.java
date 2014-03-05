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
import org.phenotips.data.PatientRepository;
import org.phenotips.data.similarity.AccessType;
import org.phenotips.data.similarity.ExternalToolJobManager;
import org.phenotips.data.similarity.Genotype;
import org.phenotips.data.similarity.GenotypeSimilarityView;
import org.phenotips.data.similarity.Variant;

import org.xwiki.component.manager.ComponentLookupException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.tuple.Pair;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

/**
 * Implementation of {@link GenotypeSimilarityView} that reveals the full patient information if the user has full
 * access to the patient, and only matching reference information if the patient is matchable.
 * 
 * @version $Id$
 * @since
 */
public class RestrictedGenotypeSimilarityView implements GenotypeSimilarityView
{
    /** Variant score threshold between non-frameshift and splicing. */
    private static final double KO_THRESHOLD = 0.87;

    /** The matched genotype to represent. */
    private Genotype matchGenotype;

    /** The reference genotype against which to compare. */
    private Genotype refGenotype;

    /** The access type the user has to the match patient. */
    private AccessType access;

    private Map<String, Double> geneScores;

    private Map<String, Pair<Pair<Variant, Variant>, Pair<Variant, Variant>>> geneVariants;

    private double getVariantScore(Variant v)
    {
        if (v == null) {
            return 0.0;
        } else {
            return v.getScore();
        }
    }

    /**
     * Simple constructor passing the {@link #match matched patient}, the {@link #reference reference patient}, and the
     * {@link #access patient access type}.
     * 
     * @param match the matched patient to represent
     * @param reference the reference patient against which to compare
     * @param access the access type the user has to the match patient
     */
    public RestrictedGenotypeSimilarityView(Patient match, Patient reference, AccessType access)
    {
        ExternalToolJobManager<Genotype> em = null;
        try {
            em =
                ComponentManagerRegistry.getContextComponentManager().getInstance(ExternalToolJobManager.class,
                    "exomizer");
        } catch (ComponentLookupException e) {
            assert false : "ExternalToolJobManager could not be retrieved";
        }

        String matchId = match.getDocument().getName();
        String refId = reference.getDocument().getName();
        this.matchGenotype = em.getResult(matchId);
        this.refGenotype = em.getResult(refId);
        this.access = access;

        if (this.matchGenotype == null && this.refGenotype == null) {

        }

        // Need patient repository for looking up patients by ID
        PatientRepository patientRepo = null;
        try {
            patientRepo = ComponentManagerRegistry.getContextComponentManager().getInstance(PatientRepository.class);
        } catch (ComponentLookupException e) {
            // Should never happen.
        }

        // Load genotypes for all other patients
        Set<String> otherGenotypedIds = new HashSet<String>(em.getAllCompleted());
        otherGenotypedIds.remove(matchId);
        otherGenotypedIds.remove(refId);
        this.geneScores = new HashMap<String, Double>();
        this.geneVariants = new HashMap<String, Pair<Pair<Variant, Variant>, Pair<Variant, Variant>>>();

        for (String gene : getGenes()) {
            Pair<Variant, Variant> refVariants = this.refGenotype.getTopVariants(gene);
            Pair<Variant, Variant> matchVariants = this.matchGenotype.getTopVariants(gene);

            // Set the variant harmfulness threshold at the min of the two patients'
            double domThresh =
                Math.min(getVariantScore(refVariants.getLeft()), getVariantScore(matchVariants.getLeft()));
            double recThresh =
                Math.min(getVariantScore(refVariants.getRight()), getVariantScore(matchVariants.getRight()));

            // Start the dominant and recessive scores as the min of the two patient's phenotype scores
            double geneScore = Math.min(this.refGenotype.getGeneScore(gene), this.matchGenotype.getGeneScore(gene));
            double domScore = geneScore;
            double recScore = geneScore;

            // XXX: heuristic hack
            if (geneScore < 0.1) {
                continue;
            }

            for (String patientId : otherGenotypedIds) {
                Genotype otherGt = em.getResult(patientId);
                if (otherGt != null && otherGt.getGeneScore(gene) != null) {
                    // Patient otherPatient = patientRepo.getPatientById(patientId);
                    double otherPhenoScore = 0.5;

                    // Adjust gene score if other patient has worse scores in gene, weighted by patient similarity
                    Pair<Variant, Variant> otherVariants = otherGt.getTopVariants(gene);
                    if (getVariantScore(otherVariants.getLeft()) >= domThresh) {
                        domScore *= 1 - otherPhenoScore;
                    }
                    if (getVariantScore(otherVariants.getRight()) >= recThresh) {
                        recScore *= 1 - otherPhenoScore;
                    }
                }
            }

            geneScores.put(gene, Math.max(domScore, recScore));
            geneVariants.put(gene, Pair.of(refVariants, matchVariants));
        }
    }

    @Override
    public Set<String> getGenes()
    {
        if (this.refGenotype == null || this.matchGenotype == null) {
            return Collections.emptySet();
        }
        // Get union of genes mutated in both patients
        Set<String> sharedGenes = new HashSet<String>(this.refGenotype.getGenes());
        Set<String> otherGenes = this.matchGenotype.getGenes();
        sharedGenes.retainAll(otherGenes);
        return Collections.unmodifiableSet(sharedGenes);
    }

    @Override
    public Double getGeneScore(String gene)
    {
        return this.matchGenotype.getGeneScore(gene);
    }

    @Override
    public Pair<Variant, Variant> getTopVariants(String gene)
    {
        // TODO Auto-generated method stub
        return this.matchGenotype.getTopVariants(gene);
    }

    @Override
    public double getScore()
    {
        return Collections.max(geneScores.values());
    }

    private JSONArray getVariantPairJSON(Pair<Variant, Variant> vp)
    {
        if (vp == null) {
            return null;
        } else {
            JSONArray varJSON = new JSONArray();
            if (vp.getLeft() != null)
                varJSON.add(vp.getLeft().toJSON());
            if (vp.getRight() != null)
                varJSON.add(vp.getRight().toJSON());
            return varJSON;
        }
    }

    @Override
    public JSONArray toJSON()
    {
        JSONArray genesJSON = new JSONArray();
        if (this.refGenotype == null || this.matchGenotype == null || this.access.isPrivateAccess()) {
            return genesJSON;
        }

        // Gene genes, in order of decreasing score
        List<Map.Entry<String, Double>> genes = new ArrayList<Map.Entry<String, Double>>(geneScores.entrySet());
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
            JSONObject geneObject = new JSONObject();
            geneObject.element("gene", gene);

            Pair<Pair<Variant, Variant>, Pair<Variant, Variant>> variants = this.geneVariants.get(gene);

            JSONObject refJSON = new JSONObject();
            refJSON.element("score", this.refGenotype.getGeneScore(gene));
            JSONArray refVarJSON = getVariantPairJSON(variants.getLeft());
            if (refVarJSON != null) {
                refJSON.element("variants", refVarJSON);
            }
            geneObject.element("reference", refJSON);

            JSONObject matchJSON = new JSONObject();
            matchJSON.element("score", this.matchGenotype.getGeneScore(gene));
            JSONArray matchVarJSON = getVariantPairJSON(variants.getRight());
            if (matchVarJSON != null) {
                matchJSON.element("variants", matchVarJSON);
            }
            geneObject.element("match", matchJSON);

            genesJSON.add(geneObject);
        }

        return genesJSON;
    }

}
