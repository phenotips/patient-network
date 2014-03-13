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
import org.phenotips.data.similarity.PatientSimilarityViewFactory;
import org.phenotips.data.similarity.Variant;

import org.xwiki.cache.Cache;
import org.xwiki.cache.CacheException;
import org.xwiki.cache.CacheManager;
import org.xwiki.cache.config.CacheConfiguration;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.environment.Environment;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

import org.bouncycastle.util.Strings;
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
    /** Variant score threshold between non-frameshift and splicing. */
    private static final double KO_THRESHOLD = 0.87;

    /** The number of genes to show in the JSON output. */
    private static final int MAX_GENES_SHOWN = 5;

    /**
     * Gene damage information from control individuals (number of people with [KO-hom, KO-het, DMG-hom, DMG-het]
     * mutations).
     */
    private static Map<String, int[]> controlDamage;

    /** Cache for storing symmetric pairwise patient similarity scores. */
    private static Cache<Double> similarityScoreCache;

    /** Logging helper object. */
    private final Logger logger = LoggerFactory.getLogger(MutualInformationPatientSimilarityView.class);

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

        // Load components
        try {
            ComponentManager componentManager = ComponentManagerRegistry.getContextComponentManager();
            if (similarityScoreCache == null) {
                CacheManager cacheManager = componentManager.getInstance(CacheManager.class);
                similarityScoreCache = cacheManager.createNewLocalCache(new CacheConfiguration());
            }

            this.patientRepo = componentManager.getInstance(PatientRepository.class);
            this.patientViewFactory = componentManager.getInstance(PatientSimilarityViewFactory.class, "mi");

            this.exomizerManager = componentManager.getInstance(ExternalToolJobManager.class, "exomizer");
        } catch (ComponentLookupException e) {
            this.logger.error("Unable to load component: " + e.toString());
        } catch (CacheException e) {
            this.logger.error("Unable to create patient similarity score cache: " + e.toString());
        }

        String matchId = match.getId();
        String refId = reference.getId();
        if (matchId == null || refId == null || this.exomizerManager == null) {
            // No genotype similarity possible if the patients don't have IDs or no ExomizerManager
            return;
        }

        this.matchGenotype = this.exomizerManager.getResult(matchId);
        this.refGenotype = this.exomizerManager.getResult(refId);
        // Score the gene-wise similarity if both patients have genotypes
        if (this.matchGenotype != null && this.refGenotype != null) {
            loadControlData();
            matchGenes();
        }
    }

    /**
     * Load 1000 Genomes Project control data from a file to use in scoring.
     */
    private void loadControlData()
    {
        if (controlDamage == null) {
            controlDamage = new HashMap<String, int[]>();
            try {
                Environment env = ComponentManagerRegistry.getContextComponentManager().getInstance(Environment.class);
                File dataFile = new File(new File(env.getPermanentDirectory(), "exomizer"), "1000gp_gene_loads.txt");
                try {
                    Scanner fileScan = new Scanner(dataFile);
                    while (fileScan.hasNextLine()) {
                        String line = fileScan.nextLine();
                        if (line.startsWith("#")) {
                            continue;
                        }
                        String[] tokens = Strings.split(line, '\t');
                        String gene = tokens[0];
                        int[] scores = new int[4];
                        // Read and save the (first) four scores for the gene
                        scores[0] = Integer.parseInt(tokens[1]);
                        scores[1] = Integer.parseInt(tokens[2]);
                        scores[2] = Integer.parseInt(tokens[3]);
                        scores[3] = Integer.parseInt(tokens[4]);
                        controlDamage.put(gene, scores);
                    }
                    fileScan.close();
                    this.logger.error("Loaded control data for " + controlDamage.size() + " genes from: "
                        + dataFile.getAbsolutePath());
                } catch (FileNotFoundException e) {
                    this.logger.error("Missing control data: " + dataFile.getAbsolutePath());
                }
            } catch (ComponentLookupException e) {
                this.logger.error("Unable to load environment component");
            }
        }
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
            similarityScoreCache.set(key, score);
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
                // TODO: choose a better, less-arbitrary default
                otherSimScore = 0.1;
            } else {
                otherSimScore =
                    Math.min(getPatientSimilarity(otherPatient, this.match),
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
        if (geneScore < 0.01) {
            return 0.0;
        }
        // Adjust upwards the starting phenotype score to account for exomizer bias (almost all scores are < 0.7)
        geneScore = Math.min(geneScore / 0.7, 1);

        double domScore = geneScore;
        double recScore = geneScore;
        for (String patientId : otherGenotypedIds) {
            Genotype otherGt = this.exomizerManager.getResult(patientId);
            if (otherGt != null && otherGt.getGeneScore(gene) != null) {
                double otherSimScore = getOtherPatientSimilarity(patientId, otherSimScores);

                // Adjust gene score if other patient has worse scores in gene, weighted by patient similarity
                double penalty = otherSimScore + 0.001;
                if (getVariantScore(otherGt.getTopVariant(gene, 0)) + 0.01 >= domThresh) {
                    domScore *= penalty;
                }
                if (getVariantScore(otherGt.getTopVariant(gene, 1)) + 0.01 >= recThresh) {
                    recScore *= penalty;
                }
            }

            // Quit early if things are going poorly
            if (Math.max(domScore, recScore) < 0.0001) {
                return 0.0;
            }
        }

        // Incorporate 1000 Genomes gene damage statistics
        int[] geneControlDamage = controlDamage.get(gene);
        if (geneControlDamage != null) {
            domScore /= geneControlDamage[1] + 1;
            if (domThresh < KO_THRESHOLD) {
                domScore /= geneControlDamage[3] + 1;
            }
            recScore /= geneControlDamage[0] + 1;
            if (recThresh < KO_THRESHOLD) {
                recScore /= geneControlDamage[2] + 1;
            }
        }

        return Math.max(domScore, recScore);
    }

    /**
     * Score genes with variants in both patients, and sets 'geneScores' and 'geneVariants' fields accordingly.
     */
    private void matchGenes()
    {
        this.geneScores = new HashMap<String, Double>();
        this.geneVariants = new HashMap<String, Variant[][]>();

        // Load genotypes for all other patients
        Set<String> otherGenotypedIds = new HashSet<String>(this.exomizerManager.getAllCompleted());
        otherGenotypedIds.remove(this.match.getId());
        otherGenotypedIds.remove(this.reference.getId());
        Map<String, Double> otherSimScores = new HashMap<String, Double>();

        for (String gene : getGenes()) {
            // Set the variant harmfulness threshold at the min of the two patients'
            Variant[][] topVariants = new Variant[2][2];
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
     * Get the JSON for an array of variants.
     * 
     * @param vs an array of Variant objects
     * @return JSON for the variants
     */
    private JSONArray getVariantsJSON(Variant[] vs)
    {
        if (vs == null) {
            return null;
        } else {
            JSONArray varJSON = new JSONArray();
            for (Variant v : vs) {
                if (v != null) {
                    varJSON.add(v.toJSON());
                }
            }
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
        int iGene = 0;
        for (Map.Entry<String, Double> geneEntry : genes) {
            // Include at most top 5 genes
            if (iGene >= MAX_GENES_SHOWN) {
                break;
            }
            String gene = geneEntry.getKey();
            double score = geneEntry.getValue();
            double refScore = this.refGenotype.getGeneScore(gene);
            double matchScore = this.matchGenotype.getGeneScore(gene);
            score /= Math.min(refScore, matchScore);

            JSONObject geneObject = new JSONObject();
            geneObject.element("gene", gene);

            Variant[][] variants = this.geneVariants.get(gene);

            JSONObject refJSON = new JSONObject();
            refJSON.element("score", score * refScore);
            JSONArray refVarJSON = getVariantsJSON(variants[0]);
            if (refVarJSON != null) {
                refJSON.element("variants", refVarJSON);
            }
            geneObject.element("reference", refJSON);

            JSONObject matchJSON = new JSONObject();
            matchJSON.element("score", score * matchScore);
            JSONArray matchVarJSON = getVariantsJSON(variants[1]);
            if (matchVarJSON != null) {
                matchJSON.element("variants", matchVarJSON);
            }
            geneObject.element("match", matchJSON);

            genesJSON.add(geneObject);
            iGene++;
        }

        return genesJSON;
    }

}
