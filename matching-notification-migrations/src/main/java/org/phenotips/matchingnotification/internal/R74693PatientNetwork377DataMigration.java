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
import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.hibernate.HibernateException;
import org.hibernate.SQLQuery;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;

import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.store.hibernate.HibernateSessionFactory;
import com.xpn.xwiki.store.migration.DataMigrationException;
import com.xpn.xwiki.store.migration.XWikiDBVersion;
import com.xpn.xwiki.store.migration.hibernate.AbstractHibernateDataMigration;

/**
 * Migration for PatientNetwork issue PN-377: Migrate existing comments to the new JSON string format to store comments
 * from multiple users. Existing comments will be migrated to be contained in the JSON object.
 *
 * @version $Id$
 * @since 1.2
 */
@Component
@Named("R74693PatientNetwork377")
@Singleton
public class R74693PatientNetwork377DataMigration extends AbstractHibernateDataMigration
{
    private static final String SQL_ALL_MATCHES_WITH_COMMENTS =
        "select id, comment from patient_matching where comment is NOT NULL";

    private static final String SQL_UPDATE_COMMENTS =
            "update patient_matching set comments = :comments where id = :id";

    /** Logging helper object. */
    @Inject
    private Logger logger;

    /** Handles persistence. */
    @Inject
    private HibernateSessionFactory sessionFactory;

    @Override
    public String getDescription()
    {
        return "Migrate existing comments to the new format.";
    }

    @Override
    public XWikiDBVersion getVersion()
    {
        return new XWikiDBVersion(74693);
    }

    @Override
    public void hibernateMigrate() throws DataMigrationException, XWikiException
    {
        Session session = this.sessionFactory.getSessionFactory().openSession();
        Transaction t = session.beginTransaction();

        try {
            // using raw SQL query here because the "comment" field that the query depends on
            // is longer mapped to the corresponding JAVA class (and thus HQL can not be used)
            SQLQuery qMatchesWithComments = session.createSQLQuery(SQL_ALL_MATCHES_WITH_COMMENTS);
            @SuppressWarnings("unchecked")
            List<Object[]> matches = qMatchesWithComments.list();

            for (Object[] fields : matches) {
                BigInteger id = (BigInteger) fields[0];
                String comment = (String) fields[1];

                // using JSONObject to properly escape the comment body and get a proper JSON string
                JSONObject newComment = new JSONObject();
                newComment.put("comment", comment);
                JSONArray comments = new JSONArray();
                comments.put(newComment);

                SQLQuery qUpdate = session.createSQLQuery(SQL_UPDATE_COMMENTS);
                qUpdate.setParameter("id", id.intValue());
                qUpdate.setParameter("comments", comments.toString());
                qUpdate.executeUpdate();
            }

            t.commit();
        } catch (HibernateException ex) {
            this.logger.error("Failed to migrate comment column: [{}]", ex.getMessage());
            if (t != null) {
                t.rollback();
            }
        } finally {
            session.close();
        }
    }
}
