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
import org.phenotips.data.permissions.PatientAccess;
import org.phenotips.data.permissions.PermissionsManager;
import org.phenotips.data.similarity.PatientSimilarityView;
import org.phenotips.matchingnotification.match.PatientMatch;
import org.phenotips.remote.common.internal.RemotePatientSimilarityView;

import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.model.reference.EntityReference;

import java.sql.Timestamp;

import javax.persistence.Basic;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

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
    private static final PermissionsManager PERMISSIONS_MANAGER;

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultPatientMatch.class);

    private static final String EMAIL = "email";

    @Id
    @GeneratedValue
    private Long id;

    @Basic
    private Timestamp timestamp;

    @Basic
    private String patientId;

    @Basic
    private String matchedPatientId;

    @Basic
    private String remoteId;

    @Basic
    private Boolean outgoingRequest;

    @Basic
    private Boolean notified;

    @Basic
    private Double score;

    @Basic
    private String ownerEmail;

    static {
        PermissionsManager permissionsManager = null;
        try {
            permissionsManager = ComponentManagerRegistry.getContextComponentManager().getInstance(
                PermissionsManager.class);
        } catch (ComponentLookupException e) {
            LOGGER.error("Error loading static components: {}", e.getMessage(), e);
        }

        PERMISSIONS_MANAGER = permissionsManager;
    }

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
     * @param patientId patientId
     * @param matchedPatientId matchedPatientId
     * @param remoteId remoteId
     * @param outgoingRequest outgoingRequest
     * @param score score
     * @param ownerEmail ownerEmail
     * @return a DefaultPatientMatch object for debug
     */
    public static DefaultPatientMatch getPatientMatchForDebug(String patientId, String matchedPatientId,
        String remoteId, boolean outgoingRequest, double score, String ownerEmail) {
        DefaultPatientMatch patientMatch = new DefaultPatientMatch();
        patientMatch.timestamp = new Timestamp(System.currentTimeMillis());
        patientMatch.patientId = patientId;
        patientMatch.matchedPatientId = matchedPatientId;
        patientMatch.remoteId = remoteId;
        patientMatch.outgoingRequest = outgoingRequest;
        patientMatch.notified = false;
        patientMatch.score = score;
        patientMatch.ownerEmail = ownerEmail;
        return patientMatch;
    }

    private void initialize(PatientSimilarityView similarityView, String remoteId, boolean outgoingRequest) {

        Patient referencePatient = similarityView.getReference();

        this.timestamp = new Timestamp(System.currentTimeMillis());
        this.patientId = referencePatient.getId();
        this.matchedPatientId = similarityView.getId();
        this.remoteId = remoteId;
        this.outgoingRequest = outgoingRequest;
        this.notified = false;
        this.score = similarityView.getScore();

        this.ownerEmail = this.getOwnerEmail(referencePatient);
    }

    private String getOwnerEmail(Patient patient) {
        PatientAccess referenceAccess = DefaultPatientMatch.PERMISSIONS_MANAGER.getPatientAccess(patient);
        EntityReference ownerUser = referenceAccess.getOwner().getUser();

        XWikiContext context = Utils.getContext();
        XWiki xwiki = context.getWiki();
        String email = null;
        try {
            email = xwiki.getDocument(ownerUser, context).getStringValue(EMAIL);
        } catch (XWikiException e) {
            DefaultPatientMatch.LOGGER.error("Error reading owner's email for patient {}.", patient.getId(), e);
        }
        return email;
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
