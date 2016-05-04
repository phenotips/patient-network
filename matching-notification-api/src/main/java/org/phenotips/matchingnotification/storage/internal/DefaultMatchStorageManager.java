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
    @Inject
    private Logger logger;

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
    public List<PatientMatch> loadAllMatches() {
        return this.loadMatchesByCriterion(null);
    }

    @Override
    public List<PatientMatch> loadMatchesByIds(List<Long> matchesIds) {
        if (matchesIds != null && matchesIds.size() > 0) {
            Criterion criterion = Restrictions.in("id", matchesIds.toArray());
            return this.loadMatchesByCriterion(criterion);
        } else {
            return Collections.emptyList();
        }
    }

    @Override
    public List<PatientMatch> loadMatchesByReferencePatientId(String patientId)
    {
        if (StringUtils.isNotEmpty(patientId)) {
            Criterion criterion = Restrictions.eq("patientId", patientId);
            return this.loadMatchesByCriterion(criterion);
        } else {
            return Collections.emptyList();
        }
    }

    @SuppressWarnings("unchecked")
    private List<PatientMatch> loadMatchesByCriterion(Criterion criterion)
    {
        List<PatientMatch> matches = null;
        Session session = this.sessionFactory.getSessionFactory().openSession();
        try {
            Criteria criteria = session.createCriteria(PatientMatch.class);
            if (criterion != null) {
                criteria.add(criterion);
            }
            matches = criteria.list();
        } catch (HibernateException ex) {
            this.logger.error("loadMatchesByCriterion. Criterion: {},  ERROR: [{}]", criterion, ex);
        } finally {
            session.close();
        }
        return matches;
    }

    // TODO remove, for debug.
    @Override
    public void clearMatches() {
        List<PatientMatch> matches = loadAllMatches();
        Session session = this.sessionFactory.getSessionFactory().openSession();
        Transaction t = session.beginTransaction();
        try {
            session.clear();
            for (PatientMatch match : matches) {
                session.delete(match);
            }
            t.commit();
        } catch (HibernateException ex) {
            this.logger.error("ERROR deleting matches", ex);
            if (t != null) {
                t.rollback();
            }
            throw ex;
        } finally {
            session.close();
        }
    }
}
