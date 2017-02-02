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

import java.util.List;

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
import com.xpn.xwiki.store.migration.hibernate.HibernateDataMigration;

/**
 * Migration for PatientNetwork issue PN-178: Create migrator for matches
 * database to migrate rejected fields to status fields.
 *
 * @version $Id$
 * @since 1.1
 */
@Component(roles = { HibernateDataMigration.class })
@Named("R71502PatientNetwork178")
@Singleton
public class R71502PatientNetwork178DataMigration extends AbstractHibernateDataMigration
{
    private static final String REJECTED_NAME = "rejected";

    private static final String UNCATEGORIZED_NAME = "uncategorized";

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
        return "Change boolean rejected field to string status field.";
    }

    @Override
    public XWikiDBVersion getVersion()
    {
        return new XWikiDBVersion(71502);
    }

    @Override
    public void hibernateMigrate() throws DataMigrationException, XWikiException
    {
        Session session = this.sessionFactory.getSessionFactory().openSession();
        Query q = session.createQuery("from " + PatientMatch.class.getCanonicalName());
        @SuppressWarnings("unchecked")
        List<PatientMatch> matches = q.list();
        Transaction t = session.beginTransaction();
        try {
            for (PatientMatch match : matches) {
                match.setStatus(match.isRejected() ? REJECTED_NAME : UNCATEGORIZED_NAME);
                session.update(match);
            }
            t.commit();
        } catch (HibernateException ex) {
            R71502PatientNetwork178DataMigration.this.logger.warn(
                     "Failed to set status to match object [{}]: {}",
                     ex.getMessage());
            if (t != null) {
                t.rollback();
            }
        } finally {
            session.close();
        }
    }
}
