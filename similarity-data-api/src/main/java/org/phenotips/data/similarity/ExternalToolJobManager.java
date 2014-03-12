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

import org.xwiki.component.annotation.Role;
import org.xwiki.stability.Unstable;

import java.util.Set;

/**
 * Allows submitting, querying, and managing tasks to run an external tool on patients.
 * 
 * @param <T> The result type of the external tool, with one result object associated with each patient.
 * @version $Id$
 * @since
 */
@Unstable
@Role
public interface ExternalToolJobManager<T>
{
    /**
     * Return whether job has been submitted (or results exist) for Patient.
     * 
     * @param patient the {@link Patient} being processed with the external tool.
     * @return {@code true} if job has been submitted, else {@code false}.
     */
    boolean hasJob(Patient patient);

    /**
     * Return whether job for patient exists and is finished (due to success or error).
     * 
     * @param patient the {@link Patient} being processed with the external tool.
     * @return {@code true} if job existed and is finished, {@code false} if job doesn't exist or is pending.
     */
    boolean hasFinished(Patient patient);

    /**
     * Return whether the patient job completed successfully and results for the patient are available.
     * 
     * @param patient the {@link Patient} being processed with the external tool.
     * @return {@code true} if the patient is successfully processed, {@code false} if patient unprocessed or the job is
     *         still pending or failed.
     */
    boolean wasSuccessful(Patient patient);

    /**
     * Return status message for patient job.
     * 
     * @param patient the {@link Patient} being processed by the external tool.
     * @return string status/error message or null, e.g. "Submitted" "Pending", "Exomizer failed"
     */
    String getStatusMessage(Patient patient);

    /**
     * Get result from the last successful run, if available.
     * 
     * @param patientId the unique identifier of the {@link Patient} being processed by the external tool.
     * @return result for last successful run, or null.
     */
    T getResult(String patientId);

    /**
     * Mark the patient's job as being successfully completed and associate a result.
     * 
     * @param patientId the id of the patient whose job completed
     * @param result the result to store for the patient
     */
    void putResult(String patientId, T result);

    /**
     * Get all patient IDs for which there are output files.
     * 
     * @return an unmodifiable set of all the patientIds with completed jobs.
     */
    Set<String> getAllCompleted();

    /**
     * Add the {@link Patient} to the processing queue for the tool. If the patient already has a job in the queue, move
     * the job to the end of the queue. If the patient had a previously successful job, the results will be overwritten
     * by the new job (but not until the job actually runs).
     * 
     * @param patient the {@link Patient} to process with the external tool.
     */
    void addJob(Patient patient);
}
