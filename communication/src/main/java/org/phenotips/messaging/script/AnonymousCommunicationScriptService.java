/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.phenotips.messaging.script;

import org.phenotips.data.similarity.PatientSimilarityView;
import org.phenotips.messaging.Connection;
import org.phenotips.messaging.ConnectionManager;

import org.xwiki.component.annotation.Component;
import org.xwiki.script.service.ScriptService;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

/**
 * Stores a connection between the owners of two matched patients, anonymously, to be used for email communication. The
 * identities of the two parties are kept private, since mails are sent behind the scenes, while the users only see the
 * {@link #id identifier of the connection}.
 * 
 * @version $Id$
 * @since 1.0M1
 */
@Component
@Named("anonymousCommunication")
@Singleton
public class AnonymousCommunicationScriptService implements ScriptService
{
    @Inject
    private ConnectionManager manager;

    /**
     * Create, store and return a new connection, with the data from the passed patient pair view.
     * 
     * @param patientPair the two patients and their owners that are involved in this connection
     * @return a new connection object, already saved in the storage
     */
    public Connection innitiateConnection(PatientSimilarityView patientPair)
    {
        try {
            return this.manager.innitiateConnection(patientPair);
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * Retrieve an existing connection from the storage.
     * 
     * @param id the identifier of the requested connection
     * @return the requested connection, if it was found in the database, {@code null} otherwise
     */
    public Connection getConnectionById(Long id)
    {
        try {
            return this.manager.getConnectionById(id);
        } catch (Exception ex) {
            return null;
        }
    }

}
