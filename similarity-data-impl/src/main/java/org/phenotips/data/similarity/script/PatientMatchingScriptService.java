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
package org.phenotips.data.similarity.script;

import org.phenotips.data.similarity.PatientSimilarityViewFactory;
import org.phenotips.data.similarity.internal.DefaultPatientSimilarityViewFactory;
import org.phenotips.data.similarity.internal.DefaultPatientGenotype;

import org.xwiki.component.annotation.Component;
import org.xwiki.script.service.ScriptService;
import org.xwiki.stability.Unstable;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.slf4j.Logger;

/**
 * Allows management of patient phenotype and genotype matching features.
 *
 * @version $Id$
 * @since
 */
@Unstable
@Component
@Named("patientMatching")
@Singleton
public class PatientMatchingScriptService implements ScriptService
{
    @Inject
    private PatientSimilarityViewFactory patientViewFactory;

    @Inject
    private Logger logger;

    /**
     * Clear all (phenotype and genotype) patient similarity caches.
     */
    public void clearCache()
    {
        ((DefaultPatientSimilarityViewFactory) patientViewFactory).clearCache();
        DefaultPatientGenotype.clearCache();
        logger.info("Cleared caches.");
    }

    /**
     * Clear all (phenotype and genotype) patient similarity caches for a specific patient.
     *
     * @param id the document ID of the patient whose caches are to be cleared.
     */
    public void clearPatientCache(String id)
    {
        if (id != null) {
            ((DefaultPatientSimilarityViewFactory) patientViewFactory).clearPatientCache(id);
            DefaultPatientGenotype.clearPatientCache(id);
            logger.info("Cleared cache for patient: " + id);
        }
    }
}
