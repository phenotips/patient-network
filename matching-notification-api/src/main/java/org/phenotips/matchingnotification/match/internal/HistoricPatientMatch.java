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
package org.phenotips.matchingnotification.match.internal;

import org.phenotips.matchingnotification.match.PatientMatch;

import javax.persistence.Entity;
import javax.persistence.Table;

import org.hibernate.annotations.Index;
import org.hibernate.classic.Lifecycle;

/**
 * @version $Id$
 */

@Entity
@Table(name = "patient_matching_history")
@org.hibernate.annotations.Table(appliesTo = "patient_matching_history",
    indexes = { @Index(name = "historyIndex",
                       columnNames = {"score", "genotypeScore", "phenotypeScore", "foundTimestamp"})})
public class HistoricPatientMatch extends AbstractPatientMatch implements PatientMatch, Lifecycle
{
    /**
     * Hibernate requires a no-args constructor.
     */
    public HistoricPatientMatch()
    {
    }

    /**
     * Build a HistoricPatientMatch from another PatientMatch.
     *
     * @param existingMatch an existing PatientMathch to be stored in the history table
     */
    public HistoricPatientMatch(PatientMatch existingMatch)
    {
        AbstractPatientMatch otherMatch = (AbstractPatientMatch) existingMatch;

        // Reference patient
        this.referencePatientId = otherMatch.getReferencePatientId();
        this.referenceServerId = otherMatch.getReferenceServerId();

        this.matchedPatientId = otherMatch.getMatchedPatientId();
        this.matchedServerId = otherMatch.getMatchedServerId();

        this.foundTimestamp = otherMatch.getFoundTimestamp();
        this.notificationHistory = otherMatch.notificationHistory;
        this.comments = otherMatch.comments;
        this.notes = otherMatch.notes;
        this.status = otherMatch.status;
        this.score = otherMatch.getScore();
        this.phenotypeScore = otherMatch.getPhenotypeScore();
        this.genotypeScore = otherMatch.getGenotypeScore();
        this.href = otherMatch.href;

        this.referenceDetails = otherMatch.referenceDetails;
        this.matchedDetails = otherMatch.matchedDetails;

        this.referencePatientInMatch = otherMatch.referencePatientInMatch;
        this.matchedPatientInMatch = otherMatch.matchedPatientInMatch;
    }
}
