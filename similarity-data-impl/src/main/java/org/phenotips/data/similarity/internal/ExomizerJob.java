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
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.phenotips.data.Feature;
import org.phenotips.data.Patient;
import org.phenotips.data.similarity.Genotype;

import exomizer.Exomizer;
import exomizer.exception.ExomizerException;

/**
 * @version $Id$
 */
public class ExomizerJob implements Runnable
{
    /** The manager component, set with {@link #setManager(ExomizerJobManager)}. */
    private static ExomizerJobManager manager;
    
    /** The patient being run. */
    private Patient patient;

    /** Input (vcf) file. */
    private File inFile;

    /** Output file directory (used for temp and final file. */
    private File outDir;

    /**
     * Create Runnable exomizer job from ExomizerManager instance.
     * 
     * @param patient the patient to run.
     * @param inFile the input (vcf) file for exomizer.
     * @param chromosomeMap the shared Exomizer data structure.
     * @param outDir the directory in which to store the output (and temp) file.
     * @param completedJobs the shared data structure to record the job completion.
     */
    public ExomizerJob(Patient patient, File inFile, File outDir)
    {
        this.patient = patient;
        this.inFile = inFile;
        this.outDir = outDir;
    }

    /**
     * Return the comma-separated string of HPO terms present in a Patient, suitable for ExomizerManager.
     * 
     * @param patient the patient to get HPO terms for.
     * @return the string of present HPO terms, comma-separated.
     */
    private static String getPatientHPOs(Patient patient)
    {
        List<String> hps = new LinkedList<String>();
        for (Feature feature : patient.getFeatures()) {
            if (feature.isPresent()) {
                hps.add(feature.getId());
            }
        }

        return StringUtils.join(hps, ",");
    }

    /**
     * Set the ExomizerJobManager used by all ExomizerJob instances.
     * 
     * @param manager the job manager to use.
     */
    public static void setManager(ExomizerJobManager manager) {
        ExomizerJob.manager = manager;
    }
    
    @Override
    public void run()
    {
        ExomizerJobManager manager = ExomizerJob.manager;
        if (manager == null) {
            throw new NullPointerException("ExomizerJobManager has not been set.");
        }

        String dbUrl = manager.getDatabaseURL();
        HashMap<Byte, Chromosome> chromosomeMap = manager.getChromosomeMap();
        
        String patientId = this.patient.getDocument().getName();
        File outFile = new File(this.outDir, patientId);
        File tempOutFile = new File(this.outDir, patientId + ".temp");

        String hpoIDs = getPatientHPOs(this.patient); // "HP:0123456,HP:0000118,..."
        try {
            Exomizer exomizer = new Exomizer(chromosomeMap);
            exomizer.setHPOids(hpoIDs);
            exomizer.setUsePathogenicityFilter(true);
            exomizer.setFrequencyThreshold("1");
            exomizer.setVCFfile(this.inFile.getAbsolutePath());
            exomizer.setOutfile(tempOutFile.getAbsolutePath());

            // Connect to database and load VCF
            exomizer.openNewDatabaseConnection(dbUrl);
            exomizer.parseVCFFile();

            // Process variants
            exomizer.initializeFilters();
            exomizer.initializePrioritizers();
            exomizer.executePrioritization();

            // Output results to file
            exomizer.outputVCF();
        } catch (ExomizerException e) {
            throw new RuntimeException("Exomizer error: " + e);
        }

        // Successfully completed, so move from temp to final file
        boolean success = tempOutFile.renameTo(outFile);
        if (!success) {
            throw new RuntimeException("Unable to move temp exomizer file to final path:" + outFile.getAbsolutePath());
        }

        // Load in and save exomizer data
        Genotype result = null;
        try {
            result = new ExomizerGenotype(outFile);
        } catch (FileNotFoundException e) {
            throw new RuntimeException("Unable to load genotype from file: " + outFile.getAbsolutePath());
        }
        manager.putResult(patientId, result);
    }
}
