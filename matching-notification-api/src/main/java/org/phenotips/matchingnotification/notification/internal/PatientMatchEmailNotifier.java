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
package org.phenotips.matchingnotification.notification.internal;

import org.phenotips.matchingnotification.match.PatientMatch;
import org.phenotips.matchingnotification.notification.PatientMatchEmail;
import org.phenotips.matchingnotification.notification.PatientMatchNotificationResponse;
import org.phenotips.matchingnotification.notification.PatientMatchNotifier;

import org.xwiki.component.annotation.Component;
import org.xwiki.mail.MailStatus;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.inject.Singleton;

/**
 * Notifies about patient matches.
 *
 * @version $Id$
 */
@Component
@Singleton
public class PatientMatchEmailNotifier implements PatientMatchNotifier
{
    @Override
    public List<PatientMatchEmail> createEmails(List<PatientMatch> matches)
    {
        Map<String, Collection<PatientMatch>> matchesByPatient = groupByPatient(matches);
        List<PatientMatchEmail> emails = new ArrayList<>(matchesByPatient.size());

        List<String> patientIds = new ArrayList<>(matchesByPatient.keySet());
        Collections.sort(patientIds);

        for (String patientId : patientIds) {
            Collection<PatientMatch> matchesForPatient = matchesByPatient.get(patientId);
            PatientMatchEmail email = new DefaultPatientMatchEmail(matchesForPatient);
            emails.add(email);
        }

        return emails;
    }

    @Override
    public List<PatientMatchNotificationResponse> notify(PatientMatchEmail email)
    {
        email.send();
        if (!email.wasSent()) {
            return Collections.emptyList();
        }

        List<PatientMatchNotificationResponse> responses = new LinkedList<>();

        MailStatus status = email.getStatus();
        Collection<PatientMatch> matches = email.getMatches();
        for (PatientMatch match : matches) {
            PatientMatchNotificationResponse response = new PatientMatchEmailNotificationResponse(status, match);
            responses.add(response);
        }

        return responses;
    }

    /*
     * Takes a list of PatientMatch objects and returns them in a map that groups all matches with same patient id.
     */
    private Map<String, Collection<PatientMatch>> groupByPatient(Collection<PatientMatch> matches)
    {
        Map<String, Collection<PatientMatch>> matchesMap = new HashMap<String, Collection<PatientMatch>>();

        for (PatientMatch match : matches) {
            String patientId = match.getReferencePatientId();
            Collection<PatientMatch> matchesList = matchesMap.get(patientId);
            if (matchesList == null) {
                matchesList = new LinkedList<PatientMatch>();
                matchesMap.put(patientId, matchesList);
            }

            matchesList.add(match);
        }

        return matchesMap;
    }
}
