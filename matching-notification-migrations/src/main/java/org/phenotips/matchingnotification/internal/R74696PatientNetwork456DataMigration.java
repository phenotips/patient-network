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
import org.hibernate.SQLQuery;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.slf4j.Logger;

import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.store.hibernate.HibernateSessionFactory;
import com.xpn.xwiki.store.migration.DataMigrationException;
import com.xpn.xwiki.store.migration.XWikiDBVersion;
import com.xpn.xwiki.store.migration.hibernate.AbstractHibernateDataMigration;

/**
 * Migration for PatientNetwork issue PN-456: the clean up of the matching notificstion database
 * to remove various duplicate matches, and have all matches in the format current code needs:
 *
 * 1) local matches are de-duplicated: given A-to-B and B-to-A matches, only one of them should be left
 *    (plus see 2) below)
 *
 * 2) local B-to-A matches where B > A alphanumerically should be converted to A-to-B matches,
 *    where A < B alphanumericaly (to match how matching notification works after PN-401 is implemented).
 *
 * 2) not notified copies of a notified match (which are not used by either old or new code) are removed
 *
 * 3) not rejected copies of a rejected match (which are not used by either old or new code) are removed
 *
 * @version $Id$
 * @since 1.3m1
 */
@Component
@Named("R74696PatientNetwork456")
@Singleton
public class R74696PatientNetwork456DataMigration extends AbstractHibernateDataMigration
{
    private static final String SQL_DELETE_NON_NOTIFIED_COPIES_OF_A_NOTIFIED_MATCH =
        "delete from patient_matching d where d.notified = false and exists"
        + " (select * from patient_matching d2 where"
        + " d2.referencePatientId = d.referencePatientId and d2.matchedPatientId = d.matchedPatientId"
        + " and d2.matchedServerId = d.matchedServerId and d2.referenceServerId = d.referenceServerId"
        + " and d2.notified = true)";

    private static final String SQL_DELETE_NON_REJECTED_COPIES_OF_A_REJECTED_MATCH =
        "delete from patient_matching d and d.status = 'uncategorized' and exists"
        + " (select * from patient_matching d2 where"
        + " d2.referencePatientId = d.referencePatientId and d2.matchedPatientId = d.matchedPatientId"
        + " and d2.matchedServerId = d.matchedServerId and d2.referenceServerId = d.referenceServerId"
        + " and d2.status = 'rejected')";

    private static final String SQL_DELETE_INVERSE_COPIES_OF_LOCAL_MATCHES =
        "delete from patient_matching d where d.matchedServerId = '' and d.referenceServerId = ''"
        + " and d.referencePatientId > d.matchedPatientId and exists"
        + " (from CurrentPatientMatch d2 where"
        + " d2.referencePatientId = d.matchedPatientId and d2.matchedPatientId = d.referencePatientId"
        + " and d2.matchedServerId = '' and d2.referenceServerId = '')";

    private static final String SQL_NORMALIZE_ORDER_OF_PATIENTS_IN_LOCAL_MATCHES =
        "update patient_matching set referencePatientId = matchedPatientId, matchedPatientId = referencePatientId"
        + ", referenceDetails = matchedDetails, matchedDetails = referenceDetails"
        + " where referencePatientId > matchedPatientId and referenceServerId = '' and matchedServerId = ''";

    /** Logging helper object. */
    @Inject
    private Logger logger;

    /** Handles persistence. */
    @Inject
    private HibernateSessionFactory sessionFactory;

    @Override
    public String getDescription()
    {
        return "Remove duplicate matches and normalize order of patients in a local match";
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
            // the query below depends on a "notified" column in the DB, however the column is no
            // longer mapped to a field in the class, so can't use HQL to address the column
            // (and thus have to use raw SQL)
            SQLQuery qRemoveUnnotifiedCopy =
                    session.createSQLQuery(SQL_DELETE_NON_NOTIFIED_COPIES_OF_A_NOTIFIED_MATCH);
            int numRemoved = qRemoveUnnotifiedCopy.executeUpdate();
            this.logger.error("Deleted [{}] un-notified copies of a notified match", numRemoved);

            SQLQuery qRemoveUnrejectedCopy =
                    session.createSQLQuery(SQL_DELETE_NON_REJECTED_COPIES_OF_A_REJECTED_MATCH);
            numRemoved = qRemoveUnrejectedCopy.executeUpdate();
            this.logger.error("Deleted [{}] non-rejected copies of a rejected match", numRemoved);

            SQLQuery qDeduplicate = session.createSQLQuery(SQL_DELETE_INVERSE_COPIES_OF_LOCAL_MATCHES);
            numRemoved = qDeduplicate.executeUpdate();
            this.logger.error("Deleted [{}] inverse [A->B]/[B->A] copies of local matches", numRemoved);

            SQLQuery qNormalize = session.createSQLQuery(SQL_NORMALIZE_ORDER_OF_PATIENTS_IN_LOCAL_MATCHES);
            int numNormalized = qNormalize.executeUpdate();
            this.logger.error("Normalized [{}] local matches [A->B where A>B] to [B->A]", numNormalized);

            t.commit();
        } catch (HibernateException ex) {
            this.logger.error("Failed to de-duplicate and normalize matches: [{}]", ex.getMessage());
            if (t != null) {
                t.rollback();
            }
        } finally {
            session.close();
        }
    }
}
