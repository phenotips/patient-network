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

import org.phenotips.data.Patient;
import org.phenotips.data.PatientContactsManager;
import org.phenotips.data.PatientData;
import org.phenotips.matchingnotification.internal.MatchesByPatient;
import org.phenotips.matchingnotification.match.PatientMatch;
import org.phenotips.matchingnotification.notification.PatientMatchEmail;
import org.phenotips.matchingnotification.notification.PatientMatchNotificationResponse;
import org.phenotips.matchingnotification.notification.PatientMatchNotifier;

import org.xwiki.component.annotation.Component;
import org.xwiki.mail.MailStatus;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import javax.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Notifies about patient matches.
 *
 * @version $Id$
 */
@Component
@Singleton
public class PatientMatchEmailNotifier implements PatientMatchNotifier
{
    private Logger logger = LoggerFactory.getLogger(PatientMatchEmailNotifier.class);

    @Override
    public List<PatientMatchEmail> createEmails(List<PatientMatch> matches)
    {
        MatchesByPatient mbp = new MatchesByPatient(matches);
        List<PatientMatchEmail> emails = new LinkedList<>();

        List<String> patientIds = new ArrayList<>(mbp.getLocalPatientIds());
        Collections.sort(patientIds);

        for (String subjectPatientId : patientIds) {
            Collection<PatientMatch> matchesForPatient = mbp.getMatchesForLocalPatientId(subjectPatientId, true);
            PatientMatchEmail email = new DefaultPatientMatchEmail(subjectPatientId, matchesForPatient);
            emails.add(email);
        }

        return emails;
    }

    @Override
    public List<PatientMatchNotificationResponse> notify(PatientMatchEmail email)
    {
        email.send();
        if (!email.wasSent()) {
            this.logger.error("Email was not sent successfully: {}.", email.getStatus());
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

    @Override
    public Collection<String> getNotificationEmailsForPatient(Patient patient)
    {
        if (patient != null) {
            PatientData<PatientContactsManager> data = patient.getData("contact");
            if (data != null) {
                return data.getValue().getEmails();
            }
        }
        return Collections.emptyList();
    }
}
