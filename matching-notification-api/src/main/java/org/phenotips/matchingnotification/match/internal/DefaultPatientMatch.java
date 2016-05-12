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
import org.phenotips.data.permissions.Owner;
import org.phenotips.data.permissions.PatientAccess;
import org.phenotips.data.permissions.PermissionsManager;
import org.phenotips.data.similarity.PatientGenotype;
import org.phenotips.data.similarity.PatientGenotypeManager;
import org.phenotips.data.similarity.PatientSimilarityView;
import org.phenotips.matchingnotification.finder.internal.TestMatchFinder;
import org.phenotips.matchingnotification.match.PatientMatch;

import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.model.reference.EntityReference;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import javax.persistence.Basic;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.PostLoad;
import javax.persistence.Table;
import javax.persistence.Transient;
import javax.persistence.UniqueConstraint;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.web.Utils;

/**
 * @version $Id$
 */
@Entity
@Table(name = "patient_matching",
       uniqueConstraints = {
           @UniqueConstraint(columnNames = { "patientId", "matchedPatientId", "remoteId", "outgoingRequest" }) })
public class DefaultPatientMatch implements PatientMatch
{
    private static final String SET_SEPARATOR = ";";

    private static final PermissionsManager PERMISSIONS_MANAGER;

    private static final PatientGenotypeManager GENOTYPE_MANAGER;

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultPatientMatch.class);

    private static final String EMAIL = "email";

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

    // Attributes related to patient with matchedPatientId

    @Basic
    private String matchedEmail;

    @Basic
    private String matchedGenes;

    @Transient
    private Set<String> matchedGenesSet;

    static {
        PermissionsManager permissionsManager = null;
        PatientGenotypeManager genotypeManager = null;
        try {
            permissionsManager = ComponentManagerRegistry.getContextComponentManager().getInstance(
                PermissionsManager.class);
            genotypeManager = ComponentManagerRegistry.getContextComponentManager().getInstance(
                PatientGenotypeManager.class);
        } catch (ComponentLookupException e) {
            LOGGER.error("Error loading static components: {}", e.getMessage(), e);
        }

        PERMISSIONS_MANAGER = permissionsManager;
        GENOTYPE_MANAGER = genotypeManager;
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
        this.matchedEmail = testData.matchedEmail;
        this.genes = testData.genes;
        this.genesSet = DefaultPatientMatch.stringToSet(testData.genes);
        this.matchedGenes = testData.matchedGenes;
        this.matchedGenesSet = DefaultPatientMatch.stringToSet(testData.matchedGenes);
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

        this.email = this.getOwnerEmail(referencePatient);
        this.genesSet = this.getGenes(referencePatient);
        this.genes = DefaultPatientMatch.setToString(this.genesSet);

        this.matchedEmail = this.getOwnerEmail(matchedPatient);
        this.matchedGenesSet = this.getGenes(matchedPatient);
        this.matchedGenes = DefaultPatientMatch.setToString(this.matchedGenesSet);
    }

    private Set<String> getGenes(Patient patient)
    {
        PatientGenotype genotype = DefaultPatientMatch.GENOTYPE_MANAGER.getGenotype(patient);
        if (genotype != null && genotype.hasGenotypeData()) {
            Set<String> set = genotype.getCandidateGenes();
            return set;
        } else {
            return Collections.emptySet();
        }
    }

    private String getOwnerEmail(Patient patient)
    {
        PatientAccess referenceAccess = DefaultPatientMatch.PERMISSIONS_MANAGER.getPatientAccess(patient);
        Owner owner = referenceAccess.getOwner();
        if (owner == null) {
            return "";
        }
        EntityReference ownerUser = owner.getUser();

        XWikiContext context = Utils.getContext();
        XWiki xwiki = context.getWiki();
        try {
            return xwiki.getDocument(ownerUser, context).getStringValue(EMAIL);
        } catch (XWikiException e) {
            DefaultPatientMatch.LOGGER.error("Error reading owner's email for patient {}.", patient.getId(), e);
            return "";
        }
    }

    @PostLoad
    private void parseSets()
    {
        this.genesSet = DefaultPatientMatch.stringToSet(this.genes);
        this.matchedGenesSet = DefaultPatientMatch.stringToSet(this.matchedGenes);
    }

    private static Set<String> stringToSet(String string)
    {
        if (StringUtils.isEmpty(string)) {
            return Collections.emptySet();
        } else {
            String[] split = string.split(DefaultPatientMatch.SET_SEPARATOR);
            Set<String> set = new HashSet<>(Arrays.asList(split));
            return set;
        }
    }

    private static String setToString(Set<String> set)
    {
        if (set == null || set.isEmpty()) {
            return "";
        } else {
            return StringUtils.join(set, DefaultPatientMatch.SET_SEPARATOR);
        }
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
        return this.matchedEmail;
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
    public JSONObject toJSON()
    {
        JSONObject json = new JSONObject();
        json.accumulate("id", this.getId());
        json.accumulate("patientId", this.getPatientId());
        json.accumulate("matchedPatientId", this.getMatchedPatientId());
        json.accumulate("remoteId", this.getRemoteId());
        json.accumulate("outgoingRequest", this.isOutgoing());
        json.accumulate("timestamp", this.timestamp);
        json.accumulate("notified", this.isNotified());
        json.accumulate("score", this.getScore());
        json.accumulate("genotypicScore", this.getGenotypeScore());
        json.accumulate("phenotypicScore", this.getPhenotypeScore());
        json.accumulate(EMAIL, this.getEmail());
        json.accumulate("genes", this.genes);
        json.accumulate("matchedEmail", this.getMatchedEmail());
        json.accumulate("matchedGenes", this.matchedGenes);
        return json;
    }

    @Override
    public String toString()
    {
        return toJSON().toString();
    }
}
