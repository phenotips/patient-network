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
import org.phenotips.matchingnotification.storage.PatientMatchStorageManager;

import org.xwiki.component.annotation.Component;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.slf4j.Logger;

import com.xpn.xwiki.store.hibernate.HibernateSessionFactory;

/**
 * @version $Id$
 */
@Component
@Singleton
public class DefaultPatientMatchStorageManager implements PatientMatchStorageManager
{
    /** Handles persistence. */
    @Inject
    private HibernateSessionFactory sessionFactory;

    /** Logging helper object. */
    @Inject
    private Logger logger;

    @Override
    public void saveMatch(PatientMatch match) {
        Session session = this.sessionFactory.getSessionFactory().openSession();
        Transaction t = session.beginTransaction();
        try {
            Long id = (Long) session.save(match);
            t.commit();
            this.logger
            .info("Stored a match for patient [{}] with id [{}]", match.getPatientId(), id);
        } catch (HibernateException ex) {
            this.logger.error("ERROR storing a match for patient [{}]: [{}]", match.getPatientId(), ex);
            if (t != null) {
                t.rollback();
            }
            throw ex;
        } finally {
            session.close();
        }
    }
}
