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
package org.phenotips.matchingnotification.match;

import java.util.Collection;
import java.util.Set;

import org.json.JSONObject;

/**
 * The interface is used for unified access to reference and matched patients.
 * For example:
 *     PatientInMatch pim = condition ? PatientMatch.getReference() : patientMatch.getMatched();
 * and then expressions like pim.getPatientId(), pim.getServerId() can be used in the code.
 * This in comparison to
 *     if (condition) {
 *        patientMatch.getReferencePatientId(), patientMatch.getReferenceServerId(), etc.
 *     } else {
 *        patientMatch.getMatchedPatientId(), patientMatch.getMatchedServerId(), etc.
 *     }
 *
 * See {@code DefaultPatientMatchEmail} for some usage examples.
 *
 * @version $Id$
 */
public interface PatientInMatch
{
    /**
     * @return id of patient.
     */
    String getPatientId();

    /**
     * @return id of server where patient is found. Null if local.
     */
    String getServerId();

    /**
     * @return a set of candidate genes for patient.
     */
    Set<String> getCandidateGenes();

    /**
     * @return phenotypes map
     */
    PhenotypesMap getPhenotypes();

    /**
     * @return JSON representation of object
     */
    JSONObject toJSON();

    /**
     * @return true if patient is local
     */
    boolean isLocal();

    /**
     * @return a collection of email addresses associated with the patient. If the patient is local, this collection
     *         will contain the patient's owner's address, addresses of people from project the patient is part of, etc.
     *         If the patient is remote, the collection will contain only an href taken from the match.
     */
    Collection<String> getEmails();

    /**
     * @return the external id of the patient.
     */
    String getExternalId();
}
