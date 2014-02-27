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

import jannovar.reference.Chromosome;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.phenotips.data.Patient;
import org.phenotips.data.similarity.ExternalToolJobManager;
import org.phenotips.data.similarity.Genotype;
import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.phase.Initializable;
import org.xwiki.component.phase.InitializationException;
import org.xwiki.environment.Environment;

import exomizer.Exomizer;
import exomizer.exception.ExomizerException;

/**
 * @version $Id$
 */
@Component
@Named("exomizer")
@Singleton
public class ExomizerJobManager implements ExternalToolJobManager<Genotype>, Initializable
{
    /** Logging helper object. */
    @Inject
    private Logger logger;

    /** Environment helper to get static directory on local filesystem. */
    @Inject
    private Environment environment;

    /** Threadpool manager. */
    private ExecutorService executor;

    /** A record of jobs that have been submitted since running. */
    private Map<String, Future< ? >> submittedJobs;

    /** A mapping of available results, from PhenomeCentral patient IDs to the annotated Genotypes. */
    private Map<String, Genotype> completedJobs;

    /** Static directory for output exomizer files. */
    private File outDir;

    /** The URL for the Exomizer postgresql server. */
    private String dbUrl = "jdbc:postgresql://localhost/nsfpalizer";

    /** The server file path for the serialized UCSC data. */
    private String serializedDb = "/data/Exomiser/ucsc.ser";

    /** Exomized gene data structure, loaded once and shared by all jobs. */
    private HashMap<Byte, Chromosome> chromosomeMap;

    @Override
    public void initialize() throws InitializationException
    {
        // Set up threadpool
        this.executor = Executors.newFixedThreadPool(2);
        this.submittedJobs = new ConcurrentHashMap<String, Future< ? >>();
        this.completedJobs = new ConcurrentHashMap<String, Genotype>();
        ExomizerJob.setManager(this);
        
        // Shared across threads
        this.chromosomeMap = null;

        // Get xwiki component permanent directory for Exomizer files
        File rootDir = this.environment.getPermanentDirectory();
        this.outDir = new File(rootDir, "exomizer");
        if (!this.outDir.isDirectory()) {
            if (this.outDir.exists()) {
                throw new InitializationException("file exists instead of data: " + this.outDir.getAbsolutePath());
            } else {
                boolean success = this.outDir.mkdirs();
                if (!success) {
                    throw new InitializationException("could not create exomizer data directory: "
                        + this.outDir.getAbsolutePath());
                }
            }
        }

        // Process all already-completed files
        FilenameFilter exomizerFileFilter = new FilenameFilter()
        {
            public boolean accept(File dir, String name)
            {
                return !name.endsWith(".temp") && (new File(dir, name)).isFile();
            }
        };
        for (File file : this.outDir.listFiles(exomizerFileFilter)) {
            String patientId = file.getName();
            if (!"".equals(patientId)) {
                try {
                    putResult(patientId, new ExomizerGenotype(file));
                } catch (FileNotFoundException e) {
                    logger.error("Unable to load genotype from file: " + file.getAbsolutePath());
                }
            }
        }
    }

    /**
     * Get the shared exomizer chromosome map instance. Must be HashMap because Exomizer API requires it.
     * 
     * @return the shared map, or null if not yet set.
     */
    public HashMap<Byte, Chromosome> getChromosomeMap()
    {
        return this.chromosomeMap;
    }

    /**
     * Get the postgresql database URL for exomizer.
     * 
     * @return the database url string
     */
    public String getDatabaseURL()
    {
        return this.dbUrl;
    }

    @Override
    public void putResult(String patientId, Genotype result)
    {
        this.completedJobs.put(patientId, result);
    }

    /**
     * Get the patient's unique PhenomeCentral ID.
     * 
     * @param p the patient.
     * @return the string PhenomeCentral ID of this patient.
     */
    private static String getPatientId(Patient p)
    {
        return p.getDocument().getName();
    }

    @Override
    public void addJob(Patient patient)
    {
        if (this.chromosomeMap == null) {
            try {
                this.logger.error(" FIRST EXOMIZER JOB: initializing Exomizer with " + this.serializedDb);
                this.chromosomeMap = Exomizer.getDeserializedUCSCdata(this.serializedDb);
            } catch (ExomizerException e) {
                this.logger.error(" initialization FAILED: " + e);
                return;
            }
        }

        Future< ? > result = this.submittedJobs.get(patient);
        if (result != null) {
            // patient was already submitted
            result.cancel(false);
        }

        // Look up filtered variants from PhenomeCentral-Medsavant API
        // XXX: String patientId = getPatientId(patient);
        File inFile = new File("/Users/orion/projects/phenotips/NA20538_101600_AD_FGFR2.vcf");

        Runnable worker = new ExomizerJob(patient, inFile, this.outDir);

        // Submit job and store future for status queries
        this.logger.error(" submitting Exomizer job to threadpool: " + getPatientId(patient));
        result = this.executor.submit(worker);
        this.submittedJobs.put(getPatientId(patient), result);
    }

    @Override
    public boolean hasJob(Patient patient)
    {
        String patientId = getPatientId(patient);
        return this.submittedJobs.containsKey(patientId) || this.completedJobs.containsKey(patientId);
    }

    @Override
    public boolean hasFinished(Patient patient)
    {
        if (wasSuccessful(patient)) {
            return true;
        } else {
            // Check status of submitted job
            Future< ? > result = this.submittedJobs.get(getPatientId(patient));
            if (result != null && result.isDone()) {
                // job submitted and finished
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean wasSuccessful(Patient patient)
    {
        return completedJobs.containsKey(getPatientId(patient));
    }

    @Override
    public String getStatusMessage(Patient patient)
    {
        if (hasJob(patient)) {
            if (hasFinished(patient)) {
                if (wasSuccessful(patient)) {
                    return "success";
                } else {
                    return "error encountered";
                }
            } else {
                return "pending";
            }
        } else {
            return null;
        }
    }

    @Override
    public Genotype getResult(String patientId)
    {
        return completedJobs.get(patientId);
    }
    
    @Override
    public Set<String> getAllCompleted()
    {
        return Collections.unmodifiableSet(this.completedJobs.keySet());
    }
}
