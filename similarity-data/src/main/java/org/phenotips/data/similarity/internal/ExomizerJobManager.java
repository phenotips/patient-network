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

import java.io.File;
import java.io.FilenameFilter;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.phenotips.data.Patient;
import org.phenotips.data.similarity.ExternalToolJobManager;
import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.phase.Initializable;
import org.xwiki.component.phase.InitializationException;


/**
 * 
 * @version $Id$
 */
@Component
@Named("exomizer")
@Singleton
public class ExomizerJobManager implements ExternalToolJobManager, Initializable
{

    /** The URL for the Exomizer postgresql server. */
    private final String DB_URL = "jdbc:postgresql://localhost/nsfpalizer";
    
    /** The server file path for the serialized UCSC data. */
    private final String UCSC_SER = "/data/Exomiser/ucsc.ser";
    
    private final HashMap<Byte, Chromosome> chromosomeMap;
    
    /** Logging helper object. */
    @Inject
    private Logger logger;

    private ExecutorService executor;
    
    private Map<Patient, Future<?>> submittedJobs;

    private Map<Patient, Future<?>> completedJobs;
    
    private File dataDir;
    
    @Override
    public void initialize() throws InitializationException
    {
        // Get xwiki component permanent directory for Exomizer files
        // Initialize record of existing jobs based upon files found there
        chromosomeMap = Exomizer.getDeserializedUCSCdata(UCSC_SER);
        dataDir = null;
        
        FilenameFilter exomizerFileFilter = new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return name.toLowerCase().endsWith(".ezr");
            }
        };

        dataDir.listFiles(exomizerFileFilter);
        
        
        executor = Executors.newFixedThreadPool(2);
        submittedJobs = new HashMap<Patient, Future<?>>();
    }
    
    @Override
    public int getStatus(Patient p)
    {
        // TODO Auto-generated method stub
        /**- exomizer: keep track of (and API for PC to query)
         * -- no-vcf
         * -- in-progress
         * -- completed
         * -- error
         **/
        Future<?> result = submittedJobs.get(p);
        if (result == null) {
            // Not in submittedJobs
        } else if (result.isDone()) {
            // Finished, due to success or failure
            if (result.get() == null) {
                // Success
            } else {
                // Error
            }
        } else {
            // Still in progress
        }
        return 0;
    }
    
    @Override
    public void addJob(Patient p)
    {
        // Look up filtered variants from PhenomeCentral-Medsavant API
        /** - non-blocking dispatch job (phenomecentral id)
         * -- lookup vcf
         * -- lookup patient phenotypes
         */
        String vcfFilePath = null;
        String outFilePath = null;
        String hpo = null; // "HP:0123456,HP:0000118,..."
        
        logger.info("Adding Exomizer job to queue: " + vcfFilePath + " -> " + outFilePath);
        Runnable worker = new ExomizerJob(this.chromosomeMap, this.DB_URL, hpo, vcfFilePath, outFilePath);
        
        Future<?> result = submittedJobs.get(p);
        if (result != null) {
            // patient was already submitted
            result.cancel(false);
        }
        
        // Submit job and store future for status queries
        result = executor.submit(worker);
        submittedJobs.put(p, result);
    }
}
