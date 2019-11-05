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

import org.hibernate.Query;
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
 * Migration for PatientNetwork issue PN-506: for each incoming/outgoing MME match pair move the "worse"
 * match to the history table (regardless of if it is incoming or outgoing). A match is cosidered to be
 * "worse" if the match score is lower, or if the score is the same and the match is older.
 *
 * @version $Id$
 * @since 1.3m1
 */
@Component
@Named("R74697PatientNetwork506")
@Singleton
public class R74697PatientNetwork506DataMigration extends AbstractHibernateDataMigration
{
    private static final String SQL_GET_WORSE_EQUIVALENTS_OF_MME_MATCHES =
        "select id, foundTimestamp, referencePatientId, matchedPatientId, referenceServerId, matchedServerId"
        + " from patient_matching as d where exists"
        + " (select * from patient_matching as d2 where"
        + " d2.referencePatientId = d.matchedPatientId and d2.matchedPatientId = d.referencePatientId"
        + " and d2.matchedServerId = d.referenceServerId and d2.referenceServerId = d.matchedServerId"
        + " and ((d2.score > d.score) or (d2.score = d.score and"
        + " (d2.foundTimestamp > d.foundTimestamp or (d2.foundTimestamp = d.foundTimestamp and d2.id > d.id)))"
        + " ))";

    private static final String SQL_COPY_MATCHES_TO_HISTORY_BY_IDS =
        "INSERT patient_matching_history"
        + " (comments, foundTimestamp, genotypeScore, href, matchedDetails,"
        + " matchedPatientId, matchedServerId, notes, notificationHistory, phenotypeScore, referenceDetails,"
        + " referencePatientId, referenceServerId, rejected, score, status)"
        + " select"
        + " comments, foundTimestamp, genotypeScore, href, matchedDetails,"
        + " matchedPatientId, matchedServerId, notes, notificationHistory, phenotypeScore, referenceDetails,"
        + " referencePatientId, referenceServerId, rejected, score, status"
        + " from patient_matching where id in :idlist";

    /** A query used to delete matches by IDs.  */
    private static final String HQL_DELETE_MATCHES_BY_IDS =
        "delete CurrentPatientMatch where id in :idlist";

    /** Logging helper object. */
    @Inject
    private Logger logger;

    /** Handles persistence. */
    @Inject
    private HibernateSessionFactory sessionFactory;

    @Override
    public String getDescription()
    {
        return "Remove older coppies of MME matches to have only one MME match per pair of patients";
    }

    @Override
    public XWikiDBVersion getVersion()
    {
        return new XWikiDBVersion(74697);
    }

    @Override
    public void hibernateMigrate() throws DataMigrationException, XWikiException
    {
        Session session = this.sessionFactory.getSessionFactory().openSession();
        Transaction t = session.beginTransaction();

        try {
            SQLQuery query = session.createSQLQuery(SQL_GET_WORSE_EQUIVALENTS_OF_MME_MATCHES);
            @SuppressWarnings("unchecked")
            List<Object[]> matches = query.list();
            if (matches != null) {
                this.logger.error("Found [{}] older equivalents of MME matches", matches.size());
                this.moveToHistory(session, this.getMatchIDs(matches));
            }
            t.commit();
        } catch (Exception ex) {
            this.logger.error("Failed to remove duplicates of MME matches: [{}]", ex.getMessage());
            if (t != null) {
                t.rollback();
            }
        } finally {
            session.close();
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

    private void moveToHistory(Session session, List<Long> matchIDs)
    {
        if (matchIDs.size() == 0) {
            return;
        }

        // copy duplicat ematches ot the history table
        SQLQuery copyQuery = session.createSQLQuery(SQL_COPY_MATCHES_TO_HISTORY_BY_IDS);
        copyQuery.setParameterList("idlist", matchIDs);
        int numCopied = copyQuery.executeUpdate();
        if (numCopied != matchIDs.size()) {
            this.logger.error("A request to copy {} matches to history only copied {}", matchIDs.size(), numCopied);
        } else {
            this.logger.error("Copied {} duplicate MME matches to history table", numCopied);
        }

        // delete matches
        Query deleteQuery = session.createQuery(HQL_DELETE_MATCHES_BY_IDS);
        deleteQuery.setParameterList("idlist", matchIDs);
        int numDeleted = deleteQuery.executeUpdate();
        if (numDeleted != matchIDs.size()) {
            this.logger.error("A request to delete {} matches only removed {}", matchIDs.size(), numDeleted);
        } else {
            this.logger.error("Removed {} duplicate MME matches", numDeleted);
        }
    }
}
