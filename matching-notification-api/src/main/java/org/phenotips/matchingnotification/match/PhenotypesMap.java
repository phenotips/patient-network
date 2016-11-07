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
package org.phenotips.matchingnotification.match;

import java.util.List;
import java.util.Map;

import org.json.JSONObject;

/**
 * Used for handling the collection of phenotypes of a patient, and in particular, for dealing with predefined and free
 * text phenotypes. It is a map so that the email (velocity) can use expressions like phenotypes.predefined and
 * phenotypes.freeText without special access methods.
 *
 * @version $Id$
 */
public interface PhenotypesMap extends Map<String, List<Map<String, String>>>
{
    /**
     * @return JSON representation of the object
     */
    JSONObject toJSON();
}
