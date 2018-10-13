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

import org.phenotips.matchingnotification.match.PatientMatch;

import org.xwiki.component.annotation.Component;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReferenceSerializer;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.hibernate.HibernateException;
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
 * Migration for PatientNetwork issue PN-241: in matching notification table
 * replace all serverID values (match or reference) which are `null` (implying local server) with an empty string.
 *
 * Also previously an incorrect constrain was defined which prevents storing both an "as notified" and
 * "current" match between the same patients, so the constraint should be dropped (it is also removed in the
 * coresponding object by PN-240).
 *
 * Note: after the update there may be duplicate records, not identified as duplicate in the past because
 * of `null` values. Dropping the uniquenes constraint before updating `null`-s with empty strings guarantees the
 * constrsaint would not prevent the update due to uniqueness violation. Ideally duplicates should be removed,
 * but they will naturally be removed after the next matching update.
 *
 * @version $Id$
 * @since 1.1
 */
@Component
@Named("R71502PatientNetwork241")
@Singleton
public class R71502PatientNetwork241DataMigration extends AbstractHibernateDataMigration
{
    /** Logging helper object. */
    @Inject
    private Logger logger;

    /** Resolves unprefixed document names to the current wiki. */
    @Inject
    @Named("current")
    private DocumentReferenceResolver<String> resolver;

    /** Serializes the PatientMatch class name. */
    @Inject
    @Named("compactwiki")
    private EntityReferenceSerializer<String> serializer;

    /** Handles persistence. */
    @Inject
    private HibernateSessionFactory sessionFactory;

    @Override
    public String getDescription()
    {
        return "Changes local serverIDs from null to empty strings in the matchingnotification table.";
    }

    @Override
    public XWikiDBVersion getVersion()
    {
        // This has the same version as R71502PatientNetwork178DataMigration because version 71503 already exists in 1.4
        // and using something bigger than that would cause migrating to PT 1.4+ would fail.
        // To ensure that this runs, we must add this in xwiki.cfg:
        // xwiki.store.migration.force=R71502PatientNetwork241
        return new XWikiDBVersion(71502);
    }

    @Override
    public void hibernateMigrate() throws DataMigrationException, XWikiException
    {
        Session session = this.sessionFactory.getSessionFactory().openSession();
        Transaction t = session.beginTransaction();

        try {
            SQLQuery findConstraintNameQuery = session.createSQLQuery("SELECT CONSTRAINT_NAME FROM"
                    + " INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE TABLE_NAME = 'PATIENT_MATCHING'"
                    + " AND CONSTRAINT_TYPE = 'UNIQUE'");
            String constraintName = (String) findConstraintNameQuery.uniqueResult();

            if (constraintName != null) {
                SQLQuery dropOutdatedConstraint = session.createSQLQuery("alter table PATIENT_MATCHING"
                         + " drop constraint " + constraintName);
                dropOutdatedConstraint.executeUpdate();
            } else {
                this.logger.error("unexpected database state: constraint not found for table PATIENT_MATCHING");
            }

            Query q1 = session.createQuery("update " + PatientMatch.class.getCanonicalName()
                     + " set referenceServerId = '' where referenceServerId is null");
            q1.executeUpdate();

            Query q2 = session.createQuery("update " + PatientMatch.class.getCanonicalName()
                     + " set matchedServerId = '' where matchedServerId is null");
            q2.executeUpdate();

            t.commit();
        } catch (HibernateException ex) {
            this.logger.error("Failed to migrate serverIDs: {}", ex.getMessage());
            if (t != null) {
                t.rollback();
            }
        } finally {
            session.close();
        }
    }
}
