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
package org.phenotips.data.similarity;

import org.phenotips.data.Patient;

import org.xwiki.stability.Unstable;

/**
 * This class allows access to an {@link #Exome} object for a given patient.
 *
 * @version $Id$
 * @since 1.0M6
 */
@Unstable
public interface ExomeManager
{
    /**
     * Get the (potentially-cached) {@link #Exome} for the patient with the given id.
     *
     * @param p the patient for which the {@link #Exome} will be retrieved
     * @return the corresponding {@link #Exome}, or null if no exome available
     */
    Exome getExome(Patient p);
}