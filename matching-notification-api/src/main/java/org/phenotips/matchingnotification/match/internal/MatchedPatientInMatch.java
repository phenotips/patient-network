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
package org.phenotips.matchingnotification.match.internal;

import org.phenotips.matchingnotification.match.PatientMatch;

import java.util.Set;

/**
 * @version $Id$
 */
public class MatchedPatientInMatch extends AbstractPatientInMatch
{
    private PatientMatch match;

    /**
     * @param match PatientMatch on which this object is based
     */
    public MatchedPatientInMatch(PatientMatch match)
    {
        this.match = match;
    }

    @Override
    public String getPatientId()
    {
        return this.match.getMatchedPatientId();
    }

    @Override
    public String getServerId()
    {
        return this.match.getMatchedServerId();
    }

    @Override
    public String getEmail()
    {
        return this.match.getMatchedEmail();
    }

    @Override
    public Set<String> getCandidateGenes()
    {
        return this.match.getMatchedCandidateGenes();
    }

}
