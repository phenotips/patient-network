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

import javax.persistence.Basic;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;

import org.json.JSONObject;

/**
 * @version $Id$
 */
@Entity
@Table(name = "patient_matching")
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
    private boolean notified;

    /**
     * Hibernate requires a no-args constructor.
     */
    public DefaultPatientMatch() {
    }

    /**
     * @param patientId id of patient
     * @param matchedPatientId id of matched patient
     */
    public DefaultPatientMatch(String patientId, String matchedPatientId) {
        this.patientId = patientId;
        this.matchedPatientId = matchedPatientId;
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
    public JSONObject toJSON() {
        JSONObject json = new JSONObject();
        json.accumulate("id", this.id);
        json.accumulate("patientId", this.patientId);
        json.accumulate("matchedPatientId", this.matchedPatientId);
        json.accumulate("notifed", this.notified);
        return json;
    }

    @Override
    public String toString() {
        return toJSON().toString();
    }
}
