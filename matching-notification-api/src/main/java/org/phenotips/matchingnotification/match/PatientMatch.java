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
package org.phenotips.matchingnotification.match;

import org.xwiki.model.EntityType;
import org.xwiki.model.reference.EntityReference;

import java.sql.Timestamp;

import org.json.JSONObject;

/**
 * Encapsulates a match between a reference patient and a matched patient.
 *
 * @version $Id$
 */
public interface PatientMatch
{
    /** The space where matches data is stored. */
    EntityReference DATA_SPACE = new EntityReference("MatchingNotification", EntityType.SPACE);

    /**
     * @return unique id of match
     */
    Long getId();

    /**
     * @return id of reference patient.
     */
    String getReferencePatientId();

    /**
     * @return id of server where reference patient is found. Null if local.
     */
    String getReferenceServerId();

    /**
     * @return id of other patient.
     */
    String getMatchedPatientId();

    /**
     * @return id of server where matched patient is found. Null if local.
     */
    String getMatchedServerId();

    /**
     * @return whether notifications regarding this match were sent.
     */
    Boolean isNotified();

    /**
     * Marks whether notifications regarding this match were sent or not.
     *
     * @param isNotified boolean indicator whether notifications regarding this match were sent or not
     */
    void setNotified(boolean isNotified);

    /**
     * @return true only if match is rejected.
     * @deprecated use {@link #getStatus()} instead
     */
    @Deprecated
    boolean isRejected();

    /**
     * Marks status property of match (saved, rejected or uncategorized).
     *
     * @param status whether saved, rejected or uncategorized
     */
    void setStatus(String status);

    /**
     * Sets match comment.
     *
     * @param comment comment text
     */
    void setComment(String comment);

    /**
     * @return object in JSON format.
     */
    JSONObject toJSON();

    /**
     * @return score of match.
     */
    Double getScore();

    /**
     * @return phenotypical score
     */
    Double getPhenotypeScore();

    /**
     * @return genotypical score
     */
    Double getGenotypeScore();

    /**
     * @return href of remote patient. If this.isLocal() returns null.
     */
    String getHref();

    /**
     * @return status of match: saved, rejected or uncategorized.
     */
    String getStatus();

    /**
     * @return match comment.
     */
    String getComment();

    /**
     * Checks if {@code other} is equivalent to this.
     * A match m1 is equivalent to a match m2 if all the following are true:
     *    m1.referencePatientId = m2.matchedPatientId
     *    m1.referenceServerId  = m2.matchedServerId
     *    m2.matchedPatientId   = m1.referencePatientId
     *    m2.matchedPatientId   = m1.referenceServerId
     *
     * @param other match to compare to
     * @return true if equivalent
     */
    boolean isEquivalent(PatientMatch other);

    /**
     * @return {@code PatientInMatch} object representing the reference patient.
     */
    PatientInMatch getReference();

    /**
     * @return {@code PatientInMatch} object representing the matched patient.
     */
    PatientInMatch getMatched();

    /**
     * Checks if the patient given in parameter is the reference patient of the match.
     *
     * @param patientId id of patient
     * @param serverId id of server where patient is found
     * @return true if patient is reference patient in match
     */
    boolean isReference(String patientId, String serverId);

    /**
     * Checks if the patient given in parameter is the matched patient of the match.
     *
     * @param patientId id of patient
     * @param serverId id of server where patient is found
     * @return true if patient is matched patient in match
     */
    boolean isMatched(String patientId, String serverId);

    /**
     * Checks if a request is local. The relationship between isLocal(), isIncoming() and isOutgoing() is:
     *    {@code isLocal()==true  ==> isIncoming()==false && isOutgoing()==false}
     * and
     *    {@code isLocal()==false ==> only one of (isIncoming(), isOutgoing()) is true}
     *
     * @return true if both reference and matched patients are local.
     */
    boolean isLocal();

    /**
     * See {@link #isLocal}.
     *
     * @return true if match request was initiated in remote server.
     */
    boolean isIncoming();

    /**
     * See {@link #isLocal}.
     *
     * @return true if match was initiated locally and was sent to a remote server.
     */
    boolean isOutgoing();

    /**
     * @return the timestamp when the match was found.
     */
    Timestamp getFoundTimestamp();

    /**
     * Set the timestamp when the match was found.
     *
     * @param timestamp timestamp
     */
    void setFoundTimestamp(Timestamp timestamp);

    /**
     * Set the match score.
     *
     * @param score score
     */
    void setScore(Double score);

    /**
     * Set genotype the score.
     *
     * @param score score
     */
    void setGenotypeScore(Double score);

    /**
     * Set the phenotype score.
     *
     * @param score score
     */
    void setPhenotypeScore(Double score);

    /**
     * Set the reference details.
     *
     * @param details details
     */
    void setReferenceDetails(String details);

    /**
     * Set the reference patient in match when the match was found.
     *
     * @param patient patient
     */
    void setReferencePatientInMatch(PatientInMatch patient);

    /**
     * Set matched details.
     *
     * @param details details
     */
    void setMatchedDetails(String details);

    /**
     * Set the matched patient in match.
     *
     * @param patient patient
     */
    void setMatchedPatientInMatch(PatientInMatch patient);

    /**
     * @return the reference details.
     */
    String getReferenceDetails();

    /**
     * @return the matched details.
     */
    String getMatchedDetails();

    /**
     * @return timestamp the match was notified
     */
    Timestamp getNotifiedTimestamp();

    /**
     * Updates a notification history JSON log string with new notification record.
     * Record is a JSON String object, in the following format:
     *     <pre>
     *      { "from": {"userinfo":{"id":"xwiki:XWiki.Mary",
     *                             "name":"Mary"},
     *                 "emails"  :["mary@blueberry.com"]
     *                },
     *         "to":  {"userinfo":{ "id":"xwiki:XWiki.Pat",
     *                              "name":"Pat Smith",
     *                              "institution":"Western Hospital San Diego"},
     *                  "emails":["patcat@catrescuecentre.kom"]
     *                },
     *         "cc":  ["mary@blueberry.com"],
     *         "subjectPatientId":"P000005"
     *         "type":"contact",
     *         "date":"2018/12/06"
     *       }
     *     </pre>
     *
     * @param notificationRecord the new notification record JSON string
     */
    void updateNotificationHistory(String notificationRecord);
}
