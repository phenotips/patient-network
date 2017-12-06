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
package org.phenotips.data.similarity.genotype;

import org.phenotips.data.Patient;
import org.phenotips.data.similarity.PatientGenotype;
import org.phenotips.data.similarity.PatientGenotypeManager;

import org.xwiki.component.annotation.Component;
import org.xwiki.component.phase.Initializable;
import org.xwiki.component.phase.InitializationException;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.slf4j.Logger;

/**
 * This is an implementation of the {@link PatientGenotypeManager}, and allows accessing the {@link PatientGenotype} for
 * the given {@link Patient}.
 *
 * @version $Id$
 * @since 1.0M6
 */
@Component
@Singleton
public class DefaultPatientGenotypeManager implements PatientGenotypeManager, Initializable
{
    /** Logging helper object. */
    @Inject
    protected static Logger logger;

    @Override
    public void initialize() throws InitializationException
    {
        logger.info("Initializing DefaultPatientGenotypeManager");
    }

    @Override
    public PatientGenotype getGenotype(Patient patient)
    {
        return new DefaultPatientGenotype(patient);
    }
}
