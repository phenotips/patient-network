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

import org.phenotips.matchingnotification.match.PatientMatch;
import org.phenotips.matchingnotification.storage.MatchStorageManager;

import org.xwiki.component.annotation.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

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
    public void saveMatches(List<PatientMatch> matches)
    {
        Session session = this.sessionFactory.getSessionFactory().openSession();
        Transaction t = session.beginTransaction();
        try {
            for (PatientMatch match : matches) {
                session.save(match);
            }
            t.commit();
        } catch (HibernateException ex) {
            this.logger.error("ERROR storing matches: [{}]", ex);
            if (t != null) {
                t.rollback();
            }
            throw ex;
        } finally {
            session.close();
        }
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

    @Override
    public List<PatientMatch> loadMatchesByReferencePatientId(String referencePatientId)
    {
        if (StringUtils.isNotEmpty(referencePatientId)) {
            return this.loadMatchesByCriteria(
                new Criterion[] { Restrictions.eq("referencePatientId", referencePatientId) });
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
    public boolean deleteMatches(Session session, List<PatientMatch> matches)
    {
        for (PatientMatch match : matches) {
            session.delete(match);
        }
        return true;
    }
}
