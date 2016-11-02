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
import org.phenotips.matchingnotification.match.PatientInMatch;
import org.phenotips.matchingnotification.match.PatientMatch;

import java.io.Serializable;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;

import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Transient;
import javax.persistence.UniqueConstraint;

import org.apache.commons.lang3.StringUtils;
import org.hibernate.CallbackException;
import org.hibernate.Session;
import org.hibernate.classic.Lifecycle;
import org.json.JSONObject;

/**
 * @version $Id$
 */
@Entity
@Table(name = "patient_matching",
    uniqueConstraints = { @UniqueConstraint(columnNames =
        { "referencePatientId", "referenceServerId", "matchedPatientId", "matchedServerId" }) })
public class DefaultPatientMatch implements PatientMatch, Lifecycle
{
    /** separates between tokens. */
    public static final String SEPARATOR = ";";

    private static final String ID = "id";

    /*
     * Attributes of the match
     */
    @Id
    @GeneratedValue
    private Long id;

    @Basic
    private Timestamp foundTimestamp;

    @Basic
    private Boolean notified;

    @Basic
    private Timestamp notifiedTimestamp;

    @Basic
    private Boolean rejected;

    @Basic
    private Double score;

    @Basic
    private Double genotypeScore;

    @Basic
    private Double phenotypeScore;

    @Basic
    /* an href to remote patient (either matched or reference). Only one of these options is true
     * 1. matched patient and reference patient are local =>
     *       referenceServerId==null, matchedServerId==null, href==null
     * 2. matched patient is remote and reference is local =>
     *       referenceServerId==null, matchedServerId!=null, href!=null, href then refers to matched patient.
     * 3. reference patient is remote and matched is local =>
     *       referenceServerId!=null, matchedServerId==null, href!=null, href then refers to reference patient.
     */
    private String href;

    /*
     * Attributes of reference patient
     */
    @Basic
    private String referencePatientId;

    @Basic
    private String referenceServerId;

    @Basic
    @Column(columnDefinition = "CLOB")
    private String referenceDetails;

    @Transient
    private PatientInMatch referencePatientInMatch;

    /*
     * Attributes of matched patient
     */
    @Basic
    private String matchedPatientId;

    @Basic
    private String matchedServerId;

    @Basic
    @Column(columnDefinition = "CLOB")
    private String matchedDetails;

    @Transient
    private PatientInMatch matchedPatientInMatch;

    /**
     * Hibernate requires a no-args constructor.
     */
    public DefaultPatientMatch()
    {
    }

    /**
     * Build a DefaultPatientMatch from a PatientSimilarityView. Both patients are local.
     *
     * @param similarityView the object to read match from
     */
    public DefaultPatientMatch(PatientSimilarityView similarityView)
    {
        this.initialize(similarityView, null, null);
    }

    /**
     * Build a DefaultPatientMatch from a PatientSimilarityView.
     *
     * @param similarityView the object to read match from
     * @param referenceServerId id of server where reference patient is found
     * @param matchedServerId if of server where matched patient is found
     */
    public DefaultPatientMatch(PatientSimilarityView similarityView, String referenceServerId, String matchedServerId)
    {
        this.initialize(similarityView, referenceServerId, matchedServerId);
    }

    private void initialize(PatientSimilarityView similarityView, String referenceServerId, String matchedServerId)
    {
        Patient referencePatient = similarityView.getReference();
        Patient matchedPatient = similarityView;

        this.referencePatientId = referencePatient.getId();
        this.referenceServerId = referenceServerId;
        this.referencePatientInMatch = new DefaultPatientInMatch(this, referencePatient, this.referenceServerId);
        this.referenceDetails = this.referencePatientInMatch.getDetailsColumn();

        this.matchedPatientId = matchedPatient.getId();
        this.matchedServerId = matchedServerId;
        this.matchedPatientInMatch = new DefaultPatientInMatch(this, matchedPatient, this.matchedServerId);
        this.matchedDetails = this.matchedPatientInMatch.getDetailsColumn();

        this.foundTimestamp = new Timestamp(System.currentTimeMillis());
        this.notifiedTimestamp = null;

        this.notified = false;
        this.rejected = false;

        this.score = similarityView.getScore();
        this.phenotypeScore = similarityView.getPhenotypeScore();
        this.genotypeScore = similarityView.getGenotypeScore();

        // TODO
        this.href = null;
    }

    @Override
    public Long getId()
    {
        return this.id;
    }

    @Override
    public void setRejected(boolean rejected)
    {
        this.rejected = rejected;
    }

    @Override
    public boolean isRejected()
    {
        return this.rejected;
    }

    @Override
    public void setNotified()
    {
        this.notified = true;
        this.notifiedTimestamp = new Timestamp(System.currentTimeMillis());
    }

    @Override
    public Boolean isNotified()
    {
        return notified;
    }

    @Override
    public String getReferencePatientId()
    {
        return referencePatientId;
    }

    @Override
    public String getReferenceServerId()
    {
        return this.referenceServerId;
    }

    @Override
    public String getMatchedPatientId()
    {
        return matchedPatientId;
    }

    @Override
    public String getMatchedServerId()
    {
        return this.matchedServerId;
    }

    @Override
    public Double getScore()
    {
        return this.score;
    }

    @Override
    public Double getPhenotypeScore()
    {
        return this.phenotypeScore;
    }

    @Override
    public Double getGenotypeScore()
    {
        return this.genotypeScore;
    }

    @Override
    public String getHref()
    {
        return this.href;
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder()
            .append("[")
            .append(this.getId()).append(SEPARATOR)
            .append(this.getReferencePatientId()).append(SEPARATOR)
            .append(this.getReferenceServerId()).append(SEPARATOR)
            .append(this.getMatchedPatientId()).append(SEPARATOR)
            .append(this.getMatchedServerId())
            .append("]");
        return sb.toString();
    }

    @Override
    public JSONObject toJSON()
    {
        JSONObject json = new JSONObject();
        json.put(ID, this.getId());

        json.put("reference", this.getReference().toJSON());
        json.put("matched", this.getMatched().toJSON());

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm");
        json.put("foundTimestamp", sdf.format(this.foundTimestamp));
        json.put("notifiedTimestamp",
            this.notifiedTimestamp == null ? "" : sdf.format(this.notifiedTimestamp));

        json.put("notified", this.isNotified());
        json.put("rejected", this.isRejected());
        json.put("score", this.getScore());
        json.put("genotypicScore", this.getGenotypeScore());
        json.put("phenotypicScore", this.getPhenotypeScore());
        json.put("href", this.isLocal() ? "" : this.getHref());

        return json;
    }

    @Override
    public boolean equals(Object obj)
    {
        if (!(obj instanceof DefaultPatientMatch)) {
            return false;
        }

        DefaultPatientMatch other = (DefaultPatientMatch) obj;

        return (StringUtils.equals(this.getReferencePatientId(), other.getReferencePatientId())
            && StringUtils.equals(this.getReferenceServerId(), other.getReferenceServerId())
            && StringUtils.equals(this.getMatchedPatientId(), other.getMatchedPatientId())
            && StringUtils.equals(this.getMatchedServerId(), other.getMatchedServerId()));
    }

    @Override
    public boolean isEquivalent(PatientMatch other)
    {
        return StringUtils.equals(this.getReferencePatientId(), other.getMatchedPatientId())
            && StringUtils.equals(this.getReferenceServerId(), other.getMatchedServerId())
            && StringUtils.equals(this.getMatchedPatientId(), other.getReferencePatientId())
            && StringUtils.equals(this.getMatchedServerId(), other.getReferenceServerId());
    }

    @Override
    public int hashCode()
    {
        String forhash = this.getReferencePatientId() + SEPARATOR
            + this.getReferenceServerId() + SEPARATOR
            + this.getMatchedPatientId() + SEPARATOR
            + this.getMatchedServerId();
        return forhash.hashCode();
    }

    @Override
    public boolean onDelete(Session arg0) throws CallbackException
    {
        return false;
    }

    @Override
    public void onLoad(Session arg0, Serializable arg1)
    {
        this.referencePatientInMatch = new DefaultPatientInMatch(
           this, this.referencePatientId, this.referenceServerId, this.referenceDetails);
        this.matchedPatientInMatch = new DefaultPatientInMatch(
           this, this.matchedPatientId, this.matchedServerId, this.matchedDetails);
    }

    @Override
    public boolean onSave(Session arg0) throws CallbackException
    {
        return false;
    }

    @Override
    public boolean onUpdate(Session arg0) throws CallbackException
    {
        return false;
    }

    @Override
    public PatientInMatch getMatched()
    {
        return this.matchedPatientInMatch;
    }

    @Override
    public PatientInMatch getReference()
    {
        return this.referencePatientInMatch;
    }

    @Override
    public boolean isReference(String patientId, String serverId)
    {
        return StringUtils.equals(this.getReferencePatientId(), patientId)
            && StringUtils.equals(this.getReferenceServerId(), serverId);
    }

    @Override
    public boolean isMatched(String patientId, String serverId)
    {
        return StringUtils.equals(this.getMatchedPatientId(), patientId)
            && StringUtils.equals(this.getMatchedServerId(), serverId);
    }

    @Override
    public boolean isLocal()
    {
        return this.getReference().isLocal() && this.getMatched().isLocal();
    }
}
