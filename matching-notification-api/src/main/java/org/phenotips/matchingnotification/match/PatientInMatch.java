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

import org.phenotips.matchingnotification.match.internal.PhenotypesMap;

import java.util.Set;

import org.json.JSONObject;

/**
 * @version $Id$
 */
public interface PatientInMatch
{
    /**
     * @return id of patient.
     */
    String getPatientId();

    /**
     * @return id of server where patient is found. Null if local.
     */
    String getServerId();

    /**
     * @return patient's owner email.
     */
    String getEmail();

    /**
     * @return a set of candidate genes for patient.
     */
    Set<String> getCandidateGenes();

    /**
     * @return phenotypes map
     */
    PhenotypesMap getPhenotypesMap();

    /**
     * @return JSON representation of object
     */
    JSONObject toJSON();
}
