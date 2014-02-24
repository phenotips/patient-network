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

import exomizer.ExomizerManager;
import exomizer.exception.ExomizerException;

import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.phenotips.data.similarity.ExternalToolJobManager;
import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.phase.Initializable;
import org.xwiki.component.phase.InitializationException;

import exomizer.ExomizerManager;
import exomizer.exception.ExomizerException;

/**
 * @version $Id$
 */
public class ExomizerJob implements Runnable
{

    private final String hpo;

    private final String vcfFilePath;

    private final String outFilePath;

    private final HashMap<Byte, Chromosome> chromosomeMap;

    private final String dbUrl;
    
    private Map<String, Integer> jobStatus;

    public ExomizerJob(HashMap<Byte, Chromosome> chromosomeMap, String dbUrl, String hpo, String vcfFilePath, String outFilePath)
    {
        this.hpo = hpo;
        this.vcfFilePath = vcfFilePath;
        this.outFilePath = outFilePath;
        this.chromosomeMap = chromosomeMap;
        this.dbUrl = dbUrl;
    }

    @Override
    public void run()
    {
        if (this.chromosomeMap == null || this.dbUrl.isEmpty()) {
            throw new RuntimeException("Exomizer job parameters not properly set before running");
        }

        try {
            // Set up Exomizer instance
            Exomizer exomizer = new Exomizer(this.chromosomeMap);
            exomizer.setHPOids(this.hpo);
            exomizer.setUsePathogenicityFilter(true);
            exomizer.setFrequencyThreshold("1");
            exomizer.setVCFfile(this.vcfFilePath);
            exomizer.setOutfile(this.outFilePath);

            // Connect to database and load VCF
            exomizer.openNewDatabaseConnection(this.dbUrl);
            exomizer.parseVCFFile();

            // Process variants
            exomizer.initializeFilters();
            exomizer.initializePrioritizers();
            exomizer.executePrioritization();

            // Output results to file
            exomizer.outputVCF();
        } catch (ExomizerException e) {
            throw new RuntimeException(e);
        }
    }

}
