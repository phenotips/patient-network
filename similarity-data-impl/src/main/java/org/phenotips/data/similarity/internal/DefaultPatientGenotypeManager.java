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
 * This is an implementation of the {@link #PatientGenotypeManager}, and allows accessing the {@link #PatientGenotype} for the
 * given {@link #Patient}.
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
