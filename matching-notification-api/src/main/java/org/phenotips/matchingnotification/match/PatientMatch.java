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

import org.json.JSONObject;

/**
 * @version $Id$
 */
public interface PatientMatch
{
    /**
     * @return unique id of match
     */
    long getId();

    /**
     * @return id of patient that was matched. The owner of this patient is notified.
     */
    String getPatientId();

    /**
     * @return id of other patient. The owner of this patient is not notified while processing this entry.
     */
    String getMatchedPatientId();

    /**
     * @return whether notifications regarding this match were sent.
     */
    boolean isNotified();

    /**
     * Marks that notifications regarding this match were sent.
     */
    void setNotified();

    /**
     * @return object in JSON format.
     */
    JSONObject toJSON();

}
