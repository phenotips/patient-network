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
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.phenotips.data.similarity.script;

import org.phenotips.data.similarity.ExomeManager;
import org.phenotips.data.similarity.PatientSimilarityViewFactory;
import org.phenotips.data.similarity.internal.DefaultPatientSimilarityViewFactory;
import org.phenotips.data.similarity.internal.ExomiserExomeManager;

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
    private ExomeManager exomeManager;

    @Inject
    private Logger logger;

    /**
     * Clear all (phenotype and genotype) patient similarity caches.
     */
    public void clearCache()
    {
        ((DefaultPatientSimilarityViewFactory) patientViewFactory).clearCache();
        ((ExomiserExomeManager) exomeManager).clearCache();
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
            ((ExomiserExomeManager) exomeManager).clearPatientCache(id);
            logger.info("Cleared cache for patient: " + id);
        }
    }
}
