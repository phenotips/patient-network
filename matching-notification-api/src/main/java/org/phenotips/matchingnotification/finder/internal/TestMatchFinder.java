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

import org.phenotips.matchingnotification.finder.MatchFinder;
import org.phenotips.matchingnotification.match.PatientMatch;
import org.phenotips.matchingnotification.match.internal.DefaultPatientMatch;

import org.xwiki.component.annotation.Component;

import java.util.LinkedList;
import java.util.List;

import javax.inject.Singleton;

/**
 * @version $Id$
 */
@Component
@Singleton
public class TestMatchFinder implements MatchFinder
{
    @Override
    public int getPriority() {
        return 0;
    }

    @Override
    public List<PatientMatch> findMatches() {
        List<PatientMatch> matches = new LinkedList<>();

        String otherPatient = "Q0000001";

        PatientMatch match1 = new DefaultPatientMatch("P0000001", otherPatient);
        matches.add(match1);

        PatientMatch match2 = new DefaultPatientMatch("P0000002", otherPatient);
        matches.add(match2);

        PatientMatch match3 = new DefaultPatientMatch("P0000003", "Q0000003");
        matches.add(match3);

        return matches;
    }
}
