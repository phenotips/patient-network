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
     * @return true only if match is rejected.
     */
    boolean isRejected();
}
