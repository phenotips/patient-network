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

import org.phenotips.data.Patient;
import org.phenotips.data.similarity.PatientSimilarityView;
import org.phenotips.matchingnotification.match.PatientMatch;
import org.phenotips.remote.common.internal.RemotePatientSimilarityView;

import javax.persistence.Basic;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

import org.json.JSONObject;

/**
 * @version $Id$
 */
@Entity
@Table(name = "patient_matching",
       uniqueConstraints = {
           @UniqueConstraint(columnNames = { "patientId", "matchedPatientId", "remoteId", "outgoingRequest" }) })
public class DefaultPatientMatch implements PatientMatch
{
    @Id
    @GeneratedValue
    private Long id;

    @Basic
    private String patientId;

    @Basic
    private String matchedPatientId;

    @Basic
    private String remoteId;

    @Basic
    private Boolean outgoingRequest;

    @Basic
    private boolean notified;

    /**
     * Hibernate requires a no-args constructor.
     */
    public DefaultPatientMatch() {
    }

    // TODO initialization becomes a mess.

    /**
     * @param patientId id of patient
     * @param matchedPatientId id of matched patient
     * @param remoteId id of server where matched patient was found
     * @param outgoingRequest true if request was initiated locally
     */
    public DefaultPatientMatch(String patientId, String matchedPatientId, String remoteId, boolean outgoingRequest) {
        this.initialize(patientId, matchedPatientId, remoteId, outgoingRequest, false);
    }

    /**
     * Build a DefaultPatientMatch from a RemotePatientSimilarityView.
     *
     * @param similarityView the object to read match from
     * @param outgoingRequest true if request was initiated locally
     */
    public DefaultPatientMatch(PatientSimilarityView similarityView, boolean outgoingRequest) {
        Patient patient = similarityView.getReference();
        String patientIdParam = patient.getId();
        String matchedPatientIdParam = similarityView.getId();

        this.initialize(patientIdParam, matchedPatientIdParam, null, outgoingRequest, false);
    }

    /**
     * Build a DefaultPatientMatch from a RemotePatientSimilarityView.
     *
     * @param similarityView the object to read match from
     * @param outgoingRequest true if request was initiated locally
     */
    public DefaultPatientMatch(RemotePatientSimilarityView similarityView, boolean outgoingRequest) {
        Patient patient = similarityView.getReference();
        String patientIdParam = patient.getId();
        String matchedPatientIdParam = similarityView.getId();
        String remoteServerId = similarityView.getRemoteServerId();

        this.initialize(patientIdParam, matchedPatientIdParam, remoteServerId, outgoingRequest, false);
    }

    private void initialize(String patientId, String matchedPatientId, String remoteId, boolean outgoingRequest,
        boolean notified)
    {
        this.patientId = patientId;
        this.matchedPatientId = matchedPatientId;
        this.remoteId = remoteId;
        this.outgoingRequest = outgoingRequest;
        this.notified = notified;
    }

    @Override
    public long getId() {
        return this.id;
    }

    @Override
    public boolean isNotified() {
        return notified;
    }

    @Override
    public void setNotified() {
        this.notified = true;
    }

    @Override
    public String getPatientId() {
        return patientId;
    }

    @Override
    public String getMatchedPatientId() {
        return matchedPatientId;
    }

    @Override
    public String getRemoteId() {
        return this.remoteId;
    }

    @Override
    public JSONObject toJSON() {
        JSONObject json = new JSONObject();
        json.accumulate("id", this.id);
        json.accumulate("patientId", this.patientId);
        json.accumulate("matchedPatientId", this.matchedPatientId);
        json.accumulate("remoteId", this.remoteId);
        json.accumulate("outgoingRequest", this.outgoingRequest);
        json.accumulate("notifed", this.notified);
        return json;
    }

    @Override
    public String toString() {
        return toJSON().toString();
    }

    @Override
    public boolean isOutgoing() {
        return outgoingRequest;
    }

    @Override
    public boolean isIncoming() {
        return !outgoingRequest;
    }
}
