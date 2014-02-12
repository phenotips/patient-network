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
package org.phenotips.messaging;

import org.phenotips.data.similarity.PatientSimilarityView;

import org.xwiki.component.annotation.Role;

/**
 * Creates and retrieves {@link Connection}s.
 * 
 * @version $Id$
 * @since 1.0M1
 */
@Role
public interface ConnectionManager
{
    /**
     * Create, store and return a new connection, with the data from the passed patient pair view.
     * 
     * @param patientPair the two patients and their owners that are involved in this connection
     * @return a new connection object, already saved in the storage
     */
    Connection innitiateConnection(PatientSimilarityView patientPair);

    /**
     * Retrieve an existing connection from the storage.
     * 
     * @param id the identifier of the requested connection
     * @return the requested connection, if it was found in the database, {@code null} otherwise
     */
    Connection getConnectionById(Long id);
}
