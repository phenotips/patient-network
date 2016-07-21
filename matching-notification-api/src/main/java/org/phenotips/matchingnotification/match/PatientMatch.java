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

import org.phenotips.matchingnotification.match.internal.PhenotypesMap;

import org.xwiki.model.EntityType;
import org.xwiki.model.reference.EntityReference;

import java.util.Set;

import org.json.JSONObject;

/**
 * @version $Id$
 */
public interface PatientMatch
{
    /** The space where family data is stored. */
    EntityReference DATA_SPACE = new EntityReference("MatchingNotification", EntityType.SPACE);

    /**
     * @return unique id of match
     */
    Long getId();

    /**
     * @return id of reference patient.
     */
    String getReferencePatientId();

    /**
     * @return id of server where reference patient is found. Null if local.
     */
    String getReferenceServerId();

    /**
     * @return id of other patient.
     */
    String getMatchedPatientId();

    /**
     * @return id of server where matched patient is found. Null if local.
     */
    String getMatchedServerId();

    /**
     * @return whether notifications regarding this match were sent.
     */
    Boolean isNotified();

    /**
     * Marks that notifications regarding this match were sent.
     */
    void setNotified();

    /**
     * Marks rejected property of match.
     *
     * @param rejected whether match is rejected or not
     */
    void setRejected(boolean rejected);

    /**
     * @return object in JSON format.
     */
    JSONObject toJSON();

    /**
     * @return score of match.
     */
    Double getScore();

    /**
     * @return phenotypical score
     */
    Double getPhenotypeScore();

    /**
     * @return genotypical score
     */
    Double getGenotypeScore();

    /**
     * @return reference patient's owner email.
     */
    String getEmail();

    /**
     * @return matched patient's owner email.
     */
    String getMatchedEmail();

    /**
     * @return a set of candidate genes for reference patient.
     */
    Set<String> getCandidateGenes();

    /**
     * @return a set of candidate genes for matched patient.
     */
    Set<String> getMatchedCandidateGenes();

    /**
     * @return phenotypes map of reference patient.
     */
    PhenotypesMap getReferencePhenotypesMap();

    /**
     * @return phenotypes map of matched patient.
     */
    PhenotypesMap getMatchedPhenotypesMap();

    /**
     * @return true only if match is rejected.
     */
    boolean isRejected();

    /**
     * Checks if {@code other} is equivalent to this.
     * A match m1 is equivalent to a match m2 if all the following are true:
     *    m1.referencePatientId = m2.matchedPatientId
     *    m1.referenceServerId  = m2.matchedServerId
     *    m2.matchedPatientId   = m1.referencePatientId
     *    m2.matchedPatientId   = m1.referenceServerId
     *
     * @param other match to compare to
     * @return true if equivalent
     */
    boolean isEquivalent(PatientMatch other);

    /**
     * @return {@code PatientInMatch} object representing the reference patient.
     */
    PatientInMatch getReference();

    /**
     * @return {@code PatientInMatch} object representing the matched patient.
     */
    PatientInMatch getMatched();

    /**
     * Checks if the patient given in parameter is the reference patient of the match.
     *
     * @param patientId id of patient
     * @param serverId id of server where patient is found
     * @return true if patient is reference patient in match
     */
    boolean isReference(String patientId, String serverId);

    /**
     * Checks if the patient given in parameter is the matched patient of the match.
     *
     * @param patientId id of patient
     * @param serverId id of server where patient is found
     * @return true if patient is matched patient in match
     */
    boolean isMatched(String patientId, String serverId);
}
