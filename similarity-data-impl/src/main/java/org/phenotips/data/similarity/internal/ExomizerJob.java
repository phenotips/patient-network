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
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.phenotips.data.Feature;
import org.phenotips.data.Patient;

import exomizer.Exomizer;
import exomizer.exception.ExomizerException;

/**
 * @version $Id$
 */
public class ExomizerJob implements Runnable
{

    /** The patient being run. */
    private Patient patient;

    /** Input (vcf) file. */
    private File inFile;

    /** Output file directory (used for temp and final file. */
    private File outDir;

    /** URL for the postgresql server. */
    private String dbUrl;

    /** The Exomizer gene data structure, shared across jobs. */
    private HashMap<Byte, Chromosome> chromosomeMap;

    /** The shared data structure to record completed jobs. */
    private Map<String, File> completedJobs;

    /**
     * Create Runnable exomizer job from ExomizerManager instance.
     * 
     * @param patient the patient to run.
     * @param inFile the input (vcf) file for exomizer.
     * @param chromosomeMap the shared Exomizer data structure.
     * @param outDir the directory in which to store the output (and temp) file.
     * @param completedJobs the shared data structure to record the job completion.
     */
    public ExomizerJob(Patient patient, File inFile, File outDir, String dbUrl,
        HashMap<Byte, Chromosome> chromosomeMap, Map<String, File> completedJobs)
    {
        this.patient = patient;
        this.inFile = inFile;
        this.outDir = outDir;
        this.dbUrl = dbUrl;
        this.chromosomeMap = chromosomeMap;
        this.completedJobs = completedJobs;
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

    @Override
    public void run()
    {
        String patientId = patient.getDocument().getName();
        File outFile = new File(outDir, patientId);
        File tempOutFile = new File(outDir, patientId + ".temp");

        String hpoIDs = getPatientHPOs(patient); // "HP:0123456,HP:0000118,..."
        try {
            Exomizer exomizer = new Exomizer(this.chromosomeMap);
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

        // Successfully completed, so move from temp to final file and add to completed
        boolean success = tempOutFile.renameTo(outFile);
        if (!success) {
            throw new RuntimeException("Unable to move temp exomizer file to final path:" + outFile.getAbsolutePath());
        }
        completedJobs.put(patientId, outFile);
    }

}
