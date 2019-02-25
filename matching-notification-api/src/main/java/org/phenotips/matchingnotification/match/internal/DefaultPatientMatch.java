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
import org.xwiki.users.User;
import org.xwiki.users.UserManager;

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
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @version $Id$
 */

@Entity
@Table(name = "patient_matching")
@org.hibernate.annotations.Table(appliesTo = "patient_matching",
    indexes = { @Index(name = "filterIndex", columnNames = { "notified", "score" }),
    @Index(name = "propertiesIndex", columnNames = { "notified", "status" }) })
public class DefaultPatientMatch implements PatientMatch, Lifecycle
{
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultPatientMatch.class);

    private static final int DB_HREF_FIELD_LENGTH = 2048;

    private static final int DB_MAX_DEFAULT_STRING_LENGTH = 255;

    /** separates between tokens. */
    private static final String SEPARATOR = ";";

    private static final String ID = "id";

    private static final String JSON_KEY_INTERACTIONS = "interactions";

    private static final String USER_CONTACTED = "user-contacted";

    private static final String NOTES = "notes";

    private static final UserManager USER_MANAGER;

    /*
     * Attributes of the match
     */
    @Id
    @GeneratedValue
    private Long id;

    @Basic
    private Timestamp foundTimestamp;

    @Basic
    @Index(name = "notifiedIndex")
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
    @Index(name = "statusIndex")
    private String status;

    @Basic
    @Column(length = 0xFFFFFF)
    private String comments;

    @Basic
    @Column(length = 0xFFFFFF)
    private String notes;

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
     *
     * Note: some servers (e.g. Pubcasefinder) send extremely long links, so the length of the field
     *       has to be increased beyond the default. For now the length is set to the suggested practical
     *       length limit of a URL, to balance storage vs generality, as suggested @
     *       https://stackoverflow.com/questions/417142/what-is-the-maximum-length-of-a-url-in-different-browsers
     */
    @Column(length = DB_HREF_FIELD_LENGTH)
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

    @Basic
    @Column(length = 0xFFFFFF)
    private String notificationHistory;

    @Transient
    private PatientInMatch matchedPatientInMatch;

    static {
        UserManager um = null;
        try {
            ComponentManager ccm = ComponentManagerRegistry.getContextComponentManager();
            um = ccm.getInstance(UserManager.class);
        } catch (Exception e) {
            LOGGER.error("Error loading static components: {}", e.getMessage(), e);
        }
        USER_MANAGER = um;
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

    private void initialize(PatientSimilarityView similarityView, String referenceServerId, String matchedServerId)
    {
        // Reference patient
        Patient referencePatient = similarityView.getReference();
        this.referencePatientId = this.limitStringLength(referencePatient.getId(), DB_MAX_DEFAULT_STRING_LENGTH);
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
        this.matchedPatientId = this.limitStringLength(matchedPatient.getId(), DB_MAX_DEFAULT_STRING_LENGTH);
        this.matchedServerId = (matchedServerId == null) ? "" : matchedServerId;
        this.matchedPatientInMatch = new DefaultPatientInMatch(this, matchedPatient, matchedServerId);

        // Properties of the match
        this.foundTimestamp = new Timestamp(System.currentTimeMillis());
        this.notifiedTimestamp = null;
        this.notified = false;
        this.notificationHistory = null;
        this.comments = null;
        this.notes = null;
        this.status = "uncategorized";
        this.score = similarityView.getScore();
        this.phenotypeScore = similarityView.getPhenotypeScore();
        this.genotypeScore = similarityView.getGenotypeScore();

        if (this.matchedPatientInMatch.isLocal()) {
            this.href = this.limitStringLength(this.referencePatientInMatch.getHref(), DB_HREF_FIELD_LENGTH);
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

    /** To protect from remote servers using extremely long href or patient IDs. */
    private String limitStringLength(String inputString, int maxLength)
    {
        if (inputString != null && inputString.length() > maxLength) {
            return inputString.substring(0, maxLength);
        }
        return inputString;
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
    public void setComments(JSONArray comments)
    {
        this.comments = (comments != null) ? comments.toString() : null;
    }

    @Override
    public void setNotes(JSONArray notes)
    {
        this.notes = (notes != null) ? notes.toString() : null;
    }

    @Override
    public void updateNotes(String note)
    {
        try {
            String currentUserId = USER_MANAGER.getCurrentUser().getId();
            JSONObject allNotes = (this.notes != null) ? new JSONObject(this.notes)
                : new JSONObject();
            JSONArray records = allNotes.optJSONArray(NOTES);
            JSONObject newRecord = new JSONObject();
            newRecord.put("user", currentUserId);
            newRecord.put("note", note);

            if (records == null) {
                records = new JSONArray();
                records.put(newRecord);
            } else {
                boolean updated = false;
                for (Object record : records) {
                    JSONObject item = (JSONObject) record;
                    if (item.optString("user", "").equals(currentUserId)) {
                        item.put("note", note);
                        updated = true;
                        break;
                    }
                }

                if (!updated) {
                    records.put(newRecord);
                }
            }

            allNotes.put(NOTES, records);
            this.notes = allNotes.toString();
        } catch (JSONException ex) {
            // error parsing notes or new record JSON string to JSON object happened
        }
    }

    @Override
    public String getStatus()
    {
        return this.status;
    }

    @Override
    public JSONArray getComments()
    {
        try {
            return new JSONArray(this.comments);
        } catch (JSONException | NullPointerException ex) {
            return null;
        }
    }

    @Override
    public String getNote()
    {
        try {
            if (this.notes == null) {
                return null;
            }
            JSONArray records = (this.notes != null) ? new JSONArray(this.notes)
                : new JSONArray();

            String currentUserId = USER_MANAGER.getCurrentUser().getId();
            for (Object record : records) {
                JSONObject note = (JSONObject) record;
                if (note.optString("user", "").equals(currentUserId)) {
                    return note.optString("note");
                }
            }
        } catch (JSONException ex) {
            // error parsing notes or new record JSON string to JSON array happened
        }
        return null;
    }

    @Override
    public JSONArray getNotes()
    {
        try {
            return new JSONArray(this.notes);
        } catch (JSONException | NullPointerException ex) {
            return null;
        }
    }

    @Override
    public void setNotified(boolean isNotified)
    {
        this.notified = isNotified;
        if (isNotified) {
            this.notifiedTimestamp = new Timestamp(System.currentTimeMillis());
        }
    }

    @Override
    public Timestamp getNotifiedTimestamp()
    {
        return this.notifiedTimestamp;
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
    public Timestamp getFoundTimestamp()
    {
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

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd");
        json.put("foundTimestamp", sdf.format(this.foundTimestamp));
        json.put("notifiedTimestamp", this.notifiedTimestamp == null ? "" : sdf.format(this.notifiedTimestamp));

        json.put("notified", this.isNotified());
        json.put("status", this.getStatus());
        json.put("score", this.getScore());
        json.put("genotypicScore", this.getGenotypeScore());
        json.put("phenotypicScore", this.getPhenotypeScore());
        json.put("href", this.isLocal() ? "" : this.getHref());
        json.put("comments", this.getComments());
        json.put("notificationHistory", this.getNotificationHistory());
        json.put("notes", this.getNote());

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
    public void setFoundTimestamp(Timestamp timestamp)
    {
        this.foundTimestamp = timestamp;
    }

    @Override
    public void setScore(Double score)
    {
        this.score = score;
    }

    @Override
    public void setGenotypeScore(Double score)
    {
        this.genotypeScore = score;
    }

    @Override
    public void setPhenotypeScore(Double score)
    {
        this.phenotypeScore = score;
    }

    @Override
    public void setReferenceDetails(String details)
    {
        this.referenceDetails = details;
    }

    @Override
    public void setReferencePatientInMatch(PatientInMatch patient)
    {
        this.referencePatientInMatch = patient;
    }

    @Override
    public void setMatchedDetails(String details)
    {
        this.matchedDetails = details;
    }

    @Override
    public void setMatchedPatientInMatch(PatientInMatch patient)
    {
        this.matchedPatientInMatch = patient;
    }

    @Override
    public String getReferenceDetails()
    {
        return this.referenceDetails;
    }

    @Override
    public String getMatchedDetails()
    {
        return this.matchedDetails;
    }

    @Override
    public JSONObject getNotificationHistory()
    {
        try {
            return new JSONObject(this.notificationHistory);
        } catch (JSONException | NullPointerException ex) {
            return null;
        }
    }

    @Override
    public void setNotificationHistory(JSONObject notificationHistory)
    {
        this.notificationHistory = (notificationHistory != null) ? notificationHistory.toString() : null;
    }

    @Override
    public void updateNotificationHistory(JSONObject notificationRecord)
    {
        try {
            JSONObject history = (this.notificationHistory != null) ? new JSONObject(this.notificationHistory)
                : new JSONObject();
            JSONArray records = history.optJSONArray(JSON_KEY_INTERACTIONS);
            if (records == null) {
                records = new JSONArray();
            }

            records.put(notificationRecord);
            history.put(JSON_KEY_INTERACTIONS, records);
            this.notificationHistory = history.toString();
        } catch (JSONException ex) {
            // error parsing notification history/ new record JSON string to JSON object happened
        }
    }

    @Override
    public void setUserContacted(boolean isUserContacted)
    {
        try {
            JSONObject history = (this.notificationHistory != null) ? new JSONObject(this.notificationHistory)
                : new JSONObject();
            history.put(USER_CONTACTED, isUserContacted);
            this.notificationHistory = history.toString();
        } catch (JSONException ex) {
            // error parsing notification history/ new record JSON string to JSON object happened
        }
    }

    @Override
    public void updateComments(String comment)
    {
        try {
            User currentUser = USER_MANAGER.getCurrentUser();
            JSONArray records = (this.comments != null) ? new JSONArray(this.comments)
                : new JSONArray();

            JSONObject newRecord = new JSONObject();
            JSONObject userInfo = new JSONObject();
            userInfo.put("id", currentUser.getId());
            userInfo.put("name", currentUser.getName());
            newRecord.put("userinfo", userInfo);
            newRecord.put("comment", comment);
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd hh:mm");
            newRecord.put("date", sdf.format(new Timestamp(System.currentTimeMillis())));

            records.put(newRecord);
            this.comments = records.toString();
        } catch (JSONException ex) {
            // error parsing notes or new record JSON string to JSON object happened
        }
    }
}
