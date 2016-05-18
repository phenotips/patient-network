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
     * Search for an existing connection for the patient pair view; if one exists, return it, otherwise create, store
     * and return a new connection, with the data from the passed patient pair view.
     *
     * @param patientPair the two patients and their owners that are involved in this connection
     * @return a connection object, already saved in the storage
     */
    Connection getConnection(PatientSimilarityView patientPair);

    /**
     * Retrieve an existing connection from the storage.
     *
     * @param id the identifier of the requested connection
     * @return the requested connection, if it was found in the database, {@code null} otherwise
     * @deprecated use {@link #getConnectionByToken(String)} instead
     */
    @Deprecated
    Connection getConnectionById(Long id);

    /**
     * Retrieve an existing connection from the storage.
     *
     * @param token the token of the requested connection
     * @return the requested connection, if it was found in the database, {@code null} otherwise
     */
    Connection getConnectionByToken(String token);
}
