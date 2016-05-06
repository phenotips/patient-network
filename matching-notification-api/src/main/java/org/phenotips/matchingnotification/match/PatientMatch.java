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
    long getId();

    /**
     * @return id of patient that was matched. The owner of this patient is notified.
     */
    String getPatientId();

    /**
     * @return id of other patient. The owner of this patient is not notified while processing this entry.
     */
    String getMatchedPatientId();

    /**
     * @return id of remote server with which remote matching was done. In case of a local match, returns null.
     */
    String getRemoteId();

    /**
     * @return true if match request was initiated in local server.
     */
    boolean isOutgoing();

    /**
     * @return true if match request was initiated remotely.
     */
    boolean isIncoming();

    /**
     * @return whether notifications regarding this match were sent.
     */
    boolean isNotified();

    /**
     * Marks that notifications regarding this match were sent.
     */
    void setNotified();

    /**
     * @return object in JSON format.
     */
    JSONObject toJSON();

    /**
     * @return score of match.
     */
    double getScore();

    /**
     * @return patient's owner email.
     */
    String getOwnerEmail();

    /**
     * @return a set of candidate genes for patient with id {@code getPatientId()}
     */
    Set<String> getCandidateGenes();

    /**
     * @return a set of candidate genes for patient with id {@code getMatchedPatientId()}
     */
    Set<String> getMatchedCandidateGenes();
}
