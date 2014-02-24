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
package org.phenotips.data.similarity;

import org.phenotips.data.Patient;
import org.phenotips.data.similarity.PatientSimilarityView;

import org.xwiki.component.annotation.Role;
import org.xwiki.stability.Unstable;

import java.util.List;

/**
 * Allows submitting, querying, and managing tasks to run an external tool on patients.
 * 
 * @version $Id$
 * @since 
 */
@Unstable
@Role
public interface ExternalToolJobManager
{
    /**
     *
     * - exomizer: keep track of (and API for PC to query)
     * -- no-vcf
     * -- in-progress
     * -- completed
     * -- error
     * 
     * @param referencePatient the reference patient, must not be {@code null}
     * @return the similar patients found in the database, an empty list if no patients are found or if the reference
     *         patient is invalid
     */
    int getStatus(Patient p);

    /**
     * - non-blocking dispatch job (phenomecentral id)
     * -- lookup vcf
     * -- lookup patient phenotypes
     * 
     * @param referencePatient the reference patient, must not be {@code null}
     * @return the number of similar patients found in the database, or {@code 0} if the reference patient is invalid
     */
    void addJob(Patient p);
}
