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
import java.io.FilenameFilter;
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

import org.apache.commons.io.FilenameUtils;
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
    private static final int MAX_GENES_SHOWN = 10;

    /** The name of the data subdirectory used by this job manager. */
    private static final String GENOTYPE_SUBDIR = "exomiser";

    /** Suffix of patient genotype files. */
    private static final String GENOTYPE_SUFFIX = ".ezr";

    /** Cache for storing symmetric pairwise patient similarity scores. */
    private static PairCache<Double> similarityScoreCache;

    /** Cache for storing patient genotypes. */
    private static Cache<Genotype> genotypeCache;

    /** Directory containing genotype information for all patients (e.g. Exomiser files). */
    private static File genotypeDirectory;

    /** Logging helper object. */
    private static Logger logger = LoggerFactory.getLogger(RestrictedGenotypeSimilarityView.class);

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

    /** Candidate genes in the reference patient. */
    private Collection<String> refCandidateGenes;

    /** Candidate genes in the match patient. */
    private Collection<String> matchCandidateGenes;

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

        if (similarityScoreCache == null) {
            try {
                similarityScoreCache = new PairCache<Double>();
            } catch (CacheException e) {
                logger.warn("Unable to create patient similarity score cache: " + e.toString());
            }
        }

        ComponentManager componentManager = ComponentManagerRegistry.getContextComponentManager();
        if (genotypeDirectory == null) {
            genotypeDirectory = getGenotypeDirectory(componentManager);
        }

        // Set up genotype cache
        if (genotypeCache == null) {
            try {
                CacheManager cacheManager = componentManager.getInstance(CacheManager.class);
                genotypeCache = cacheManager.createNewLocalCache(new CacheConfiguration());
            } catch (ComponentLookupException | CacheException e) {
                logger.warn("Unable to create patient genotype cache: " + e.toString());
            }
        }

        // Load components
        try {
            this.patientRepo = componentManager.getInstance(PatientRepository.class);
            this.patientViewFactory = componentManager.getInstance(PatientSimilarityViewFactory.class, "restricted");
        } catch (ComponentLookupException e) {
            logger.warn("Unable to load component: " + e.toString());
        }

        // Add in candidate genes
        this.matchCandidateGenes = getCandidateGeneNames(this.match);
        this.refCandidateGenes = getCandidateGeneNames(this.reference);

        // Get genotype information
        String matchId = match.getId();
        String refId = reference.getId();
        if (matchId != null) {
            this.matchGenotype = getGenotype(matchId);
        }
        if (refId != null) {
            this.refGenotype = getGenotype(refId);
        }

        // Match candidate genes and any genotype available
        if ((!this.refCandidateGenes.isEmpty() || this.refGenotype != null)
            && (!this.matchCandidateGenes.isEmpty() || this.matchGenotype != null)) {
            matchGenes();
        }
    }

    /**
     * Get the directory containing processed genotype (e.g. Exomiser) files for patients.
     *
     * @param componentManager
     * @return the directory as a File object, or null if it could not be found.
     */
    private static File getGenotypeDirectory(ComponentManager componentManager)
    {
        Environment environment = null;
        try {
            environment = componentManager.getInstance(Environment.class);
        } catch (ComponentLookupException e) {
            logger.warn("Unable to lookup environment: " + e.toString());
        }

        if (environment != null) {
            File rootDir = environment.getPermanentDirectory();
            File dataDir = new File(rootDir, GENOTYPE_SUBDIR);
            if (!dataDir.isDirectory() && dataDir.exists()) {
                logger.error("Expected directory but found file: " + dataDir.getAbsolutePath());
            } else {
                return dataDir;
            }
        }
        logger.warn("Could not find genotype directory");
        return null;
    }

    /**
     * Get a list of all patients with genotype information.
     *
     * @return a list of the document IDs for patients with genotype information.
     */
    private static List<String> getGenotypedPatients()
    {
        // List the basename of any genotype files in the genotype directory
        File[] outputFiles = genotypeDirectory.listFiles(new FilenameFilter()
        {
            @Override
            public boolean accept(File dir, String name)
            {
                return name.endsWith(GENOTYPE_SUFFIX);
            }
        });

        List<String> patientIds = new ArrayList<String>();
        for (File outputFile : outputFiles) {
            String id = FilenameUtils.removeExtension(outputFile.getName());
            patientIds.add(id);
        }
        return patientIds;
    }

    /**
     * Get the (potentially-cached) genotype for the patient with the given id.
     *
     * @param id the document identifier for the patient.
     * @return the loaded Genotype, or null if no genotype available.
     */
    private Genotype getGenotype(String id)
    {
        Genotype genotype = null;
        if (genotypeCache != null) {
            genotype = genotypeCache.get(id);
        }
        if (id != null && genotype == null && genotypeDirectory != null) {
            // Attempt to load genotype from file
            File vcf = new File(genotypeDirectory, id + GENOTYPE_SUFFIX);
            if (vcf.isFile()) {
                try {
                    genotype = new ExomizerGenotype(vcf);
                    logger.info("Loaded genotype for " + id);
                } catch (FileNotFoundException e) {
                    // No problem
                }
            }
            // Cache genotype
            if (genotype != null && genotypeCache != null) {
                genotypeCache.set(id, genotype);
            }
        }
        return genotype;
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
                otherSimScore = getPatientSimilarity(otherPatient, this.reference);
            }
            otherSimScores.put(patientId, otherSimScore);
        }
        return otherSimScore;
    }

    /**
     * Get the score for a gene, given the score threshold for each inheritance model for the pair of patients.
     *
     * @param gene the gene being scored
     * @param thresholds the [dominant, recessive] inheritance model score threshold
     * @param otherGenotypedIds the patient ids of other patients with genotype information, for comparison
     * @param otherSimScores the cached phenotype scores of other patients
     * @return the score for the gene, between [0, 1], with 0 corresponding to a poor score
     */
    private double scoreGene(String gene, double[] thresholds,
        Map<String, Genotype> otherGenotypes, Map<String, Double> otherSimScores)
    {
        double geneScore = Math.min(getGenePhenotypeScore(gene, this.refCandidateGenes, this.refGenotype),
            getGenePhenotypeScore(gene, this.matchCandidateGenes, this.matchGenotype));

        // Quit early if things are going poorly
        if (geneScore < 0.2) {
            return 0.0;
        }

        double[] scores = new double[2];
        for (int i = 0; i <= 1; i++) {
            scores[i] = geneScore * thresholds[i];
        }

        // Quit early if things are going poorly
        if (Math.max(scores[0], scores[1]) < 0.2) {
            return 0.0;
        }

        for (String patientId : otherGenotypes.keySet()) {
            Genotype otherGt = otherGenotypes.get(patientId);
            if (otherGt != null && otherGt.getGeneScore(gene) != null) {
                double[] otherScores = getGeneInheritanceScores(gene, null, otherGt);
                boolean applyPenalty = false;
                for (int i = 0; i <= 1; i++) {
                    otherScores[i] += 0.01;
                    if (scores[i] > 0.001 && otherScores[i] >= thresholds[i]) {
                        applyPenalty = true;
                    }
                }
                if (applyPenalty) {
                    double otherSimScore = Math.min(getOtherPatientSimilarity(patientId, otherSimScores) + 0.2, 1);
                    for (int i = 0; i <= 1; i++) {
                        double diff = otherScores[i] - thresholds[i];
                        if (diff > 0.0) {
                            scores[i] *= Math.pow(otherSimScore, diff);
                        }
                    }
                }
            }

            // Quit early if things are going poorly
            if (Math.max(scores[0], scores[1]) < 0.001) {
                return 0.0;
            }
        }

        return Math.max(scores[0], scores[1]);
    }

    /**
     * Return a collection of the names of candidate genes listed for the patient.
     *
     * @param p the patient
     * @return a (potentially-empty) unmodifiable collection of the names of candidate genes
     */
    private Collection<String> getCandidateGeneNames(Patient p)
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
                if (geneName != null) {
                    geneNames.add(geneName);
                }
            }
            return Collections.unmodifiableSet(geneNames);
        }
        return Collections.emptySet();
    }

    /**
     * Get top Variants for a gene.
     *
     * @param genotype the patient Genotype.
     * @param gene the gene to get variants for.
     * @return a (potentially-empty) list of top variants for the gene, by decreasing score.
     */
    private List<Variant> getTopVariants(Genotype genotype, String gene)
    {
        List<Variant> variants = new ArrayList<Variant>();
        if (genotype != null) {
            for (int i = 0; i <= 1; i++) {
                Variant v = genotype.getTopVariant(gene, i);
                if (v != null) {
                    variants.add(v);
                }
            }
        }
        return variants;
    }

    /**
     * Get dominant and recessive scores for gene in patient.
     *
     * @param gene
     * @param candidateGenes
     * @param genotype
     * @return array of [dominant, recessive] scores.
     */
    private double[] getGeneInheritanceScores(String gene, Collection<String> candidateGenes, Genotype genotype)
    {
        double[] scores = new double[2];

        if (candidateGenes != null && candidateGenes.contains(gene)) {
            scores[0] = 1.0;
            scores[1] = 1.0;
        } else {
            List<Variant> variants = getTopVariants(genotype, gene);
            for (int i = 0; i < variants.size(); i++) {
                scores[i] = getVariantScore(variants.get(i));
            }
        }
        return scores;
    }

    /**
     * Get phenotype score for gene in patient.
     *
     * @param gene
     * @param candidateGenes
     * @param genotype
     * @return phenotype score for gene.
     */
    private double getGenePhenotypeScore(String gene, Collection<String> candidateGenes, Genotype genotype)
    {
        Double score = null;
        if (candidateGenes != null && candidateGenes.contains(gene)) {
            score = 1.0;
        } else if (genotype != null) {
            score = genotype.getGeneScore(gene);
        }
        if (score == null) {
            score = 0.0;
        }
        return score;
    }

    /**
     * Score genes with variants in both patients, and sets 'geneScores' and 'geneVariants' fields accordingly.
     */
    private void matchGenes()
    {
        this.geneScores = new HashMap<String, Double>();

        // Load genotypes for all other patients
        Map<String, Genotype> otherGenotypes = null;
        Map<String, Double> otherSimScores = new HashMap<String, Double>();

        for (String gene : getGenes()) {
            double[] refScores = getGeneInheritanceScores(gene, this.refCandidateGenes, this.refGenotype);
            double[] matchScores = getGeneInheritanceScores(gene, this.matchCandidateGenes, this.matchGenotype);

            double geneScore = 0.0;
            if (this.refGenotype == null && this.matchGenotype == null) {
                // Without any genotypes, just score matching candidate genes as 1.0;
                geneScore = 1.0;
            } else {
                // Else, compute gene score based on full genotype breakdown
                // Set the variant harmfulness thresholds at the min of the two patients'
                double[] thresholds = new double[2];
                for (int i = 0; i <= 1; i++) {
                    thresholds[i] = Math.min(refScores[i], matchScores[i]);
                }

                if (otherGenotypes == null) {
                    otherGenotypes = new HashMap<String, Genotype>();
                    Set<String> otherIds = new HashSet<String>(getGenotypedPatients());
                    otherGenotypes.remove(this.match.getId());
                    otherGenotypes.remove(this.reference.getId());
                    for (String id : otherIds) {
                        Genotype gt = getGenotype(id);
                        if (gt != null) {
                            otherGenotypes.put(id, gt);
                        }
                    }
                }
                geneScore = scoreGene(gene, thresholds, otherGenotypes, otherSimScores);
            }

            if (geneScore < 0.0001) {
                continue;
            }

            // Save score and variants for display
            this.geneScores.put(gene, geneScore);
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
        Set<String> sharedGenes = new HashSet<String>(this.refCandidateGenes);
        if (this.refGenotype != null) {
            sharedGenes.addAll(this.refGenotype.getGenes());
        }
        Set<String> matchGenes = new HashSet<String>(this.matchCandidateGenes);
        if (this.matchGenotype != null) {
            matchGenes.addAll(this.matchGenotype.getGenes());
        }

        // Get union of genes mutated/candidate in both patients
        sharedGenes.retainAll(matchGenes);
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
        if (genotypeCache != null) {
            genotypeCache.removeAll();
        }
        logger.info("Cleared caches.");
    }

    /**
     * Clear all cached similarity scores associated with a particular patient.
     *
     * @param id the document identifier of the patient to clear from the cache.
     */
    public static void clearPatientCache(String id)
    {
        if (id != null) {
            if (similarityScoreCache != null) {
                similarityScoreCache.removeAssociated(id);
            }
            if (genotypeCache != null) {
                genotypeCache.remove(id);
            }
            logger.info("Cleared caches for patient: " + id);
        }
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
     * @param candidateGenes the patient's candidate gene names.
     * @param restricted if false, the variants are displayed regardless of the current accessType
     * @return JSON for the patient's variants, an empty array if there are no variants
     */
    private JSONObject getPatientVariantsJSON(String gene, Genotype genotype, Collection<String> candidateGenes,
        boolean restricted)
    {
        JSONObject patientJSON = new JSONObject();

        List<Variant> variants = getTopVariants(genotype, gene);
        JSONArray variantsJSON;
        if (variants == null && candidateGenes != null && candidateGenes.contains(gene)) {
            // Show candidate gene if no variants in gene
            variantsJSON = getCandidateGeneJSON();
        } else {
            // Show the top variants in the patient, according to access level
            variantsJSON = new JSONArray();
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
        }
        // Only add element if there are variants
        if (!variantsJSON.isEmpty()) {
            patientJSON.element("variants", variantsJSON);
        }
        return patientJSON;
    }

    private JSONObject getGeneVariantsJSON(String gene)
    {
        JSONObject variantsJSON = new JSONObject();
        variantsJSON
            .element("reference", getPatientVariantsJSON(gene, this.refGenotype, this.refCandidateGenes, false));
        variantsJSON.element("match", getPatientVariantsJSON(gene, this.matchGenotype, this.matchCandidateGenes, true));
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
