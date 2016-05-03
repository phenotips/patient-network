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
package org.phenotips.matchingnotification.finder.internal;

import org.phenotips.data.Patient;
import org.phenotips.matchingnotification.finder.MatchFinder;
import org.phenotips.matchingnotification.match.PatientMatch;
import org.phenotips.matchingnotification.match.internal.DefaultPatientMatch;

import org.xwiki.component.annotation.Component;

import java.util.LinkedList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.slf4j.Logger;

/**
 * @version $Id$
 */
@Component
@Singleton
public class TestMatchFinder implements MatchFinder
{
    @Inject
    private Logger logger;

    @Override
    public int getPriority() {
        return 0;
    }

    @Override
    public List<PatientMatch> findMatches(Patient patient)
    {
        this.logger.debug("Finding test matches for patient {}.", patient.getId());

        List<PatientMatch> matches = new LinkedList<>();

        String server1 = "server1";

        PatientMatch match1 = DefaultPatientMatch.getPatientMatchForDebug(patient.getId(), "Q0000001", server1, true);
        matches.add(match1);

        PatientMatch match2 = DefaultPatientMatch.getPatientMatchForDebug(patient.getId(), "Q0000002", server1, true);
        matches.add(match2);

        return matches;
    }
}
