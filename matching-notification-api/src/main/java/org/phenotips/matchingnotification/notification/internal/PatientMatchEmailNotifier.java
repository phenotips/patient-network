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
import org.phenotips.data.permissions.Owner;
import org.phenotips.data.permissions.PatientAccess;
import org.phenotips.data.permissions.PermissionsManager;
import org.phenotips.matchingnotification.internal.MatchesByPatient;
import org.phenotips.matchingnotification.match.PatientMatch;
import org.phenotips.matchingnotification.notification.PatientMatchEmail;
import org.phenotips.matchingnotification.notification.PatientMatchNotificationResponse;
import org.phenotips.matchingnotification.notification.PatientMatchNotifier;

import org.xwiki.component.annotation.Component;
import org.xwiki.mail.MailStatus;
import org.xwiki.model.reference.EntityReference;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;

/**
 * Notifies about patient matches.
 *
 * @version $Id$
 */
@Component
@Singleton
public class PatientMatchEmailNotifier implements PatientMatchNotifier
{
    @Inject
    private PermissionsManager permissionsManager;

    @Inject
    private Provider<XWikiContext> contextProvider;

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
        // TODO currently only return owner's email. Add emails from projects the patient belongs to.
        Collection<String> emails = new LinkedList<>();

        String ownerEmail = this.getOwnerEmail(patient);
        if (StringUtils.isNotEmpty(ownerEmail)) {
            emails.add(ownerEmail);
        }

        return emails;
    }

    private String getOwnerEmail(Patient patient)
    {
        PatientAccess referenceAccess = this.permissionsManager.getPatientAccess(patient);
        Owner owner = referenceAccess.getOwner();
        if (owner == null) {
            return null;
        }

        EntityReference ownerUser = owner.getUser();

        XWikiContext context = this.contextProvider.get();
        XWiki xwiki = context.getWiki();
        try {
            return xwiki.getDocument(ownerUser, context).getStringValue("email");
        } catch (XWikiException e) {
            this.logger.error("Error reading owner's email for patient {}.", patient.getId(), e);
        }
        return null;
    }
}
