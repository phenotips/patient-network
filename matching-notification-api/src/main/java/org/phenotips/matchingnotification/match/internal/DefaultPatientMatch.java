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
import org.phenotips.matchingnotification.match.PatientInMatch;
import org.phenotips.matchingnotification.match.PatientMatch;

import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.component.manager.ComponentManager;

import java.io.Serializable;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Set;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @version $Id$
 */
@Entity
@Table(name = "patient_matching",
       uniqueConstraints = { @UniqueConstraint(columnNames =
           { "referencePatientId", "referenceServerId", "matchedPatientId", "matchedServerId" }) })
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
    private String referencePatientId;

    @Basic
    private String referenceServerId;

    @Basic
    private String matchedPatientId;

    @Basic
    private String matchedServerId;

    @Basic
    private Timestamp timestamp;

    @Basic
    private Boolean notified;

    @Basic
    private Boolean rejected;

    @Basic
    private Double score;

    @Basic
    private Double genotypeScore;

    @Basic
    private Double phenotypeScore;

    // Attributes related to reference patient

    @Basic
    private String email;

    @Basic
    @Column(columnDefinition = "CLOB")
    private String genes;

    @Transient
    private Set<String> genesSet;

    @Basic
    @Column(columnDefinition = "CLOB")
    private String phenotypes;

    @Transient
    private PhenotypesMap phenotypesMap;

    // Attributes related to matched patient

    @Basic
    private String matchedHref;

    @Basic
    @Column(columnDefinition = "CLOB")
    private String matchedGenes;

    @Transient
    private Set<String> matchedGenesSet;

    @Basic
    @Column(columnDefinition = "CLOB")
    private String matchedPhenotypes;

    @Transient
    private PhenotypesMap matchedPhenotypesMap;

    @Transient
    private PatientInMatch referencePatientInMatch;

    @Transient
    private PatientInMatch matchedPatientInMatch;

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

    /**
     * TODO remove.
     *
     * @param testData testData
     */
    public DefaultPatientMatch(TestMatchFinder.TestMatchData testData)
    {
        this.timestamp = new Timestamp(System.currentTimeMillis());
        this.referencePatientId = testData.referenceServerId;
        this.matchedPatientId = testData.matchedPatientId;
        this.matchedServerId = testData.matchedServerId;
        this.notified = false;
        this.rejected = false;
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

        this.createPatientInMatches();
    }

    private void initialize(PatientSimilarityView similarityView, String referenceServerId, String matchedServerId)
    {
        Patient referencePatient = similarityView.getReference();
        Patient matchedPatient = similarityView;

        this.referencePatientId = referencePatient.getId();
        this.referenceServerId = referenceServerId;
        this.matchedPatientId = matchedPatient.getId();
        this.matchedServerId = matchedServerId;

        this.timestamp = new Timestamp(System.currentTimeMillis());

        this.notified = false;
        this.rejected = false;

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

        this.createPatientInMatches();
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
    public boolean isRejected() {
        return this.rejected;
    }

    @Override
    public void setNotified()
    {
        this.notified = true;
    }

    @Override
    public void setRejected(boolean rejected) {
        this.rejected = rejected;
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
    public PhenotypesMap getReferencePhenotypesMap()
    {
        return this.phenotypesMap;
    }

    @Override
    public PhenotypesMap getMatchedPhenotypesMap()
    {
        return this.matchedPhenotypesMap;
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

        json.put("timestamp", new SimpleDateFormat("yyyy/MM/dd HH:mm").format(this.timestamp));
        json.put("notified", this.isNotified());
        json.put("rejected", this.isRejected());
        json.put("score", this.getScore());
        json.put("genotypicScore", this.getGenotypeScore());
        json.put("phenotypicScore", this.getPhenotypeScore());

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
    public boolean isEquivalent(PatientMatch other) {
        return StringUtils.equals(this.getReferencePatientId(), other.getMatchedPatientId())
                && StringUtils.equals(this.getReferenceServerId(), other.getMatchedServerId())
                && StringUtils.equals(this.getMatchedPatientId(), other.getReferencePatientId())
                && StringUtils.equals(this.getMatchedServerId(), other.getReferenceServerId());
    }

    @Override
    public int hashCode() {
        String forhash = this.getReferencePatientId() + SEPARATOR
            + this.getReferenceServerId() + SEPARATOR
            + this.getMatchedPatientId() + SEPARATOR
            + this.getMatchedServerId();
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

        this.createPatientInMatches();
    }

    @Override
    public boolean onSave(Session arg0) throws CallbackException {
        return false;
    }

    @Override
    public boolean onUpdate(Session arg0) throws CallbackException {
        return false;
    }

    private void createPatientInMatches()
    {
        this.referencePatientInMatch = new ReferencePatientInMatch(this);
        this.matchedPatientInMatch = new MatchedPatientInMatch(this);
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
}
