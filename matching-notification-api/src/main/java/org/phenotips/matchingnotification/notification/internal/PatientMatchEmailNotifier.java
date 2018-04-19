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

import org.phenotips.data.ContactInfo;
import org.phenotips.data.Patient;
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
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    public List<PatientMatchEmail> createAdminEmailsToLocalUsers(List<PatientMatch> matches,
            Map<Long, List<String>> matchesIds)
    {
        MatchesByPatient mbp = new MatchesByPatient(matches);
        List<PatientMatchEmail> emails = new LinkedList<>();

        Set<String> patientIds = new HashSet<>();
        for (List<String> ids : matchesIds.values()) {
            patientIds.addAll(ids);
        }

        for (String subjectPatientId : patientIds) {
            Collection<PatientMatch> matchesForPatient = mbp.getMatchesForLocalPatientId(subjectPatientId, true);
            // filter matchesForPatient by matchesIds to contain only matches with ids as a key for subjectPatientId
            Iterator<PatientMatch> matchIterator = matchesForPatient.iterator();
            while (matchIterator.hasNext()) {
                PatientMatch match = matchIterator.next();
                if (!matchesIds.get(match.getId()).contains(subjectPatientId)) {
                    matchIterator.remove();
                }
            }
            if (matchesForPatient.size() == 0) {
                this.logger.error("No matches found for patient [{}] when composing admin notification emails",
                        subjectPatientId);
                continue;
            }
            PatientMatchEmail email = new DefaultAdminPatientMatchEmail(subjectPatientId, matchesForPatient);
            emails.add(email);
        }

        return emails;
    }

    @Override
    public PatientMatchEmail createUserEmail(PatientMatch match, String subjectPatientId, String subjectServerId,
            String customEmailText, String customEmailSubject)
    {
        return new DefaultUserPatientMatchEmail(
                    match, subjectPatientId, subjectServerId, customEmailText, customEmailSubject);
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
        List<String> result = new ArrayList<>();
        if (patient != null) {
            PatientData<ContactInfo> data = patient.getData("contact");
            if (data != null && data.size() > 0) {
                for (ContactInfo contact : data) {
                    result.addAll(contact.getEmails());
                }
            }
        }
        return result;
    }
}
