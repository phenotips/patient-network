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

import java.math.BigInteger;
import java.util.LinkedList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

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
 * Migration for PatientNetwork issue PN-456: the clean up of the matching notification database
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
    // The query below depends on a "notified" column in the DB, however the column is no
    // longer mapped to a field in the class, so can't use HQL to address the column
    // (and thus have to use raw SQL)
    //
    // Also MariaDB & MySQL do not allow to update a table that is used in a sub-query ("ERROR 1093"),
    // so can not find the matchs and delete them in a single "delete where exists" query,
    // and need a separate "select" and "delete" queries
    private static final String SQL_GET_NON_NOTIFIED_COPIES_OF_A_NOTIFIED_MATCH =
        "select id, referencePatientId, matchedPatientId, referenceServerId, matchedServerId, notified"
        + " from patient_matching as d where d.notified = false and exists"
        + " (select * from patient_matching as d2 where"
        + " d2.referencePatientId = d.referencePatientId and d2.matchedPatientId = d.matchedPatientId"
        + " and d2.matchedServerId = d.matchedServerId and d2.referenceServerId = d.referenceServerId"
        + " and d2.notified = true)";

    private static final String SQL_GET_NON_REJECTED_COPIES_OF_A_REJECTED_MATCH =
        "select id, referencePatientId, matchedPatientId, referenceServerId, matchedServerId, status"
        + " from patient_matching as d where d.status = 'uncategorized' and exists"
        + " (select * from patient_matching as d2 where"
        + " d2.referencePatientId = d.referencePatientId and d2.matchedPatientId = d.matchedPatientId"
        + " and d2.matchedServerId = d.matchedServerId and d2.referenceServerId = d.referenceServerId"
        + " and d2.status = 'rejected')";

    private static final String SQL_GET_INVERSE_COPIES_OF_LOCAL_MATCHES =
        "select id, referencePatientId, matchedPatientId, referenceServerId, matchedServerId"
        + " from patient_matching as d where d.matchedServerId = '' and d.referenceServerId = ''"
        + " and d.referencePatientId > d.matchedPatientId and exists"
        + " (select * from patient_matching as d2 where"
        + " d2.referencePatientId = d.matchedPatientId and d2.matchedPatientId = d.referencePatientId"
        + " and d2.matchedServerId = '' and d2.referenceServerId = '')";

    // inspired by https://stackoverflow.com/questions/37649/swapping-column-values-in-mysql
    // had to wrap ":=" in /*'*/ as suggested in https://stackoverflow.com/questions/9460018/
    private static final String SQL_NORMALIZE_ORDER_OF_PATIENTS_IN_LOCAL_MATCHES =
        "update patient_matching set referencePatientId = (@tempId/*'*/:=/*'*/referencePatientId),"
        + " referencePatientId = matchedPatientId, matchedPatientId = @tempId,"
        + " referenceDetails = (@tempDetails/*'*/:=/*'*/referenceDetails),"
        + " referenceDetails = matchedDetails, matchedDetails = @tempDetails"
        + " where referencePatientId > matchedPatientId and referenceServerId = '' and matchedServerId = ''";

    private static final String SQL_DELETE_MATCHES_BY_IDS =
        "delete from patient_matching where id in :idlist";

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
            this.removeMatchesBasedOnQuery(session, SQL_GET_NON_NOTIFIED_COPIES_OF_A_NOTIFIED_MATCH,
                    "un-notified copies of a notified match");

            this.removeMatchesBasedOnQuery(session, SQL_GET_NON_REJECTED_COPIES_OF_A_REJECTED_MATCH,
                    "non-rejected copies of a rejected match");

            this.removeMatchesBasedOnQuery(session, SQL_GET_INVERSE_COPIES_OF_LOCAL_MATCHES,
                    "inverse [A->B]/[B->A] copies of local matches");

            SQLQuery qNormalize = session.createSQLQuery(SQL_NORMALIZE_ORDER_OF_PATIENTS_IN_LOCAL_MATCHES);
            int numNormalized = qNormalize.executeUpdate();
            this.logger.error("Normalized [{}] local matches [A->B where A>B] to [B->A]", numNormalized);

            t.commit();
        } catch (Exception ex) {
            this.logger.error("Failed to de-duplicate and normalize matches: [{}]", ex.getMessage());
            if (t != null) {
                t.rollback();
            }
        } finally {
            session.close();
        }
    }

    private void removeMatchesBasedOnQuery(Session session, String queryString, String description)
    {
        SQLQuery query = session.createSQLQuery(queryString);
        @SuppressWarnings("unchecked")
        List<Object[]> matches = query.list();
        if (matches != null) {
            this.logger.error("Found [{}] {}", matches.size(), description);
            this.deleteMatches(session, this.getMatchIDs(matches));
        }
    }

    // the assumption is that id is the first field in the Object[] array
    private List<Long> getMatchIDs(List<Object[]> matches)
    {
        List<Long> ids = new LinkedList<>();
        for (Object[] fields : matches) {
            BigInteger id = (BigInteger) fields[0];
            ids.add(id.longValue());
        }
        return ids;
    }

    private void deleteMatches(Session session, List<Long> matchIDs)
    {
        if (matchIDs.size() == 0) {
            return;
        }

        SQLQuery query = session.createSQLQuery(SQL_DELETE_MATCHES_BY_IDS);
        query.setParameterList("idlist", matchIDs);
        int numDeleted = query.executeUpdate();
        if (numDeleted != matchIDs.size()) {
            this.logger.error("A request to delete {} matches only removed {}", matchIDs.size(), numDeleted);
        }
    }
}
