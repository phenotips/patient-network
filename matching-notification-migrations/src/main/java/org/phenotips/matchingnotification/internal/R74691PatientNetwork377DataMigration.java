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
 * Migration for PatientNetwork issue PN-377: Migrate existing comments to the new JSON string format to store comments
 * from multiple users. Migrator alters matching notification table column "comment" by renaming it to "comments" and
 * increasing column size which holds all comments JSON string to 0xFFFFFF length.
 *
 * Existing comments will be migrated to be contained in the JSON object.
 *
 * @version $Id$
 * @since 1.3
 */
@Component
@Named("R74691PatientNetwork377")
@Singleton
public class R74691PatientNetwork377DataMigration extends AbstractHibernateDataMigration
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
        return "Migrate existing comments to the new format.";
    }

    @Override
    public XWikiDBVersion getVersion()
    {
        return new XWikiDBVersion(74691);
    }

    @Override
    public void hibernateMigrate() throws DataMigrationException, XWikiException
    {
        Session session = this.sessionFactory.getSessionFactory().openSession();
        Transaction t = session.beginTransaction();

        try {
            SQLQuery changeName = session.createSQLQuery(
                "ALTER TABLE patient_matching CHANGE comment comments VARCHAR(0xFFFFFF)");
            changeName.executeUpdate();

            Query q1 = session.createQuery("update " + PatientMatch.class.getCanonicalName()
                + " set comments = ('{ comments:[{ comment:' + comments + '}] }') where comments is not null");
            q1.executeUpdate();

            t.commit();
        } catch (HibernateException ex) {
            this.logger.error("Failed to modify comment column: [{}]", ex.getMessage());
            if (t != null) {
                t.rollback();
            }
        } finally {
            session.close();
        }
    }
}
