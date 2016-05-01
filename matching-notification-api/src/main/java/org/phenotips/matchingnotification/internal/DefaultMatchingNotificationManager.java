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
package org.phenotips.matchingnotification.internal;

import org.phenotips.data.ConsentManager;
import org.phenotips.data.Patient;
import org.phenotips.data.PatientRepository;
import org.phenotips.data.permissions.PermissionsManager;
import org.phenotips.data.permissions.Visibility;
import org.phenotips.matchingnotification.MatchingNotificationManager;
import org.phenotips.matchingnotification.finder.MatchFinderManager;
import org.phenotips.matchingnotification.match.PatientMatch;
import org.phenotips.matchingnotification.storage.MatchStorageManager;

import org.xwiki.component.annotation.Component;
import org.xwiki.query.Query;
import org.xwiki.query.QueryException;
import org.xwiki.query.QueryManager;

import java.util.LinkedList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.slf4j.Logger;

/**
 * @version $Id$
 */
@Component
@Singleton
public class DefaultMatchingNotificationManager implements MatchingNotificationManager
{
    private static final String REMOTE_MATCHING_CONSENT_ID = "matching";

    @Inject
    private Logger logger;

    @Inject
    private QueryManager qm;

    @Inject
    private PatientRepository patientRepository;

    @Inject
    private ConsentManager consentManager;

    @Inject
    private PermissionsManager permissionsManager;

    @Inject
    @Named("matchable")
    private Visibility matchableVisibility;

    @Inject
    private MatchFinderManager matchFinderManager;

    @Inject
    private MatchStorageManager matchStorageManager;

    @Override
    public boolean findAndSaveMatches()
    {
        List<Patient> patients = this.getPatientsList();
        for (Patient patient : patients) {
            List<PatientMatch> matchesForPatient = this.matchFinderManager.findMatches(patient);
            this.matchStorageManager.saveMatches(matchesForPatient);
        }

        return false;
    }

    /*
     * Returns a list of patients with visibility>=matchable and consent for
     * remote matching.
     */
    private List<Patient> getPatientsList()
    {
        List<String> potentialPatientIds = null;
        List<Patient> patients = new LinkedList<>();
        try {
            Query q = this.qm.createQuery(
                    "select doc.name " + "from Document doc, " + "doc.object(PhenoTips.PatientClass) as patient "
                            + "where patient.identifier is not null order by patient.identifier desc",
                    Query.XWQL);
            potentialPatientIds = q.execute();
        } catch (QueryException e) {
            this.logger.error("Error retrieving a list of patients for matching: {}", e);
            return null;
        }

        for (String patientId : potentialPatientIds) {
            Patient patient = this.patientRepository.getPatientById(patientId);
            Visibility patientVisibility = this.permissionsManager.getPatientAccess(patient).getVisibility();

            if (this.consentManager.hasConsent(patient, REMOTE_MATCHING_CONSENT_ID)
                    && patientVisibility.compareTo(matchableVisibility) >= 0) {
                patients.add(patient);
            }
        }

        return patients;
    }
}
