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

import org.phenotips.data.similarity.PatientSimilarityView;
import org.phenotips.matchingnotification.match.PatientInMatch;
import org.phenotips.matchingnotification.match.PatientMatch;

import javax.persistence.Entity;
import javax.persistence.Table;

import org.apache.commons.lang3.StringUtils;
import org.hibernate.CallbackException;
import org.hibernate.Session;
import org.hibernate.annotations.Index;
import org.hibernate.classic.Lifecycle;

/**
 * @version $Id$
 */

@Entity
@Table(name = "patient_matching")
@org.hibernate.annotations.Table(appliesTo = "patient_matching",
    indexes = { @Index(name = "scoreIndex",
                       columnNames = {"score", "genotypeScore", "phenotypeScore", "foundTimestamp"}),
                @Index(name = "patientIndex",
                       columnNames = {"referencePatientId", "referenceServerId", "matchedServerId"}),
                @Index(name = "referencePatientIndex",
                       columnNames = {"referencePatientId"}),
                @Index(name = "matchedPatientIndex",
                       columnNames = {"matchedPatientId"})})
public class CurrentPatientMatch extends AbstractPatientMatch implements PatientMatch, Lifecycle
{
    /**
     * Hibernate requires a no-args constructor.
     */
    public CurrentPatientMatch()
    {
    }

    /**
     * Create a CurrentPatientMatch from a PatientSimilarityView, when both matched patients are local.
     *
     * @param similarityView the object to read match data from
     */
    public CurrentPatientMatch(PatientSimilarityView similarityView)
    {
        this(similarityView, null, null);
    }

    /**
     * Create a CurrentPatientMatch from a PatientSimilarityView.
     *
     * @param similarityView the object to read match data from
     * @param referenceServerId id of server where reference patient is found
     * @param matchedServerId if of server where matched patient is found
     */
    public CurrentPatientMatch(PatientSimilarityView similarityView, String referenceServerId, String matchedServerId)
    {
        super(similarityView, referenceServerId, matchedServerId);
    }

    @Override
    public boolean onSave(Session arg0) throws CallbackException
    {
        this.reorderLocalPatientsForStorage();
        return false;
    }

    /**
     *
     */
    private void reorderLocalPatientsForStorage()
    {
        if (StringUtils.isBlank(this.referenceServerId) && StringUtils.isBlank(this.matchedServerId)
            && this.referencePatientId.compareTo(this.matchedPatientId) > 0) {

            // can't have a nice swap() method in Java

            String tempPatient = this.referencePatientId;
            String tempServer = this.referenceServerId;
            String tempDetails = this.referenceDetails;
            PatientInMatch tempPatientInMatch = this.referencePatientInMatch;

            this.referencePatientId = this.matchedPatientId;
            this.referenceServerId = this.matchedServerId;
            this.referenceDetails = this.matchedDetails;
            this.referencePatientInMatch = this.matchedPatientInMatch;

            this.matchedPatientId = tempPatient;
            this.matchedServerId = tempServer;
            this.matchedDetails = tempDetails;
            this.matchedPatientInMatch = tempPatientInMatch;
        }
    }
}
