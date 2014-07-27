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
import org.phenotips.data.PatientRepository;
import org.phenotips.data.similarity.AccessType;
import org.phenotips.data.similarity.ExternalToolJobManager;
import org.phenotips.data.similarity.Genotype;
import org.phenotips.data.similarity.GenotypeSimilarityView;
import org.phenotips.data.similarity.PatientSimilarityViewFactory;
import org.phenotips.data.similarity.Variant;

import org.xwiki.cache.CacheException;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.component.manager.ComponentManager;

import java.util.ArrayList;
import java.util.Collection;
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
    /** The number of genes to show in the JSON output. */
    private static final int MAX_GENES_SHOWN = 5;

    /** Cache for storing symmetric pairwise patient similarity scores. */
    private static PairCache<Double> similarityScoreCache;

    /** Logging helper object. */
    private final Logger logger = LoggerFactory.getLogger(DefaultPatientSimilarityView.class);

    /** The matched patient to represent. */
    private Patient match;

    /** The reference patient against which to compare. */
    private Patient reference;

    /** The matched genotype to represent. */
    private Genotype matchGenotype;

    /** The reference genotype against which to compare. */
    private Genotype refGenotype;

    /** The access type the user has to the match patient. */
    private AccessType access;

    /** Access to the patient repository for looking up other patients by id. */
    private PatientRepository patientRepo;

    /** Access to the patient view factory to get other patient phenotypic similarities. */
    private PatientSimilarityViewFactory patientViewFactory;

    /** Access to the ExomizerJobManager. */
    private ExternalToolJobManager<Genotype> exomizerManager;

    /** The similarity score for all genes. */
    private Map<String, Double> geneScores;

    /** The top variants in each patient for all shared genes. */
    private Map<String, Variant[][]> geneVariants;

    /**
     * Simple constructor passing the {@link #match matched patient}, the {@link #reference reference patient}, and the
     * {@link #access patient access type}.
     * 
     * @param match the matched patient to represent
     * @param reference the reference patient against which to compare
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

        if (similarityScoreCache == null) {
            try {
                similarityScoreCache = new PairCache<Double>();
            } catch (CacheException e) {
                this.logger.error("Unable to create patient similarity score cache: " + e.toString());
            }
        }

        // Load components
        try {
            ComponentManager componentManager = ComponentManagerRegistry.getContextComponentManager();
            this.patientRepo = componentManager.getInstance(PatientRepository.class);
            this.patientViewFactory = componentManager.getInstance(PatientSimilarityViewFactory.class, "restricted");
            this.exomizerManager = componentManager.getInstance(ExternalToolJobManager.class, "exomizer");
        } catch (ComponentLookupException e) {
            this.logger.error("Unable to load component: " + e.toString());
        }

        String matchId = match.getId();
        String refId = reference.getId();
        if (matchId != null && refId != null && this.exomizerManager != null) {
            // Full genotype similarity breakdown is possible
            this.matchGenotype = this.exomizerManager.getResult(matchId);
            this.refGenotype = this.exomizerManager.getResult(refId);
        }

        // Match candidate genes and any genotype available
        matchGenes();
    }

    /**
     * Get the phenotype similarity between two patients in a cached manner.
     * 
     * @param p1 the first patient
     * @param p2 the second patient
     * @return the symmetric similarity score between them, potentially cached
     */
    private double getPatientSimilarity(Patient p1, Patient p2)
    {
        String id1 = p1.getId();
        String id2 = p2.getId();
        if (id1.compareTo(id2) > 0) {
            String temp = id1;
            id1 = id2;
            id2 = temp;
        }
        String key = id1 + '|' + id2;
        Double score = similarityScoreCache.get(key);
        if (score == null) {
            score = this.patientViewFactory.makeSimilarPatient(p1, p2).getScore();
            similarityScoreCache.set(id1, id2, key, score);
        }
        return score;
    }

    /**
     * Get the similarity score for another patient, relative to the pair of patients being matched.
     * 
     * @param patientId the String id of the other patient
     * @param otherSimScores a cache of such scores, added to by calling this method
     * @return the similarity score of the patient
     */
    private double getOtherPatientSimilarity(String patientId, Map<String, Double> otherSimScores)
    {
        Double otherSimScore = otherSimScores.get(patientId);

        if (otherSimScore == null) {
            Patient otherPatient = this.patientRepo.getPatientById(patientId);
            if (otherPatient == null) {
                otherSimScore = 0.0;
            } else {
                otherSimScore =
                    Math.max(getPatientSimilarity(otherPatient, this.match),
                        getPatientSimilarity(otherPatient, this.reference));
            }
            otherSimScores.put(patientId, otherSimScore);
        }
        return otherSimScore;
    }

    /**
     * Get the score for a gene, given the score threshold for each inheritance model for the pair of patients.
     * 
     * @param gene the gene being score
     * @param domThresh the dominant inheritance model score threshold
     * @param recThresh the recessive inheritance model score threshold
     * @param otherGenotypedIds the patient ids of other patients with genotype information, for comparison
     * @param otherSimScores the cached phenotype scores of other patients
     * @return the score for the gene, between [0, 1], with 0 corresponding to a poor score
     */
    private double scoreGene(String gene, double domThresh, double recThresh, Set<String> otherGenotypedIds,
        Map<String, Double> otherSimScores)
    {
        double geneScore = Math.min(this.refGenotype.getGeneScore(gene), this.matchGenotype.getGeneScore(gene));
        // XXX: heuristic hack
        if (geneScore < 0.0001) {
            return 0.0;
        }
        // Adjust upwards the starting phenotype score to account for exomizer bias (almost all scores are < 0.7)
        // geneScore = Math.min(geneScore / 0.7, 1);

        double domScore = geneScore * domThresh;
        double recScore = geneScore * recThresh;

        // Quit early if things are going poorly
        if (Math.max(domScore, recScore) < 0.0001) {
            return 0.0;
        }

        for (String patientId : otherGenotypedIds) {
            Genotype otherGt = this.exomizerManager.getResult(patientId);
            if (otherGt != null && otherGt.getGeneScore(gene) != null) {
                double otherDomScore = getVariantScore(otherGt.getTopVariant(gene, 0)) + 0.01;
                double otherRecScore = getVariantScore(otherGt.getTopVariant(gene, 1)) + 0.01;
                if ((domScore > 0.0001 && otherDomScore >= domThresh) ||
                    (recScore > 0.0001 && otherRecScore >= recThresh)) {
                    // Adjust gene score if other patient has worse scores in gene, weighted by patient similarity
                    double otherSimScore = Math.min(getOtherPatientSimilarity(patientId, otherSimScores) + 0.2, 1);
                    double domDiff = otherDomScore - domThresh;
                    double recDiff = otherRecScore - recThresh;
                    if (domDiff > 0.0) {
                        domScore *= Math.pow(otherSimScore, 1 - domDiff);
                    }
                    if (recDiff > 0.0) {
                        recScore *= Math.pow(otherSimScore, 1 - recDiff);
                    }
                }
            }

            // Quit early if things are going poorly
            if (Math.max(domScore, recScore) < 0.0001) {
                return 0.0;
            }
        }

        return Math.pow(Math.max(domScore, recScore), 0.1);
    }

    /**
     * Return a collection of the names of candidate genes listed for the patient.
     * 
     * @param p the patient
     * @return a (potentially-empty) unmodifiable collection of the names of candidate genes
     */
    private Collection<String> getCandidateGeneNames(Patient p)
    {
        PatientData<Map<String, String>> genesData = p.getData("genes");
        if (genesData != null) {
            Set<String> geneNames = new HashSet<String>();
            Iterator<Map<String, String>> iterator = genesData.iterator();
            while (iterator.hasNext()) {
                Map<String, String> geneInfo = iterator.next();
                String geneName = geneInfo.get("gene");
                if (geneName != null) {
                    geneNames.add(geneName);
                }
            }
            return Collections.unmodifiableSet(geneNames);
        }
        return Collections.emptySet();
    }

    /**
     * Score genes with variants in both patients, and sets 'geneScores' and 'geneVariants' fields accordingly.
     */
    private void matchGenes()
    {
        this.geneScores = new HashMap<String, Double>();
        this.geneVariants = new HashMap<String, Variant[][]>();

        // Auto-match shared candidate genes
        Collection<String> matchGenes = getCandidateGeneNames(this.match);
        Collection<String> refGenes = getCandidateGeneNames(this.reference);
        Set<String> sharedGenes = new HashSet<String>();
        sharedGenes.addAll(matchGenes);
        sharedGenes.retainAll(refGenes);
        for (String gene : sharedGenes) {
            Variant[][] topVariants = new Variant[2][2];
            topVariants[0][0] = new CandidateGene();
            topVariants[0][1] = null;
            topVariants[1][0] = new CandidateGene();
            topVariants[1][1] = null;
            this.geneScores.put(gene, 1.0);
            this.geneVariants.put(gene, topVariants);
        }

        // Add candidate genes to each patient's genotype
        if (this.matchGenotype != null) {
            for (String gene : matchGenes) {
                Variant v = new CandidateGene();
                this.matchGenotype.addVariant(gene, v);
            }
        }
        if (this.refGenotype != null) {
            for (String gene : refGenes) {
                Variant v = new CandidateGene();
                this.refGenotype.addVariant(gene, v);
            }
        }

        if (this.exomizerManager != null && this.matchGenotype != null && this.refGenotype != null) {
            // Load genotypes for all other patients
            Set<String> otherGenotypedIds = new HashSet<String>(this.exomizerManager.getAllCompleted());
            otherGenotypedIds.remove(this.match.getId());
            otherGenotypedIds.remove(this.reference.getId());
            Map<String, Double> otherSimScores = new HashMap<String, Double>();

            for (String gene : getGenes()) {
                if (this.geneScores.containsKey(gene)) {
                    continue;
                }

                // Set the variant harmfulness threshold at the min of the two patients'
                Variant[][] topVariants = new Variant[2][2];
                // TODO: rework to handle candidate genes better so they aren't listed twice in json
                topVariants[0][0] = this.refGenotype.getTopVariant(gene, 0);
                topVariants[0][1] = this.refGenotype.getTopVariant(gene, 1);
                topVariants[1][0] = this.matchGenotype.getTopVariant(gene, 0);
                topVariants[1][1] = this.matchGenotype.getTopVariant(gene, 1);

                double domThresh = Math.min(getVariantScore(topVariants[0][0]), getVariantScore(topVariants[1][0]));
                double recThresh = Math.min(getVariantScore(topVariants[0][1]), getVariantScore(topVariants[1][1]));
                double geneScore = scoreGene(gene, domThresh, recThresh, otherGenotypedIds, otherSimScores);

                if (geneScore < 0.0001) {
                    continue;
                }
                this.geneScores.put(gene, geneScore);
                this.geneVariants.put(gene, topVariants);
            }
        }
    }

    /**
     * Get the score of a variant.
     * 
     * @param v the variant (or null)
     * @return the score of the variant (or 0.0 if v is null)
     */
    private double getVariantScore(Variant v)
    {
        if (v == null) {
            return 0.0;
        } else {
            return v.getScore();
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
        if (this.matchGenotype == null) {
            return null;
        } else {
            return this.matchGenotype.getGeneScore(gene);
        }
    }

    @Override
    public Variant getTopVariant(String gene, int k)
    {
        if (this.matchGenotype == null) {
            return null;
        } else {
            return this.matchGenotype.getTopVariant(gene, k);
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

    /**
     * Clear all entries in the similarity score cache.
     */
    public static void clearCache()
    {
        if (similarityScoreCache != null) {
            similarityScoreCache.removeAll();
        }
    }

    /**
     * Get the JSON for an array of variants.
     * 
     * @param vs an array of Variant objects
     * @param restricted if false, the variants are displayed regardless of the current accessType
     * @return JSON for the variants, an empty array if there are no variants
     */
    private JSONArray getVariantsJSON(Variant[] vs, boolean restricted)
    {
        JSONArray varJSON = new JSONArray();
        if (vs == null) {
            return varJSON;
        }
        for (Variant v : vs) {
            if (v != null) {
                if (!restricted || this.access.isOpenAccess()) {
                    varJSON.add(v.toJSON());
                } else if (this.access.isLimitedAccess()) {
                    // Only show score if limited access
                    JSONObject variantJSON = new JSONObject();
                    variantJSON.element("score", v.getScore());
                    varJSON.add(variantJSON);
                }
            }
        }
        return varJSON;
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
            // Include at most top 5 genes
            if (iGene >= MAX_GENES_SHOWN) {
                break;
            }
            String gene = geneEntry.getKey();
            Double score = geneEntry.getValue();

            JSONObject geneObject = new JSONObject();
            geneObject.element("gene", gene);

            Variant[][] variants = this.geneVariants.get(gene);

            JSONObject refJSON = new JSONObject();
            refJSON.element("score", score);
            JSONArray refVarJSON = getVariantsJSON(variants[0], false);
            if (!refVarJSON.isEmpty()) {
                refJSON.element("variants", refVarJSON);
            }
            geneObject.element("reference", refJSON);

            JSONObject matchJSON = new JSONObject();
            matchJSON.element("score", score);
            JSONArray matchVarJSON = getVariantsJSON(variants[1], true);
            if (!matchVarJSON.isEmpty()) {
                matchJSON.element("variants", matchVarJSON);
            }
            geneObject.element("match", matchJSON);

            genesJSON.add(geneObject);
            iGene++;
        }
        return genesJSON;
    }

    /**
     * Setting gene score not supported for similarity view.
     * 
     * @see org.phenotips.data.similarity.Genotype#setGeneScore(java.lang.String, java.lang.Double)
     */
    @Override
    public void setGeneScore(String gene, Double score)
    {
        return;
    }

    /**
     * Adding variants not supported for similarity view.
     * 
     * @see org.phenotips.data.similarity.Genotype#addVariant(java.lang.String, org.phenotips.data.similarity.Variant)
     */
    @Override
    public void addVariant(String gene, Variant variant)
    {
        return;
    }

}
