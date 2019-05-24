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

package org.phenotips.matchingnotification.internal;

import org.xwiki.component.annotation.Component;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.hibernate.HibernateException;
import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.slf4j.Logger;

import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.store.hibernate.HibernateSessionFactory;
import com.xpn.xwiki.store.migration.DataMigrationException;
import com.xpn.xwiki.store.migration.XWikiDBVersion;
import com.xpn.xwiki.store.migration.hibernate.AbstractHibernateDataMigration;

/**
 * Migration for PatientNetwork issue PN-456: local matches should be de-duplicated:
 * given A-to-B and B-to-A matches, only A-to-B match should be left, where A < B alphanumericaly
 * (to match how matching notification works after PN-401 is implemented).
 *
 * @version $Id$
 * @since 1.3m1
 */
@Component
@Named("R74696PatientNetwork456")
@Singleton
public class R74696PatientNetwork456DataMigration extends AbstractHibernateDataMigration
{
    private static final String HQL_DELETE_DUPLICATE_LOCAL_MATCHES =
        "delete from CurrentPatientMatch d where d.matchedServerId = '' and d.referenceServerId = ''"
        + " and d.referencePatientId > d.matchedPatientId and exists"
        + " (from CurrentPatientMatch d2 where"
        + " d2.referencePatientId = d.matchedPatientId and d2.matchedPatientId = d.referencePatientId"
        + " and d2.matchedServerId = '' and d2.referenceServerId = '')";

    private static final String NORMALIZE_ORDER_OF_PATIENTS_IN_A_MATCH =
        "update CurrentPatientMatch set referencePatientId = matchedPatientId, matchedPatientId = referencePatientId"
        + " where referencePatientId > matchedPatientId";

    /** Logging helper object. */
    @Inject
    private Logger logger;

    /** Handles persistence. */
    @Inject
    private HibernateSessionFactory sessionFactory;

    @Override
    public String getDescription()
    {
        return "Remove duplicate local matches and normalize order of patients in a match";
    }

    @Override
    public XWikiDBVersion getVersion()
    {
        return new XWikiDBVersion(74696);
    }

    @Override
    public void hibernateMigrate() throws DataMigrationException, XWikiException
    {
        Session session = this.sessionFactory.getSessionFactory().openSession();
        Transaction t = session.beginTransaction();

        try {
            Query qDeduplicate = session.createQuery(HQL_DELETE_DUPLICATE_LOCAL_MATCHES);
            int numDeleted = qDeduplicate.executeUpdate();
            this.logger.error("Deleted [{}] duplicate [A->B]/[B->A] matches", numDeleted);

            Query qNormalize = session.createQuery(NORMALIZE_ORDER_OF_PATIENTS_IN_A_MATCH);
            int numNormalized = qNormalize.executeUpdate();
            this.logger.error("Normalized [{}] matches [A->B where A>B] to [B->A]", numNormalized);

            t.commit();
        } catch (HibernateException ex) {
            this.logger.error("Failed to de-duplicate and normalize local matches: [{}]", ex.getMessage());
            if (t != null) {
                t.rollback();
            }
        } finally {
            session.close();
        }
    }
}
