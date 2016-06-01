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
package org.phenotips.messaging.internal;

import org.phenotips.data.similarity.PatientSimilarityView;
import org.phenotips.data.similarity.PatientSimilarityViewFactory;
import org.phenotips.messaging.Connection;
import org.phenotips.messaging.ConnectionManager;

import org.xwiki.component.annotation.Component;

import java.util.List;
import java.util.UUID;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.hibernate.Criteria;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.criterion.Example;

import com.xpn.xwiki.store.hibernate.HibernateSessionFactory;

/**
 * Default implementation for the {@code ConnectionManager} role, based on Hibernate for storage.
 *
 * @version $Id$
 * @since 1.0M1
 */
@Component
@Singleton
public class DefaultConnectionManager implements ConnectionManager
{
    /** Handles persistence. */
    @Inject
    private HibernateSessionFactory sessionFactory;

    /** Converts possibly restricted patient pairs into open patient pairs, to be able to read all their data. */
    @Inject
    private PatientSimilarityViewFactory publicPatientSimilarityViewFactory;

    @Override
    public Connection getConnection(PatientSimilarityView patientPair)
    {
        Session session = this.sessionFactory.getSessionFactory().openSession();
        Criteria c = session.createCriteria(Connection.class);
        Connection connection = new Connection(this.publicPatientSimilarityViewFactory.convert(patientPair));
        c.add(Example.create(connection).excludeProperty("id"));
        @SuppressWarnings("unchecked")
        List<Connection> foundEntries = c.list();
        if (foundEntries.isEmpty()) {
            Transaction t = session.beginTransaction();
            t.begin();
            session.save(connection);
            t.commit();
            return connection;
        }
        return foundEntries.get(0);
    }

    @Override
    public Connection getConnectionById(Long id)
    {
        Session session = this.sessionFactory.getSessionFactory().openSession();
        return (Connection) session.load(Connection.class, id);
    }

    @Override
    public Connection getConnectionByToken(String token)
    {
        Session session = this.sessionFactory.getSessionFactory().openSession();
        return (Connection) session.load(Connection.class, UUID.fromString(token));
    }
}
