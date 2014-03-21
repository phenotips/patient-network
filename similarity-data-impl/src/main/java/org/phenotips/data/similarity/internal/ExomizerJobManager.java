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
import org.phenotips.data.similarity.ExternalToolJobManager;
import org.phenotips.data.similarity.Genotype;
import org.phenotips.integration.medsavant.MedSavantServer;

import org.xwiki.component.annotation.Component;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.component.phase.Initializable;
import org.xwiki.component.phase.InitializationException;
import org.xwiki.environment.Environment;
import org.xwiki.query.Query;
import org.xwiki.query.QueryException;
import org.xwiki.query.QueryManager;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.slf4j.Logger;

import exomizer.Exomizer;
import exomizer.exception.ExomizerException;
import jannovar.reference.Chromosome;

/**
 * Manager for dispatching and caching patient exomizer data.
 * 
 * @version $Id$
 */
@Component(roles = { ExternalToolJobManager.class })
@Named("exomizer")
@Singleton
public class ExomizerJobManager implements ExternalToolJobManager<Genotype>, Initializable
{
    /** The name of the data subdirectory used by this job manager. */
    private static final String DATA_SUBDIR = "exomizer";

    /** Suffix for successfully completed patient exomizer file. */
    private static final String EXOMIZER_SUFFIX = ".ezr";

    /** Filename of serialized UCSC data, used by exomizer. */
    private static final String SERIALIZED_UCSC = "ucsc.ser";

    /** Logging helper object. */
    @Inject
    private Logger logger;

    /** Environment helper to get static directory on local filesystem. */
    @Inject
    private Environment environment;

    /** Threadpool manager. */
    private ExecutorService executor;

    /** A record of jobs that have been submitted since running. */
    private Map<String, Future<?>> submittedJobs;

    /** A mapping of available results, from PhenomeCentral patient IDs to the annotated Genotypes. */
    private Map<String, Genotype> completedJobs;

    /** Static directory for output exomizer files. */
    private File dataDir;

    /** Exomizer gene data structure, loaded once and shared by all jobs. */
    private HashMap<Byte, Chromosome> chromosomeMap;

    @Override
    public void initialize() throws InitializationException
    {
        logger.error("Intializing ExomizerJobManager...");
        this.chromosomeMap = null;

        // Set up threadpool
        this.executor = Executors.newFixedThreadPool(2);
        this.submittedJobs = new ConcurrentHashMap<String, Future<?>>();
        this.completedJobs = new ConcurrentHashMap<String, Genotype>();

        // Get xwiki component permanent directory for Exomizer files
        File rootDir = this.environment.getPermanentDirectory();
        this.dataDir = new File(rootDir, DATA_SUBDIR);
        if (!this.dataDir.isDirectory()) {
            if (this.dataDir.exists()) {
                throw new InitializationException("file exists instead of data: " + this.dataDir.getAbsolutePath());
            } else {
                boolean success = this.dataDir.mkdirs();
                if (!success) {
                    throw new InitializationException("could not create exomizer data directory: "
                        + this.dataDir.getAbsolutePath());
                }
            }
        }
        this.logger.error("ExomizerJobManager data directory: " + this.dataDir.getAbsolutePath());

        initializeData();
    }

    private void initializeData()
    {
        this.logger.error("Looking for available genotype jobs to start...");

        PatientRepository patients = null;
        QueryManager qm = null;
        MedSavantServer medsavant = null;
        List<String> patientDocs = null;
        try {
            ComponentManager cm = ComponentManagerRegistry.getContextComponentManager();
            patients = cm.getInstance(PatientRepository.class);
            qm = cm.getInstance(QueryManager.class);
            patientDocs = qm.createQuery("from doc.object(PhenoTips.PatientClass) as patient", Query.XWQL).execute();

            // Handle MedSavantServer component more carefully, since it *will* likely crash
            try {
                medsavant = cm.getInstance(MedSavantServer.class);
            } catch (ComponentLookupException e) {
                this.logger.error("Could not load medsavant, no jobs will be started.");
            }
        } catch (ComponentLookupException e) {
            this.logger.error("Could not load components for patient lookup.");
            return;
        } catch (QueryException e) {
            this.logger.error("Query error: " + e.toString());
            return;
        }

        for (String patientDoc : patientDocs) {
            Patient p = patients.getPatientById(patientDoc);
            if (p != null) {
                String patientId = p.getId();
                File results = new File(this.dataDir, patientId + EXOMIZER_SUFFIX);
                if (results.exists()) {
                    try {
                        this.logger.error("Loading genetics for " + patientId);
                        putResult(patientId, new ExomizerGenotype(results));
                    } catch (FileNotFoundException e) {
                        this.logger.error("Unable to load genotype from file: " + results.getAbsolutePath());
                    }
                } else if (medsavant != null && !hasJob(p) && medsavant.hasVCF(p)) {
                    addJob(p);
                }
            }
        }
    }

    /**
     * Get the chromosomeMap lazily, memoizing once it's loaded.
     * 
     * @return the chromosomeMap for exomizer to use
     */
    private HashMap<Byte, Chromosome> getChromosomeMap()
    {
        if (this.chromosomeMap == null) {
            String serializedDb = (new File(this.dataDir, SERIALIZED_UCSC)).getAbsolutePath();
            try {
                this.chromosomeMap = Exomizer.getDeserializedUCSCdata(serializedDb);
                this.logger.error("Loaded shared Exomizer chromosome map from: " + serializedDb);
            } catch (ExomizerException e) {
                String err = "Failed to load chromosome map: " + e;
                this.logger.error(err);
            }
        }
        return this.chromosomeMap;
    }

    @Override
    public void putResult(String patientId, Genotype result)
    {
        this.completedJobs.put(patientId, result);
    }

    @Override
    public void addJob(Patient patient)
    {

        Future<?> result = this.submittedJobs.get(patient);
        if (result != null) {
            // cancel pending submission for same patient
            result.cancel(false);
        }

        Runnable worker = new ExomizerJob(this, getChromosomeMap(), patient, this.dataDir);

        // Submit job and store future for status queries
        this.logger.error(" submitting Exomizer job to threadpool: " + patient.getId());
        result = this.executor.submit(worker);

        // Add future (potentially-null/failed) result to submitted
        this.submittedJobs.put(patient.getId(), result);
    }

    @Override
    public boolean hasJob(Patient patient)
    {
        String patientId = patient.getId();
        return (this.submittedJobs.containsKey(patientId) || this.completedJobs.containsKey(patientId));
    }

    @Override
    public boolean hasFinished(Patient patient)
    {
        if (wasSuccessful(patient)) {
            return true;
        } else {
            // Check status of submitted job
            Future<?> result = this.submittedJobs.get(patient.getId());
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
        return this.completedJobs.containsKey(patient.getId());
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
        return this.completedJobs.get(patientId);
    }

    @Override
    public Set<String> getAllCompleted()
    {
        return Collections.unmodifiableSet(this.completedJobs.keySet());
    }
}
