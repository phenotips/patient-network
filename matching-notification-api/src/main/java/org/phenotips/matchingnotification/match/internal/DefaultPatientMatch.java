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

import org.phenotips.components.ComponentManagerRegistry;
import org.phenotips.data.Patient;
import org.phenotips.data.PatientRepository;
import org.phenotips.data.similarity.PatientSimilarityView;
import org.phenotips.data.similarity.phenotype.DefaultPhenotypesMap;
import org.phenotips.data.similarity.phenotype.PhenotypesMap;
import org.phenotips.matchingnotification.match.PatientInMatch;
import org.phenotips.matchingnotification.match.PatientMatch;

import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.component.manager.ComponentManager;

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

import org.apache.commons.lang3.StringUtils;
import org.hibernate.CallbackException;
import org.hibernate.Session;
import org.hibernate.annotations.Index;
import org.hibernate.classic.Lifecycle;
import org.json.JSONObject;

/**
 * @version $Id$
 */
@Entity
@Table(name = "patient_matching")
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

    /**
     * @deprecated use {@link status} instead
     */
    @Basic
    @Deprecated
    private Boolean rejected;

    @Basic
    private String status;

    @Basic
    private Double score;

    @Basic
    private Double genotypeScore;

    @Basic
    private Double phenotypeScore;

    @Basic
    /* an href to remote patient (either matched or reference). Only one of these options is true
     * 1. matched patient and reference patient are local =>
     *       getReferenceServerId()==null, getMatchedServerId()==null, href==null (for both patients)
     * 2. matched patient is remote and reference is local =>
     *       getReferenceServerId()==null, getMatchedServerId()!=null, href!=null, href refers to matched patient.
     * 3. reference patient is remote and matched is local =>
     *       getReferenceServerId()!=null, getMatchedServerId()==null, href!=null, href refers to reference patient.
     */
    private String href;

    /*
     * Attributes of reference patient
     */
    @Basic
    @Index(name = "referencePatientIndex")
    private String referencePatientId;

    /* Local server is stored as "" (to avoid complications of dealing with `null`-s in SQL),
     * but getters return it as `null`
     */
    @Basic
    @Index(name = "referenceServerIndex")
    private String referenceServerId;

    @Basic
    @Column(length = 0xFFFFFF)
    private String referenceDetails;

    @Transient
    private PatientInMatch referencePatientInMatch;

    /*
     * Attributes of matched patient
     */
    @Basic
    @Index(name = "matchedPatientIndex")
    private String matchedPatientId;

    /* Local server is stored as "" (to avoid complications of dealing with `null`-s in SQL),
     * but getters return it as `null`
     */
    @Basic
    @Index(name = "matchedServerIndex")
    private String matchedServerId;

    @Basic
    @Column(length = 0xFFFFFF)
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
        // Reference patient
        Patient referencePatient = similarityView.getReference();
        this.referencePatientId = referencePatient.getId();
        this.referenceServerId = (referenceServerId == null) ? "" : referenceServerId;
        // we want to store local server ID as "" to avoid complications of dealing with `null`-s in SQL
        this.referencePatientInMatch = new DefaultPatientInMatch(this, referencePatient, referenceServerId);

        // Matched patient: The matched patient is provided by the similarity view for local matches. But for an
        // incoming remote match, where the reference patient is remote and the matched is local, similarity view
        // will hold a patient with restricted access, because of the restricted visibility of the local patient
        // to the remote server. Reading the patient's details will not work. However, since it's a local patient,
        // it's possible to get a non-restricted version of it.
        Patient matchedPatient = null;
        if (this.isIncoming()) {
            matchedPatient = getPatient(similarityView.getId());
        } else {
            matchedPatient = similarityView;
        }
        this.matchedPatientId = matchedPatient.getId();
        this.matchedServerId = (matchedServerId == null) ? "" : matchedServerId;
        this.matchedPatientInMatch = new DefaultPatientInMatch(this, matchedPatient, matchedServerId);

        // Properties of the match
        this.foundTimestamp = new Timestamp(System.currentTimeMillis());
        this.notifiedTimestamp = null;
        this.notified = false;
        this.status = "uncategorized";
        this.score = similarityView.getScore();
        this.phenotypeScore = similarityView.getPhenotypeScore();
        this.genotypeScore = similarityView.getGenotypeScore();

        if (this.matchedPatientInMatch.isLocal()) {
            this.href = this.referencePatientInMatch.getHref();
        } else {
            this.href = this.matchedPatientInMatch.getHref();
        }

        // Reorder phenotype
        DefaultPhenotypesMap.reorder(
            this.referencePatientInMatch.getPhenotypes().get(PhenotypesMap.PREDEFINED),
            this.matchedPatientInMatch.getPhenotypes().get(PhenotypesMap.PREDEFINED));

        // After reordering!
        this.referenceDetails = this.referencePatientInMatch.getDetailsColumn();
        this.matchedDetails = this.matchedPatientInMatch.getDetailsColumn();
    }

    @Override
    public Long getId()
    {
        return this.id;
    }

    /**
     * @deprecated use {@link status} instead
     */
    @Override
    @Deprecated
    public boolean isRejected()
    {
        return this.rejected != null ? this.rejected : "rejected".equals(this.status);
    }

    @Override
    public void setStatus(String newStatus)
    {
        this.status = newStatus;
    }

    @Override
    public String getStatus()
    {
        return this.status;
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
        return this.notified;
    }

    @Override
    public String getReferencePatientId()
    {
        return this.referencePatientId;
    }

    @Override
    public String getReferenceServerId()
    {
        return StringUtils.isEmpty(this.referenceServerId) ? null : this.referenceServerId;
    }

    @Override
    public String getMatchedPatientId()
    {
        return this.matchedPatientId;
    }

    @Override
    public String getMatchedServerId()
    {
        return StringUtils.isEmpty(this.matchedServerId) ? null : this.matchedServerId;
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
    public Timestamp getFoundTimestamp() {
        return this.foundTimestamp;
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
        json.put("notifiedTimestamp", this.notifiedTimestamp == null ? "" : sdf.format(this.notifiedTimestamp));

        json.put("notified", this.isNotified());
        json.put("status", this.getStatus());
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
            this, this.referencePatientId, this.getReferenceServerId(), this.referenceDetails);
        this.matchedPatientInMatch = new DefaultPatientInMatch(
            this, this.matchedPatientId, this.getMatchedServerId(), this.matchedDetails);
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

    @Override
    public boolean isIncoming()
    {
        return this.getReferenceServerId() != null && this.getMatchedServerId() == null;
    }

    @Override
    public boolean isOutgoing()
    {
        return this.getReferenceServerId() == null && this.getMatchedServerId() != null;
    }

    private static Patient getPatient(String patientId)
    {
        PatientRepository patientRepository = null;
        try {
            ComponentManager ccm = ComponentManagerRegistry.getContextComponentManager();
            patientRepository = ccm.getInstance(PatientRepository.class);
        } catch (ComponentLookupException e) {
            return null;
        }
        return patientRepository.get(patientId);
    }

    @Override
    public void setFoundTimestamp(Timestamp timestamp) {
        this.foundTimestamp = timestamp;
    }

    @Override
    public void setScore(Double score) {
        this.score = score;
    }

    @Override
    public void setGenotypeScore(Double score) {
        this.genotypeScore = score;
    }

    @Override
    public void setPhenotypeScore(Double score) {
        this.phenotypeScore = score;
    }

    @Override
    public void setReferenceDetails(String details) {
        this.referenceDetails = details;
    }

    @Override
    public void setReferencePatientInMatch(PatientInMatch patient) {
        this.referencePatientInMatch = patient;
    }

    @Override
    public void setMatchedDetails(String details) {
        this.matchedDetails = details;
    }

    @Override
    public void setMatchedPatientInMatch(PatientInMatch patient) {
        this.matchedPatientInMatch = patient;
    }

    @Override
    public String getReferenceDetails() {
        return this.referenceDetails;
    }

    @Override
    public String getMatchedDetails() {
        return this.matchedDetails;
    }
}
