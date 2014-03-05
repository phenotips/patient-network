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

import org.phenotips.data.Feature;
import org.phenotips.data.Patient;
import org.phenotips.data.PatientRepository;
import org.phenotips.data.similarity.Genotype;
import org.phenotips.data.similarity.Variant;
import org.phenotips.integration.medsavant.MedSavantServer;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import exomizer.Exomizer;
import exomizer.exception.ExomizerException;
import jannovar.reference.Chromosome;
import net.sf.json.JSONArray;

/**
 * @version $Id$
 */
public class ExomizerJob implements Runnable
{
    /** Logging helper object. */
    private final Logger logger;

    /** The manager component, set with {@link #initialize(ExomizerJobManager, PatientRepository)}. */
    private final ExomizerJobManager manager;

    /** The patient being run. */
    private Patient patient;

    /** Data directory (used for temp and final files). */
    private File dataDir;

    /**
     * Create Runnable exomizer job from ExomizerManager instance.
     * 
     * @param manager the job manager running this job
     * @param patient the patient to run.
     * @param chromosomeMap the shared Exomizer data structure.
     * @param dataDir the directory in which to store the output (and temp) files.
     * @param completedJobs the shared data structure to record the job completion.
     */
    public ExomizerJob(ExomizerJobManager manager, Patient patient, File dataDir)
    {
        this.manager = manager;
        this.patient = patient;
        this.dataDir = dataDir;
        this.logger = LoggerFactory.getLogger(ExomizerJob.class);
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
     * Get the patient's unique PhenomeCentral ID.
     * 
     * @param p the patient.
     * @return the string PhenomeCentral ID of this patient.
     */
    private static String getPatientId(Patient p)
    {
        return p.getDocument().getName();
    }

    /**
     * Write out the filtered variants for the patient to an output file.
     * 
     * @param outFile the output file to write the variants to
     * @throws IOException if there is an error writing the variants to the file
     */
    private void writeVariantsToFile(File outFile) throws IOException
    {
        Patient patient = this.patient;
        String patientId = getPatientId(patient);

        this.logger.error("Writing variant from " + patientId + " to " + outFile.getAbsolutePath());
        final long startTime = System.currentTimeMillis();

        // Look up filtered variants from PhenomeCentral-Medsavant API
        MedSavantServer medsavant = this.manager.getMedSavantManager();
        List<JSONArray> variants = medsavant.getFilteredVariants(patient);

        File tempOutFile = new File(outFile.getAbsolutePath() + ".temp");
        BufferedWriter ofp = new BufferedWriter(new FileWriter(tempOutFile));

        // Write VCF header
        ofp.write("##fileFormat=VCF4.1\n");
        ofp.write("#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\tFORMAT\t" + getPatientId(patient));
        for (JSONArray variant : variants) {
            Variant v = new ExomizerVariant(variant);
            ofp.write(v.toVCFLine() + "\n");
        }
        ofp.close();

        boolean success = tempOutFile.renameTo(outFile);
        if (!success) {
            throw new IOException("Unable to move temp VCF file to final path:" + outFile.getAbsolutePath());
        }

        this.logger.error("Variants written to: " + outFile.getAbsolutePath());
        this.logger.error("Took " + (System.currentTimeMillis() - startTime) / 1000.0 + " seconds.");
    }

    @Override
    public void run()
    {
        if (this.manager == null) {
            throw new NullPointerException("ExomizerJobManager has not been set.");
        }
        String dbUrl = this.manager.getDatabaseURL();
        HashMap<Byte, Chromosome> chromosomeMap = this.manager.getChromosomeMap();
        String patientId = getPatientId(this.patient);

        // Create a VCF file with filtered variants for Exomizer to process
        File inFile = new File(this.dataDir, patientId + ".vcf");
        // TODO: only do this if the genotype is updated
        try {
            writeVariantsToFile(inFile);
        } catch (IOException e) {
            // Set result to have error
            throw new RuntimeException(e);
        }

        String hpoIDs = getPatientHPOs(this.patient); // "HP:0123456,HP:0000118,..."

        // Run Exomizer on VCF and HPO terms to generate outFile
        File outFile = new File(this.dataDir, patientId + ".ezr");
        File tempOutFile = new File(this.dataDir, patientId + ".temp");
        try {
            Exomizer exomizer = new Exomizer(chromosomeMap);
            exomizer.setHPOids(hpoIDs);
            exomizer.setUsePathogenicityFilter(true);
            exomizer.setFrequencyThreshold("1");
            exomizer.setVCFfile(inFile.getAbsolutePath());
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
        this.manager.putResult(patientId, result);
    }
}
