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
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.phenotips.data.similarity;

import org.phenotips.data.Patient;

import org.xwiki.component.annotation.Role;
import org.xwiki.stability.Unstable;

/**
 * This class allows access to an {@link Exome} object for a given patient.
 *
 * @version $Id$
 * @since 1.0M6
 */
@Unstable
@Role
public interface ExomeManager
{
    /**
     * Get the (potentially-cached) {@link Exome} for the given {@link Patient}.
     *
     * @param p the patient for which the {@link Exome} will be retrieved
     * @return the corresponding {@link Exome}, or {@code null} if no exome available
     */
    Exome getExome(Patient p);
}
