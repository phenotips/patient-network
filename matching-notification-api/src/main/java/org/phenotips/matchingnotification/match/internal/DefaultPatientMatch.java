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

    /**
     * Build a DefaultPatientMatch from a PatientSimilarityView.
     *
     * @param similarityView the object to read match from
     * @param outgoingRequest true if request was initiated locally
     */
    public DefaultPatientMatch(PatientSimilarityView similarityView, boolean outgoingRequest) {
        this.initialize(similarityView, null, outgoingRequest);
    }

    /**
     * Build a DefaultPatientMatch from a RemotePatientSimilarityView.
     *
     * @param similarityView the object to read match from
     * @param outgoingRequest true if request was initiated locally
     */
    public DefaultPatientMatch(RemotePatientSimilarityView similarityView, boolean outgoingRequest) {
        this.initialize(similarityView, similarityView.getRemoteServerId(), outgoingRequest);
    }

    /**
     * TODO remove.
     *
     * @param patientId id of patient
     * @param matchedPatientId id of matched patient
     * @param remoteId id of server where matched patient was found
     * @param outgoingRequest true if request was initiated locally
     * @return a DefaultPatientMatch object for debug
     */
    public static DefaultPatientMatch getPatientMatchForDebug(String patientId, String matchedPatientId,
        String remoteId, boolean outgoingRequest) {
        DefaultPatientMatch patientMatch = new DefaultPatientMatch();
        patientMatch.patientId = patientId;
        patientMatch.matchedPatientId = matchedPatientId;
        patientMatch.remoteId = remoteId;
        patientMatch.outgoingRequest = outgoingRequest;
        patientMatch.notified = false;
        return patientMatch;
    }

    private void initialize(PatientSimilarityView similarityView, String remoteId, boolean outgoingRequest) {
        this.patientId = similarityView.getReference().getId();
        this.matchedPatientId = similarityView.getId();
        this.remoteId = remoteId;
        this.outgoingRequest = outgoingRequest;
        this.notified = false;
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
