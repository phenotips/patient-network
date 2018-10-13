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
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReferenceSerializer;

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
 * Migration for PatientNetwork issue PN-262: increase column size which holds remote contact info ("href").
 *
 * Some servers (e.g. pubcasefinder) send extremely long links as methods of contact, we should be able to store them.
 *
 * @version $Id$
 * @since 1.1
 */
@Component
@Named("R71502PatientNetwork262")
@Singleton
public class R71502PatientNetwork262DataMigration extends AbstractHibernateDataMigration
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
        return "Increase HREF column width in matching notification table.";
    }

    @Override
    public XWikiDBVersion getVersion()
    {
        // This has the same version as R71502PatientNetwork178DataMigration because version 71503 already exists in 1.4
        // and using something bigger than that would cause migrating to PT 1.4+ would fail.
        // To ensure that this runs, we must add this in xwiki.cfg:
        // xwiki.store.migration.force=R71502PatientNetwork262
        return new XWikiDBVersion(71502);
    }

    @Override
    public void hibernateMigrate() throws DataMigrationException, XWikiException
    {
        Session session = this.sessionFactory.getSessionFactory().openSession();
        Transaction t = session.beginTransaction();

        try {
            SQLQuery increaseColumnSize = session.createSQLQuery(
                    "ALTER TABLE patient_matching MODIFY href VARCHAR(2048)");
            increaseColumnSize.executeUpdate();

            t.commit();
        } catch (HibernateException ex) {
            this.logger.error("Failed to increase matching notification HREF column size: [{}]", ex.getMessage());
            if (t != null) {
                t.rollback();
            }
        } finally {
            session.close();
        }
    }
}
