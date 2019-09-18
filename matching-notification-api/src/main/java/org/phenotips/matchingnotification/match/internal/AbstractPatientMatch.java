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
import org.phenotips.data.Gene;
import org.phenotips.data.Patient;
import org.phenotips.data.PatientRepository;
import org.phenotips.data.internal.PhenoTipsGene;
import org.phenotips.data.similarity.PatientGenotypeSimilarityView;
import org.phenotips.data.similarity.PatientPhenotypeSimilarityView;
import org.phenotips.data.similarity.PatientSimilarityView;
import org.phenotips.data.similarity.genotype.RestrictedPatientGenotypeSimilarityView;
import org.phenotips.data.similarity.phenotype.DefaultPatientPhenotypeSimilarityView;
import org.phenotips.matchingnotification.match.PatientInMatch;
import org.phenotips.matchingnotification.match.PatientMatch;

import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.users.User;
import org.xwiki.users.UserManager;

import java.io.Serializable;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.MappedSuperclass;
import javax.persistence.Transient;

import org.apache.commons.lang3.StringUtils;
import org.hibernate.CallbackException;
import org.hibernate.Session;
import org.hibernate.classic.Lifecycle;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @version $Id$
 */
@MappedSuperclass()
@SuppressWarnings({ "checkstyle:ClassFanOutComplexity", "checkstyle:ClassDataAbstractionCoupling" })
public class AbstractPatientMatch implements PatientMatch, Lifecycle
{
    protected static final Logger LOGGER = LoggerFactory.getLogger(AbstractPatientMatch.class);

    protected static final int DB_HREF_FIELD_LENGTH = 2048;

    protected static final int DB_MAX_DEFAULT_STRING_LENGTH = 255;

    /** separates between tokens. */
    protected static final String SEPARATOR = ";";

    protected static final String ID = "id";

    protected static final String JSON_KEY_INTERACTIONS = "interactions";

    protected static final String USER_CONTACTED = "user-contacted";

    protected static final UserManager USER_MANAGER;

    protected static final double DOUBLE_COMPARE_EPSILON = 0.0000001;

    /*
     * Attributes of the match
     */
    @Id
    @GeneratedValue
    protected Long id;

    @Basic
    protected Timestamp foundTimestamp;

    /**
     * @deprecated use {@link status} instead
     */
    @Basic
    @Deprecated
    protected Boolean rejected;

    @Basic
    protected String status;

    @Basic
    @Column(length = 0xFFFFFF)
    protected String comments;

    @Basic
    @Column(length = 0xFFFFFF)
    protected String notes;

    @Basic
    protected Double score;

    @Basic
    protected Double genotypeScore;

    @Basic
    protected Double phenotypeScore;

    /**
     * @deprecated use {@link referenceDetails} and {@link matchedDetails} owner info instead
     */
    @Basic
    @Deprecated
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
    protected String href;

    /*
     * Attributes of reference patient
     */
    @Basic
    protected String referencePatientId;

    /* Local server is stored as "" (to avoid complications of dealing with `null`-s in SQL),
     * but getters return it as `null`
     */
    @Basic
    protected String referenceServerId;

    @Basic
    @Column(length = 0xFFFFFF)
    protected String referenceDetails;

    @Transient
    protected PatientInMatch referencePatientInMatch;

    /*
     * Attributes of matched patient
     */
    @Basic
    protected String matchedPatientId;

    /* Local server is stored as "" (to avoid complications of dealing with `null`-s in SQL),
     * but getters return it as `null`
     */
    @Basic
    protected String matchedServerId;

    @Basic
    @Column(length = 0xFFFFFF)
    protected String matchedDetails;

    @Basic
    @Column(length = 0xFFFFFF)
    protected String notificationHistory;

    @Transient
    protected PatientInMatch matchedPatientInMatch;

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
    public AbstractPatientMatch()
    {
    }

    /**
     * Create a PatientMatch from a PatientSimilarityView.
     *
     * @param similarityView the object to read match from
     * @param referenceServerId id of server where reference patient is found
     * @param matchedServerId if of server where matched patient is found
     */
    public AbstractPatientMatch(PatientSimilarityView similarityView, String referenceServerId, String matchedServerId)
    {
        this.initialize(similarityView, referenceServerId, matchedServerId);
    }

    private void initialize(PatientSimilarityView similarityView, String referenceServerId, String matchedServerId)
    {
        // Reference patient
        Patient referencePatient = similarityView.getReference();
        this.referencePatientId = this.limitStringLength(referencePatient.getId(), DB_MAX_DEFAULT_STRING_LENGTH);
        this.referenceServerId = (referenceServerId == null) ? "" : referenceServerId;
        Set<String> matchedGenes = similarityView.getMatchingGenes();
        // we want to store local server ID as "" to avoid complications of dealing with `null`-s in SQL
        this.referencePatientInMatch = new DefaultPatientInMatch(referencePatient, this.referenceServerId,
                matchedGenes);

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
        this.matchedPatientInMatch = new DefaultPatientInMatch(matchedPatient, this.matchedServerId,
                matchedGenes);

        // Properties of the match
        this.foundTimestamp = new Timestamp(System.currentTimeMillis());
        this.notificationHistory = null;
        this.comments = null;
        this.notes = null;
        this.status = "uncategorized";
        this.score = similarityView.getScore();
        this.phenotypeScore = similarityView.getPhenotypeScore();
        this.genotypeScore = similarityView.getGenotypeScore();

        // After reordering!
        this.referenceDetails = this.referencePatientInMatch.getDetailsColumnJSON().toString();
        this.matchedDetails = this.matchedPatientInMatch.getDetailsColumnJSON().toString();
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
            JSONArray records = (this.notes != null) ? new JSONArray(this.notes) : new JSONArray();
            JSONObject newRecord = new JSONObject();
            newRecord.put("user", currentUserId);
            newRecord.put("note", note);

            if (records.isEmpty()) {
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

            this.notes = records.toString();
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

            JSONArray records = new JSONArray(this.notes);

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

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        json.put("foundTimestamp", sdf.format(this.foundTimestamp));

        json.put("status", this.getStatus());
        json.put("score", this.getScore());
        json.put("genotypicScore", this.getGenotypeScore());
        json.put("phenotypicScore", this.getPhenotypeScore());
        json.put("href", this.isLocal() ? "" : this.getHref());
        json.put("comments", this.getComments());
        json.put("notificationHistory", this.getNotificationHistory());
        json.put("notes", this.getNote());

        json.put("phenotypesSimilarity", getFeatureMatchesJSON());
        json.put("genotypeSimilarity", getGenotypeSimilarityJSON());

        return json;
    }

    @Override
    public boolean equals(Object obj)
    {
        if (obj == null || obj.getClass() != getClass()) {
            return false;
        }

        AbstractPatientMatch other = (AbstractPatientMatch) obj;

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
    public boolean hasSameMatchData(PatientMatch other)
    {
        return isEquivalent(other)
            && sameScore(other)
            && StringUtils.equals(this.getMatchedDetails(), other.getMatchedDetails())
            && StringUtils.equals(this.getReferenceDetails(), other.getReferenceDetails());
    }

    /**
     * Compares match scores of this match and other match.
     * @param other other match
     * @return true if all match scores are the same
     */
    public boolean sameScore(PatientMatch other)
    {
        return Math.abs(this.getScore() - other.getScore()) < DOUBLE_COMPARE_EPSILON
            && Math.abs(this.getPhenotypeScore() - other.getPhenotypeScore()) < DOUBLE_COMPARE_EPSILON
            && Math.abs(this.getGenotypeScore() - other.getGenotypeScore()) < DOUBLE_COMPARE_EPSILON;
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
        this.initializePatientInMatchesFromDBData();
    }

    protected void initializePatientInMatchesFromDBData()
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

            this.setNotificationHistory(history);
        } catch (JSONException ex) {
            // error parsing notification history/ new record JSON string to JSON object happened
        }
    }

    @Override
    public void setExternallyContacted(boolean isExternallyContacted)
    {
        try {
            JSONObject history = (this.notificationHistory != null) ? new JSONObject(this.notificationHistory)
                : new JSONObject();
            history.put(USER_CONTACTED, isExternallyContacted);

            this.setNotificationHistory(history);
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

    @Override
    public JSONArray getFeatureMatchesJSON()
    {
        PatientPhenotypeSimilarityView featuresView =
            new DefaultPatientPhenotypeSimilarityView(this.matchedPatientInMatch.getPhenotypes(),
                this.referencePatientInMatch.getPhenotypes());

        return featuresView.toJSON();
    }

    @Override
    public JSONArray getGenotypeSimilarityJSON()
    {
        Patient matched = this.matchedPatientInMatch.getPatient();
        // if matched is remote, instantiate RemotePatient
        if (matched == null) {
            matched = new RemoteMatchingPatient(this.matchedPatientInMatch.getPatientId(), null, null, null,
                getPhenoTipsGenes(this.matchedPatientInMatch.getCandidateGenes()), null);
        }

        Patient ref = this.referencePatientInMatch.getPatient();
        // if matched is remote, instantiate RemotePatient
        if (ref == null) {
            ref = new RemoteMatchingPatient(this.referencePatientInMatch.getPatientId(), null, null, null,
                getPhenoTipsGenes(this.referencePatientInMatch.getCandidateGenes()), null);
        }

        PatientGenotypeSimilarityView genesView =
            new RestrictedPatientGenotypeSimilarityView(matched, ref, this.matchedPatientInMatch.getAccessType());

        return genesView.toJSON();
    }

    private Set<Gene> getPhenoTipsGenes(Set<String> genes)
    {
        Set<Gene> geneSet = new HashSet<>();
        for (String geneName : genes) {
            if (StringUtils.isBlank(geneName)) {
                continue;
            }
            Gene gene = new PhenoTipsGene(null, geneName, null, Collections.singleton(""), null);
            geneSet.add(gene);
        }
        return geneSet;
    }
}
