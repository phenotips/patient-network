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
import org.phenotips.data.similarity.Genotype;
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
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
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

/**
 * This class represents the collective genotype information in a patient, both from candidate genes and from exome
 * data.
 *
 * @version $Id$
 */
public class PatientGenotype
{
    /** Logging helper object. */
    private static Logger logger = LoggerFactory.getLogger(PatientGenotype.class);

    /** The name of the data subdirectory which stores the genotype data. */
    private static final String GENOTYPE_SUBDIR = "exomiser";

    /** Suffix of patient genotype files. */
    private static final String GENOTYPE_SUFFIX = ".variants.tsv.pass";

    /** Cache for storing patient genotypes. */
    private static Cache<Genotype> genotypeCache;

    /** Directory containing genotype information for all patients (e.g. Exomiser files). */
    private static File genotypeDirectory;

    /** A flag for whether or not static initialization has been performed yet. */
    private static boolean initialized;

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
    public PatientGenotype(Patient patient)
    {
        if (!initialized) {
            initialize();
        }

        genotype = getPatientGenotype(patient);
        candidateGenes = getPatientCandidateGeneNames(patient);
    }

    private static void initialize()
    {
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

        initialized = true;
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

    private static Genotype loadPatientGenotype(String id)
    {
        File patientDirectory = new File(genotypeDirectory, id);
        File exome = new File(patientDirectory, id + GENOTYPE_SUFFIX);
        if (patientDirectory.isDirectory() && exome.isFile()) {
            try {
                Reader exomeReader = new FileReader(exome);
                Genotype genotype = new ExomiserGenotype(exomeReader);
                logger.info("Loading genotype for " + id + " from: " + exome);
                return genotype;
            } catch (FileNotFoundException e) {
                // No problem
            } catch (IOException e) {
                logger.error("Encountered error reading genotype: " + exome);
            }
        }
        return null;
    }

    /**
     * Get the (potentially-cached) Genotype for the patient with the given id.
     *
     * @param p the patient to get the genotype for.
     * @return the corresponding Genotype, or null if no genotype available
     */
    private static Genotype getPatientGenotype(Patient p)
    {
        String id = p.getId();
        if (id == null) {
            // this must be a remote patient: such patients never have genotype
            return null;
        }
        Genotype genotype = null;
        if (genotypeCache != null) {
            genotype = genotypeCache.get(id);
        }
        if (genotype == null && genotypeDirectory != null) {
            // Attempt to load genotype from file
            genotype = loadPatientGenotype(id);
            // Cache genotype
            if (genotype != null && genotypeCache != null) {
                genotypeCache.set(id, genotype);
            }
        }
        return genotype;
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

    /**
     * Return whether the patient has any genotype data available (candidate genes or exome data).
     *
     * @return true iff the patient has any candidate genes or exome data
     */
    public boolean hasGenotypeData()
    {
        return (candidateGenes != null && !candidateGenes.isEmpty()) || genotype != null;
    }

    /**
     * Return a collection of the names of candidate genes listed for the patient.
     *
     * @return a (potentially-empty) unmodifiable collection of the names of candidate genes
     */
    public Collection<String> getCandidateGenes()
    {
        return candidateGenes;
    }

    /**
     * Get the genes likely mutated in the patient, both from candidate genes and exome data.
     *
     * @return a collection of gene names
     */
    public Collection<String> getGenes()
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

    /**
     * Get top Variants for a gene.
     *
     * @param gene the gene to get variants for.
     * @return a (potentially-empty) list of top variants for the gene, by decreasing score
     */
    public List<Variant> getTopVariants(String gene)
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
     * Get the kth top Variant for a gene.
     *
     * @param gene the gene to get variants for.
     * @param k the rank of the variant to return (0 is the highest ranked, 1 is the second-highest, etc.)
     * @return a (potentially-empty) list of top variants for the gene, by decreasing score
     */
    public Variant getTopVariant(String gene, int k)
    {
        return genotype == null ? null : genotype.getTopVariant(gene, k);
    }

    /**
     * Get the score for the given gene.
     *
     * @param gene the gene to get the score for.
     * @return the score between 0 and 1, with a score of 1 corresponding to a better gene ranking
     */
    public double getGeneScore(String gene)
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
     * Clear all cached patient genotype data.
     */
    public static void clearCache()
    {
        if (genotypeCache != null) {
            genotypeCache.removeAll();
            logger.info("Cleared cache.");
        }
    }

    /**
     * Clear all cached similarity data associated with a particular patient.
     *
     * @param id the document ID of the patient to remove from the cache
     */
    public static void clearPatientCache(String id)
    {
        if (genotypeCache != null) {
            genotypeCache.remove(id);
            logger.info("Cleared patient from cache: " + id);
        }
    }
}
