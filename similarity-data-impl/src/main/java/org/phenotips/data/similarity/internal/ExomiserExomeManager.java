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
package org.phenotips.data.similarity.internal;

import org.phenotips.data.Patient;
import org.phenotips.data.similarity.Exome;
import org.phenotips.data.similarity.ExomeManager;

import org.xwiki.cache.Cache;
import org.xwiki.cache.CacheException;
import org.xwiki.cache.CacheManager;
import org.xwiki.cache.config.CacheConfiguration;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.phase.Initializable;
import org.xwiki.component.phase.InitializationException;
import org.xwiki.environment.Environment;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.slf4j.Logger;

/**
 * This is an implementation of the {@link ExomeManager}, and allows accessing {@link ExomiserExome} objects for the
 * given {@link Patient}.
 *
 * @version $Id$
 * @since 1.0M6
 */
@Component
@Named("exomiser")
@Singleton
public class ExomiserExomeManager implements ExomeManager, Initializable
{
    /** Logging helper object. */
    @Inject
    protected static Logger logger;

    /** The name of the data subdirectory which stores the exome data. */
    private static final String GENOTYPE_SUBDIR = "exomiser";

    /** Suffix of patient exome files. */
    private static final String GENOTYPE_SUFFIX = ".variants.tsv.pass";

    /** Environment handle, to access exome data on filesystem. */
    @Inject
    protected Environment environment;

    /** Manager to create exome caches. */
    @Inject
    protected CacheManager cacheManager;

    /** Cache for storing patient exomes. */
    protected Cache<Exome> exomeCache;

    /** Directory containing exome information for all patients (e.g. Exomiser files). */
    protected File exomeDirectory;

    @Override
    public void initialize() throws InitializationException
    {
        exomeDirectory = getExomeDirectory();

        // Set up exome cache
        try {
            exomeCache = cacheManager.createNewLocalCache(new CacheConfiguration());
        } catch (CacheException e) {
            logger.error("Unable to create patient genotype cache: " + e.toString());
        }
    }

    @Override
    public Exome getExome(Patient patient)
    {
        String id = patient.getId();
        if (id == null) {
            // this must be a remote patient: such patients never have an exome
            return null;
        }
        Exome exome = null;
        if (exomeCache != null) {
            exome = exomeCache.get(id);
        }

        if (exome == null && exomeDirectory != null) {
            // Attempt to load exome from file
            exome = loadExomeById(id);
            // Cache exome
            if (exome != null && exomeCache != null) {
                exomeCache.set(id, exome);
            }
        }
        return exome;
    }

    /**
     * Get the directory containing processed exome (e.g. Exomiser) files for patients.
     *
     * @return the directory as a File object, or null if it could not be found.
     */
    private File getExomeDirectory()
    {
        if (environment != null) {
            File rootDir = environment.getPermanentDirectory();
            File dataDir = new File(rootDir, GENOTYPE_SUBDIR);
            if (!dataDir.isDirectory() && dataDir.exists()) {
                logger.error("Expected directory but found file: " + dataDir.getAbsolutePath());
            } else {
                return dataDir;
            }
        }
        logger.warn("Could not find exome directory");
        return null;
    }

    /**
     * Load a patient's {@link ExomiserExome}, based on the patient's id.
     *
     * @param id the patient record identifier
     * @return the {@link ExomiserExome} for the corresponding patient
     */
    private Exome loadExomeById(String id)
    {
        File patientDirectory = new File(exomeDirectory, id);
        File exomeFile = new File(patientDirectory, id + GENOTYPE_SUFFIX);
        if (patientDirectory.isDirectory() && exomeFile.isFile()) {
            try {
                Reader exomeReader = new FileReader(exomeFile);
                Exome exome = new ExomiserExome(exomeReader);
                logger.info("Loading genotype for " + id + " from: " + exomeFile);
                return exome;
            } catch (FileNotFoundException e) {
                logger.info("No exome data exists for " + id + " at: " + exomeFile);
            } catch (IOException e) {
                logger.error("Encountered error reading genotype: " + exomeFile);
            }
        }
        return null;
    }

    /**
     * Clear all cached patient exome data.
     */
    public void clearCache()
    {
        if (exomeCache != null) {
            exomeCache.removeAll();
            logger.info("Cleared cache.");
        }
    }

    /**
     * Clear all cached exome data associated with a particular patient.
     *
     * @param id the document ID of the patient to remove from the cache
     */
    public void clearPatientCache(String id)
    {
        if (exomeCache != null) {
            exomeCache.remove(id);
            logger.info("Cleared patient from cache: " + id);
        }
    }
}
