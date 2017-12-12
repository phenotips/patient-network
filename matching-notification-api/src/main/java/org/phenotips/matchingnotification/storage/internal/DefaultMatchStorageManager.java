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

        Session session = this.beginNotificationMarkingTransaction();
        for (String ptId : refPatients) {
            this.deleteMatches(session, this.loadLocalMatchesByPatientId(ptId));
        }
        this.saveMatches(session, matches);
        return this.endNotificationMarkingTransaction(session);
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
            if (isIncoming) {
                matchesToDelete.addAll(this.loadIncomingMatchesByPatientId(ptId, serverId));
            } else {
                matchesToDelete.addAll(this.loadOutgoingMatchesByPatientId(ptId, serverId));
            }
        }

        this.logger.debug("Deleting {} existing matches which are being replaced by {} new matches"
                          + " (server: [{}], incoming: [{}])",
                          matchesToDelete.size(), matchesToSave.size(), serverId, isIncoming);

        Session session = this.beginNotificationMarkingTransaction();
        this.deleteMatches(session, matchesToDelete);
        this.saveMatches(session, matchesToSave);
        return this.endNotificationMarkingTransaction(session);
    }

    @Override
    public List<PatientMatch> loadMatches(double score, boolean notified)
    {
        return this.loadMatchesByCriteria(
            new Criterion[] { Restrictions.ge("score", score),
            Restrictions.eq("notified", notified) });
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

    private List<PatientMatch> loadLocalMatchesByPatientId(String patientId)
    {
        if (StringUtils.isNotEmpty(patientId)) {
            return this.loadMatchesByCriteria(
                new Criterion[] { Restrictions.or(Restrictions.and(Restrictions.eq("referencePatientId", patientId),
                                                                   Restrictions.eq("referenceServerId", null)),
                                                    Restrictions.and(Restrictions.eq("matchedPatientId", patientId),
                                                                     Restrictions.eq("matchedServerId", null)))});
        } else {
            return Collections.emptyList();
        }
    }

    private List<PatientMatch> loadOutgoingMatchesByPatientId(String patientId, String serverId)
    {
        if (StringUtils.isNotEmpty(patientId) && StringUtils.isNotEmpty(serverId)) {
            return this.loadMatchesByCriteria(
                new Criterion[] { Restrictions.and(Restrictions.eq("referencePatientId", patientId),
                                                   Restrictions.eq("matchedServerId", serverId))});
        } else {
            return Collections.emptyList();
        }
    }

    private List<PatientMatch> loadIncomingMatchesByPatientId(String patientId, String serverId)
    {
        if (StringUtils.isNotEmpty(patientId) && StringUtils.isNotEmpty(serverId)) {
            return this.loadMatchesByCriteria(
                new Criterion[] { Restrictions.and(Restrictions.eq("referencePatientId", patientId),
                                                   Restrictions.eq("referenceServerId", serverId))});
        } else {
            return Collections.emptyList();
        }
    }

    @Override
    public List<PatientMatch> loadMatchesBetweenPatients(String patientId1, String patientId2)
    {
        if (StringUtils.isNotEmpty(patientId1) && StringUtils.isNotEmpty(patientId2)) {
            String[] ids = new String[] {patientId1, patientId2};
            return this.loadMatchesByCriteria(
                new Criterion[] { Restrictions.and(Restrictions.in("referencePatientId", ids),
                                                   Restrictions.in("matchedPatientId", ids)) });
        } else {
            return Collections.emptyList();
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
    public Session beginNotificationMarkingTransaction()
    {
        Session session = this.sessionFactory.getSessionFactory().openSession();
        session.beginTransaction();
        return session;
    }

    @Override
    public boolean markNotified(Session session, List<PatientMatch> matches)
    {
        for (PatientMatch match : matches) {
            match.setNotified();
            session.update(match);
        }
        return true;
    }

    @Override
    public boolean setStatus(Session session, List<PatientMatch> matches, String status)
    {
        for (PatientMatch match : matches) {
            match.setStatus(status);
            session.update(match);
        }
        return true;
    }

    @Override
    public boolean endNotificationMarkingTransaction(Session session)
    {
        Transaction t = null;
        try {
            t = session.getTransaction();
            t.commit();
        } catch (HibernateException ex) {
            this.logger.error("ERROR marking matches as notified", ex);
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
    public boolean deleteMatches(String patientId)
    {
        List<PatientMatch> matches = this.loadLocalMatchesByPatientId(patientId);
        Session session = this.beginNotificationMarkingTransaction();
        this.deleteMatches(session, matches);
        return this.endNotificationMarkingTransaction(session);
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
