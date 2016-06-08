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
import org.phenotips.data.similarity.PatientSimilarityView;
import org.phenotips.matchingnotification.finder.internal.TestMatchFinder;
import org.phenotips.matchingnotification.match.PatientMatch;

import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.component.manager.ComponentManager;

import java.io.Serializable;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Set;

import javax.persistence.Basic;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @version $Id$
 */
@Entity
@Table(name = "patient_matching",
       uniqueConstraints = {
           @UniqueConstraint(columnNames = { "patientId", "matchedPatientId", "remoteId", "outgoingRequest" }) })
public class DefaultPatientMatch implements PatientMatch, Lifecycle
{
    /** separate between tokens. */
    public static final String SEPARATOR = ";";

    private static final String ID = "id";

    private static final DefaultPatientMatchUtils UTILS;

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultPatientMatch.class);

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
    private Timestamp timestamp;

    @Basic
    private Boolean notified;

    @Basic
    private Double score;

    @Basic
    private Double genotypeScore;

    @Basic
    private Double phenotypeScore;

    // Attributes related to patient with patientId

    @Basic
    private String email;

    @Basic
    private String genes;

    @Transient
    private Set<String> genesSet;

    @Basic
    private String phenotypes;

    @Transient
    // key: id, value: name
    private PhenotypesMap phenotypesMap;

    // Attributes related to patient with matchedPatientId

    @Basic
    private String matchedHref;

    @Basic
    private String matchedGenes;

    @Transient
    private Set<String> matchedGenesSet;

    @Basic
    private String matchedPhenotypes;

    @Transient
    private PhenotypesMap matchedPhenotypesMap;

    static {
        DefaultPatientMatchUtils utils = null;
        try {
            ComponentManager ccm = ComponentManagerRegistry.getContextComponentManager();
            utils = ccm.getInstance(DefaultPatientMatchUtils.class);
        } catch (ComponentLookupException e) {
            LOGGER.error("Error loading static components: {}", e.getMessage(), e);
        }
        UTILS = utils;
    }

    /**
     * Hibernate requires a no-args constructor.
     */
    public DefaultPatientMatch()
    {
    }

    /**
     * Build a DefaultPatientMatch from a PatientSimilarityView.
     *
     * @param similarityView the object to read match from
     * @param outgoingRequest true if request was initiated locally
     */
    public DefaultPatientMatch(PatientSimilarityView similarityView, boolean outgoingRequest)
    {
        this.initialize(similarityView, null, outgoingRequest);
    }

    /**
     * Build a DefaultPatientMatch from a PatientSimilarityView.
     *
     * @param similarityView the object to read match from
     * @param remoteId identifier of server where patient is found
     * @param outgoingRequest true if request was initiated locally
     */
    public DefaultPatientMatch(PatientSimilarityView similarityView, String remoteId, boolean outgoingRequest)
    {
        this.initialize(similarityView, remoteId, outgoingRequest);
    }

    /**
     * TODO remove.
     *
     * @param testData testData
     */
    public DefaultPatientMatch(TestMatchFinder.TestMatchData testData)
    {
        this.timestamp = new Timestamp(System.currentTimeMillis());
        this.patientId = testData.patientId;
        this.matchedPatientId = testData.matchedPatientId;
        this.remoteId = testData.remoteId;
        this.outgoingRequest = testData.outgoingRequest;
        this.notified = false;
        this.score = testData.score;
        this.phenotypeScore = testData.phenotypeScore;
        this.genotypeScore = testData.genotypeScore;
        this.email = testData.email;
        this.matchedHref = testData.matchedHref;
        this.genes = testData.genes;
        this.genesSet = UTILS.stringToSet(testData.genes);
        this.matchedGenes = testData.matchedGenes;
        this.matchedGenesSet = UTILS.stringToSet(testData.matchedGenes);
        this.phenotypes = "";
        this.phenotypesMap = PhenotypesMap.getInstance(this.phenotypes);
        this.matchedPhenotypes = "";
        this.matchedPhenotypesMap = PhenotypesMap.getInstance(this.matchedPhenotypes);
    }

    private void initialize(PatientSimilarityView similarityView, String remoteId, boolean outgoingRequest)
    {
        Patient referencePatient = similarityView.getReference();
        Patient matchedPatient = similarityView;

        this.patientId = referencePatient.getId();
        this.matchedPatientId = matchedPatient.getId();

        this.remoteId = remoteId;
        this.outgoingRequest = outgoingRequest;
        this.timestamp = new Timestamp(System.currentTimeMillis());
        this.notified = false;

        this.score = similarityView.getScore();
        this.phenotypeScore = similarityView.getPhenotypeScore();
        this.genotypeScore = similarityView.getGenotypeScore();

        this.email = UTILS.getOwnerEmail(referencePatient);
        this.genesSet = UTILS.getGenes(referencePatient);
        this.genes = UTILS.setToString(this.genesSet);
        this.phenotypesMap = new PhenotypesMap(referencePatient);
        this.phenotypes = this.phenotypesMap.toString();

        this.matchedHref = UTILS.getOwnerEmail(matchedPatient);
        this.matchedGenesSet = UTILS.getGenes(matchedPatient);
        this.matchedGenes = UTILS.setToString(this.matchedGenesSet);
        this.matchedPhenotypesMap = new PhenotypesMap(matchedPatient);
        this.matchedPhenotypes = this.matchedPhenotypesMap.toString();
    }

    @Override
    public Long getId()
    {
        return this.id;
    }

    @Override
    public Boolean isNotified()
    {
        return notified;
    }

    @Override
    public void setNotified()
    {
        this.notified = true;
    }

    @Override
    public String getPatientId()
    {
        return patientId;
    }

    @Override
    public String getMatchedPatientId()
    {
        return matchedPatientId;
    }

    @Override
    public String getRemoteId()
    {
        return this.remoteId;
    }

    @Override
    public Boolean isOutgoing()
    {
        return outgoingRequest;
    }

    @Override
    public Boolean isIncoming()
    {
        return !outgoingRequest;
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
    public String getEmail()
    {
        return this.email;
    }

    @Override
    public String getMatchedEmail()
    {
        return this.matchedHref;
    }

    @Override
    public Set<String> getCandidateGenes()
    {
        return this.genesSet;
    }

    @Override
    public Set<String> getMatchedCandidateGenes()
    {
        return this.matchedGenesSet;
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder()
            .append("[").append(this.getId())
            .append(SEPARATOR).append(this.getPatientId())
            .append(SEPARATOR).append(this.getMatchedPatientId())
            .append(SEPARATOR).append(this.getRemoteId())
            .append(SEPARATOR).append(this.isOutgoing()).append("]");
        return sb.toString();
    }

    @Override
    public JSONObject toJSON()
    {
        JSONObject json = new JSONObject();
        json.put(ID, this.getId());
        json.put("patientId", this.getPatientId());
        json.put("matchedPatientId", this.getMatchedPatientId());
        json.put("remoteId", this.getRemoteId());
        json.put("outgoingRequest", this.isOutgoing());
        json.put("timestamp", new SimpleDateFormat("yyyy/MM/dd HH:mm").format(this.timestamp));
        json.put("notified", this.isNotified());
        json.put("score", this.getScore());
        json.put("genotypicScore", this.getGenotypeScore());
        json.put("phenotypicScore", this.getPhenotypeScore());
        json.put("email", this.getEmail());
        json.put("matchedHref", this.getMatchedEmail());
        json.put("genes", UTILS.setToJSONArray(this.genesSet));
        json.put("matchedGenes", UTILS.setToJSONArray(this.matchedGenesSet));
        json.put("phenotypes", this.phenotypesMap.toJSON());
        json.put("matchedPhenotypes", this.matchedPhenotypesMap.toJSON());
        return json;
    }

    @Override
    public boolean equals(Object obj)
    {
        if (!(obj instanceof DefaultPatientMatch)) {
            return false;
        }

        DefaultPatientMatch other = (DefaultPatientMatch) obj;

        return (StringUtils.equals(this.getPatientId(), other.getPatientId())
            && StringUtils.equals(this.getMatchedPatientId(), other.getMatchedPatientId())
            && StringUtils.equals(this.getRemoteId(), other.getRemoteId())
            && this.isIncoming() == other.isIncoming());
    }

    @Override
    public int hashCode() {
        String forhash = this.getPatientId() + SEPARATOR
            + this.getMatchedPatientId() + SEPARATOR
            + this.getRemoteId() + SEPARATOR
            + this.isIncoming();
        return forhash.hashCode();
    }

    @Override
    public boolean onDelete(Session arg0) throws CallbackException {
        return false;
    }

    @Override
    public void onLoad(Session arg0, Serializable arg1) {
        this.genesSet = UTILS.stringToSet(this.genes);
        this.matchedGenesSet = UTILS.stringToSet(this.matchedGenes);
        this.phenotypesMap = PhenotypesMap.getInstance(this.phenotypes);
        this.matchedPhenotypesMap = PhenotypesMap.getInstance(this.matchedPhenotypes);
    }

    @Override
    public boolean onSave(Session arg0) throws CallbackException {
        return false;
    }

    @Override
    public boolean onUpdate(Session arg0) throws CallbackException {
        return false;
    }
}
