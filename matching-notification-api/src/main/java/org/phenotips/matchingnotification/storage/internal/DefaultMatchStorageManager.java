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
package org.phenotips.matchingnotification.storage.internal;

import org.phenotips.data.similarity.PatientSimilarityView;
import org.phenotips.matchingnotification.match.PatientMatch;
import org.phenotips.matchingnotification.match.internal.DefaultPatientMatch;
import org.phenotips.matchingnotification.storage.MatchStorageManager;

import org.xwiki.component.annotation.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.hibernate.Criteria;
import org.hibernate.HibernateException;
import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.criterion.Criterion;
import org.hibernate.criterion.Restrictions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.xpn.xwiki.store.hibernate.HibernateSessionFactory;

/**
 * @version $Id$
 */
@Component
@Singleton
public class DefaultMatchStorageManager implements MatchStorageManager
{
    /** A query used to delete all matcheds for the given local patient (ID == localId). */
    private static final String HQL_DELETE_MATCHES_FOR_LOCAL_PATIENT =
            "delete DefaultPatientMatch where referenceServerId is null"
            + " and matchedServerId is null"
            + " and (referencePatientId = :localId or matchedPatientId = :localId)";

    /**
     *  A sub-query for the query below, to find matches similar to the given match, but that has been notified.
     *  A special case is when there is a similar local match, but where matched and reference patients are reversed.
     */
    private static final String HQL_QUERY_SAME_PATIENT_BUT_NOTIFIED =
            "from DefaultPatientMatch as m2 where m2.notified = true"
            + " and ("
            + " ("
            + "  m.referencePatientId = m2.referencePatientId"
            + "  and m.referenceServerId = m2.referenceServerId"
            + "  and m.matchedPatientId = m2.matchedPatientId"
            + "  and m.matchedServerId = m2.matchedServerId"
            + " ) or ("
            + "  m.referencePatientId = m2.matchedPatientId"
            + "  and m.matchedPatientId = m2.referencePatientId"
            + "  and m.matchedServerId = m2.referenceServerId"
            + "  and m.referenceServerId = m2.matchedServerId)"
            + ")";

    /** A query to find all un-notified matches with a score greater than given (score == minScore). */
    private static final String HQL_QUERY_FIND_UNNOTIFIED_MATCHES_BY_SCORE =
            "from DefaultPatientMatch as m where m.notified = false"
            + " and score > :minScore and not exists (" + HQL_QUERY_SAME_PATIENT_BUT_NOTIFIED + ")";

    /** Handles persistence. */
    @Inject
    private HibernateSessionFactory sessionFactory;

    /** Logging helper object. */
    private Logger logger = LoggerFactory.getLogger(DefaultMatchStorageManager.class);

    @Override
    public boolean saveLocalMatches(List<PatientMatch> matches, String patientId)
    {
        Set<String> refPatients = new HashSet<>();
        refPatients.add(patientId);

        for (PatientMatch match : matches) {
            String refPatientID = match.getReferencePatientId();
            if (!patientId.equals(refPatientID)) {
                refPatients.add(refPatientID);
                this.logger.error("A list of matches for local patient {} also constains matches for patient {}",
                                  patientId, refPatientID);
            }
        }

        Session session = this.beginTransaction();
        for (String ptId : refPatients) {
            this.deleteMatchesForLocalPatient(session, ptId, false);
        }
        this.saveMatches(session, matches);
        return this.endTransaction(session);
    }

    @Override
    public boolean saveLocalMatchesViews(List<PatientSimilarityView> similarPatients, String patientId)
    {
        List<PatientMatch> matches = new LinkedList<>();
        for (PatientSimilarityView similarityView : similarPatients) {
            PatientMatch match = new DefaultPatientMatch(similarityView, null, null);
            matches.add(match);
        }
        return this.saveLocalMatches(matches, patientId);
    }

    @Override
    public boolean saveRemoteMatches(List<? extends PatientSimilarityView> similarityViews, String patientId,
        String serverId, boolean isIncoming)
    {
        Set<String> refPatients = new HashSet<>();
        refPatients.add(patientId);

        List<PatientMatch> matchesToSave = new LinkedList<>();
        for (PatientSimilarityView similarityView : similarityViews) {
            String refPatientID = similarityView.getReference().getId();
            if (!patientId.equals(refPatientID)) {
                refPatients.add(refPatientID);
                if (isIncoming) {
                    this.logger.error(
                        "A list of incoming matches for remote patient {} also constains matches for patient {}",
                        patientId, refPatientID);
                } else {
                    this.logger.error(
                        "A list of outgoing matches for local patient {} also constains matches for patient {}",
                        patientId, refPatientID);
                }
            }
            PatientMatch match = isIncoming ? new DefaultPatientMatch(similarityView, serverId, null)
                            : new DefaultPatientMatch(similarityView, null, serverId);
            matchesToSave.add(match);
        }

        List<PatientMatch> matchesToDelete = new LinkedList<>();
        for (String ptId : refPatients) {
            // load all un-notified matches of the same kind for the given reference patient
            if (isIncoming) {
                matchesToDelete.addAll(this.loadIncomingMatchesByPatientId(ptId, serverId, false));
            } else {
                matchesToDelete.addAll(this.loadOutgoingMatchesByPatientId(ptId, serverId, false));
            }
        }

        this.logger.error("Deleting {} existing MME matches which are being replaced by {} new matches"
                          + " (server: [{}], incoming: [{}])",
                          matchesToDelete.size(), matchesToSave.size(), serverId, isIncoming);

        Session session = this.beginTransaction();
        this.deleteMatches(session, matchesToDelete);
        this.saveMatches(session, matchesToSave);
        return this.endTransaction(session);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<PatientMatch> loadMatches(double score, boolean notified)
    {
        if (notified) {
            // this is simple, just return all matches with the given score which have been notified
            return this.loadMatchesByCriteria(
                    new Criterion[] { Restrictions.ge("score", score), this.notifiedRestriction(notified) });
        }

        // ...else it is more complicated: need to return all matches that haven't been notified, but
        // also exclude matches that have a similar match that have been notified
        //
        // This is because if owners of a match were notified, the notified version of the match
        // will be stored as-is in the database, but new verisons of the match will still keep being
        // placed in the DB, resulting in two similar matche sin the DB:
        //  first:   patientA - patientB - notified     - <data at the moment of notification>
        //  second:  patientA - patientB - NOT notified - <data as of last match check>

        Session session = this.beginTransaction();

        Query query = session.createQuery(HQL_QUERY_FIND_UNNOTIFIED_MATCHES_BY_SCORE);
        query.setParameter("minScore", score);

        List<PatientMatch> result = query.list();

        this.logger.error("Retrieved [{}] un-notified matches with score > [{}]", result.size(), score);

        if (!this.endTransaction(session)) {
            return Collections.emptyList();
        }

        return result;
    }

    @Override
    public List<PatientMatch> loadMatchesByIds(List<Long> matchesIds)
    {
        if (matchesIds != null && matchesIds.size() > 0) {
            return this.loadMatchesByCriteria(
                new Criterion[] { Restrictions.in("id", matchesIds.toArray()) });
        } else {
            return Collections.emptyList();
        }
    }

    private List<PatientMatch> loadOutgoingMatchesByPatientId(String localPatientId, String remoteServerId,
            boolean notifiedStatus)
    {
        if (StringUtils.isNotEmpty(localPatientId) && StringUtils.isNotEmpty(remoteServerId)) {
            return this.loadMatchesByCriteria(
                new Criterion[] { this.notifiedRestriction(notifiedStatus),
                                  this.patientIsReference(localPatientId, null),
                                  Restrictions.eq("matchedServerId", remoteServerId)});
        } else {
            return Collections.emptyList();
        }
    }

    private List<PatientMatch> loadIncomingMatchesByPatientId(String remotePatientId, String remoteServerId,
            boolean notifiedStatus)
    {
        if (StringUtils.isNotEmpty(remotePatientId) && StringUtils.isNotEmpty(remoteServerId)) {
            return this.loadMatchesByCriteria(
                new Criterion[] { this.notifiedRestriction(notifiedStatus),
                                  this.patientIsReference(remotePatientId, remoteServerId) });
        } else {
            return Collections.emptyList();
        }
    }

    @Override
    public List<PatientMatch> loadMatchesBetweenPatients(String patientId1, String serverId1,
            String patientId2, String serverId2)
    {
        if (StringUtils.isNotEmpty(patientId1) && StringUtils.isNotEmpty(patientId2)) {
            return this.loadMatchesByCriteria(
                // either patientId1 is reference and patientId2 is a match, or the other way around
                new Criterion[] { Restrictions.or(
                        Restrictions.and(this.patientIsReference(patientId1, serverId1),
                                         this.patientIsMatch(patientId2, serverId2)),
                        Restrictions.and(this.patientIsReference(patientId2, serverId2),
                                         this.patientIsMatch(patientId1, serverId1)))});
        } else {
            return Collections.emptyList();
        }
    }

    private Criterion notifiedRestriction(boolean notified)
    {
        return Restrictions.eq("notified", notified);
    }

    private Criterion patientIsReference(String patientId, String serverId)
    {
        return Restrictions.and(Restrictions.eq("referencePatientId", patientId),
                                this.compareServerId("referenceServerId", serverId));
    }

    private Criterion patientIsMatch(String patientId, String serverId)
    {
        return Restrictions.and(Restrictions.eq("matchedPatientId", patientId),
                                this.compareServerId("matchedServerId", serverId));
    }

    private Criterion compareServerId(String serverField, String serverId)
    {
        if (serverId == null) {
            return Restrictions.isNull(serverField);
        } else {
            return Restrictions.eq(serverField, serverId);
        }
    }

    @SuppressWarnings("unchecked")
    private List<PatientMatch> loadMatchesByCriteria(Criterion[] criteriaToApply)
    {
        List<PatientMatch> matches = null;
        Session session = this.sessionFactory.getSessionFactory().openSession();
        try {
            Criteria criteria = session.createCriteria(PatientMatch.class);
            if (criteriaToApply != null) {
                for (Criterion c : criteriaToApply) {
                    criteria.add(c);
                }
            }
            matches = criteria.list();
        } catch (HibernateException ex) {
            this.logger.error("loadMatchesByCriteria. Criteria: {},  ERROR: [{}]",
                Arrays.toString(criteriaToApply), ex);
        } finally {
            session.close();
        }
        return matches;
    }

    @Override
    public boolean markNotified(List<PatientMatch> matches)
    {
        Session session = this.beginTransaction();
        for (PatientMatch match : matches) {
            match.setNotified();
            session.update(match);
        }
        return this.endTransaction(session);
    }

    @Override
    public boolean setStatus(List<PatientMatch> matches, String status)
    {
        Session session = this.beginTransaction();
        for (PatientMatch match : matches) {
            match.setStatus(status);
            session.update(match);
        }
        return this.endTransaction(session);
    }

    /**
     * Initializes a transaction. See also {@code endTransaction}.
     *
     * @return the session. Null if initialization failed.
     */
    private Session beginTransaction()
    {
        Session session = this.sessionFactory.getSessionFactory().openSession();
        session.beginTransaction();
        return session;
    }

    /**
     * Commits the transaction. See also {@code startTransaction} and
     * {@code markNotified}.
     *
     * @param session the transaction session created for marking.
     * @return true if successful
     */
    private boolean endTransaction(Session session)
    {
        Transaction t = null;
        try {
            t = session.getTransaction();
            t.commit();
            session.flush();
        } catch (HibernateException ex) {
            this.logger.error("ERROR committing changes to the matching notification database", ex);
            if (t != null) {
                t.rollback();
            }
            return false;
        } finally {
            session.close();
        }
        return true;
    }

    @Override
    public boolean deleteMatchesForLocalPatient(String patientId)
    {
        Session session = this.beginTransaction();
        this.deleteMatchesForLocalPatient(session, patientId, true);
        return this.endTransaction(session);
    }

    private void deleteMatchesForLocalPatient(Session session, String patientId, boolean deleteNotified)
    {
        Query query = session.createQuery(HQL_DELETE_MATCHES_FOR_LOCAL_PATIENT
                + (deleteNotified ? "" : " and notified = false"));
        query.setParameter("localId", patientId);

        int numDeleted = query.executeUpdate();

        this.logger.debug("Removed [{}] stored local matches for patient [{}]", numDeleted, patientId);
    }

    private void deleteMatches(Session session, List<PatientMatch> matches)
    {
        for (PatientMatch match : matches) {
            session.delete(match);
        }
    }

    private void saveMatches(Session session, List<PatientMatch> matches)
    {
        for (PatientMatch match : matches) {
            session.save(match);
        }
    }
}
