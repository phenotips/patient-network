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
package org.phenotips.data.similarity;

import org.phenotips.data.permissions.AccessLevel;

import org.xwiki.stability.Unstable;

/**
 * Helper class providing quick information about an {@link AccessLevel access level}.
 *
 * @version $Id$
 * @since 1.0M1
 */
@Unstable
public interface AccessType
{
    /**
     * The real level of access the user has on the patient's data.
     *
     * @return the computed access level, can not be {@code null}
     */
    AccessLevel getAccessLevel();

    /**
     * Indicates full access to the patient's data.
     *
     * @return {@code true} if the user has full access to the patient data, {@code false} otherwise
     */
    boolean isOpenAccess();

    /**
     * Indicates limited, obfuscated access to the patient's data.
     *
     * @return {@code true} if the user has only limited access to the patient data, {@code false} otherwise
     */
    boolean isLimitedAccess();

    /**
     * Indicates no access to the patient's data.
     *
     * @return {@code true} if the user has no access to the patient data, {@code false} otherwise
     */
    boolean isPrivateAccess();
}
