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
 * The interface is used for unified access to reference and matched patients. It calculate the content of the
 * column for each patient in the match and gives a simpler access for their details (these two concerns could
 * potentially be handled in different classes, but for now are handled together).
 *
 * The advantage of using this interface over a direct access to the match can be seen in comparing these two
 * code blocks:
 *     PatientInMatch pim = condition ? PatientMatch.getReference() : patientMatch.getMatched();
 *     pim.getPatientId();
 *     pim.getServerId();
 *     pim.getGenes();
 * compared to
 *     if (condition) {
 *        patientMatch.getReferencePatientId(),
 *        patientMatch.getReferenceServerId(),
 *        patientMatch.getReferenceGenes() (see comment)
 *     } else {
 *        patientMatch.getMatchedPatientId(),
 *        patientMatch.getMatchedServerId(),
 *        patientMatch.getMatchedGenes() (see comment)
 *     }
 * See {@link DefaultPatientMatchEmail} for some actual usage examples.
 *
 * Comment: getMatchedGenes()/getReferenceGenes() do not exist - the code serves as an example why it's not a good idea.
 * The access to genes is via PatientInMatch as in the first example.
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
     * @return external id of patient.
     */
    String getExternalId();

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
     * @return the content of details columns for the patient in the match table
     */
    String getDetailsColumn();

    /**
     * @return mode of inheritance
     */
    Collection<String> getModeOfInheritance();

    /**
     * @return age of onset
     */
    String getAgeOfOnset();
}
