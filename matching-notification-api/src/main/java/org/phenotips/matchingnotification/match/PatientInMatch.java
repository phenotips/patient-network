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

import org.phenotips.data.ContactInfo;
import org.phenotips.data.Disorder;
import org.phenotips.data.Feature;
import org.phenotips.data.Patient;
import org.phenotips.data.permissions.AccessLevel;
import org.phenotips.data.similarity.AccessType;

import java.util.Collection;
import java.util.Set;

import org.json.JSONObject;

/**
 * The interface is used for unified access to reference and matched patients. It calculate the content of the column
 * for each patient in the match and gives a simpler access for their details (these two concerns could potentially be
 * handled in different classes, but for now are handled together).
 * <p>
 * The advantage of using this interface over a direct access to the match can be seen in comparing these two code
 * blocks:
 * </p>
 *
 * <pre>
 * {@code
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
 * }
 * </pre>
 *
 * See {@link org.phenotips.matchingnotification.notification.internal.DefaultAdminPatientMatchEmail} for some actual usage
 * examples.
 * <p>
 * Comment: getMatchedGenes()/getReferenceGenes() do not exist - the code serves as an example why it's not a good idea.
 * The access to genes is via PatientInMatch as in the first example.
 * </p>
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
     * @return a set of matched exome genes for patient.
     */
    Set<String> getMatchedExomeGenes();

    /**
     * @return set of patients phenotypes
     */
    Set<? extends Feature> getPhenotypes();

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
    JSONObject getDetailsColumnJSON();

    /**
     * @return mode of inheritance
     */
    Collection<String> getModeOfInheritance();

    /**
     * @return age of onset
     */
    String getAgeOfOnset();

    /**
     * @return patient if patient is local and exists or null otherwise.
     */
    Patient getPatient();

    /**
     * @return patient href.
     */
    String getHref();

    /**
     * @return true if patient has exome data.
     */
    boolean hasExomeData();

    /**
     * @return What type of access does the user have to this patient profile.
     */
    AccessLevel getAccess();

    /**
     * @return status of genes (solved or candidate) stored in PatientGenotype class,
     *         or null if patient does not have genotype or genotype status is unknown.
     */
    String getGenesStatus();

    /**
     * @return patient contact information.
     */
    ContactInfo getContactInfo();

    /**
     * @return What type of access does the user have to this patient profile.
     */
    AccessType getAccessType();

    /**
     * @return Patient disorders.
     */
    Set<? extends Disorder> getDisorders();
}
