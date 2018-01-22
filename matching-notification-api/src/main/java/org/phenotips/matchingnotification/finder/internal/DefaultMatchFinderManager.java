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
import org.phenotips.data.PatientRepository;
import org.phenotips.data.permissions.PermissionsManager;
import org.phenotips.data.permissions.Visibility;
import org.phenotips.matchingnotification.finder.MatchFinder;
import org.phenotips.matchingnotification.finder.MatchFinderManager;
import org.phenotips.matchingnotification.match.PatientMatch;

import org.xwiki.component.annotation.Component;
import org.xwiki.query.Query;
import org.xwiki.query.QueryManager;

import java.util.LinkedList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @version $Id$
 */
@Component
@Singleton
public class DefaultMatchFinderManager implements MatchFinderManager
{
    private Logger logger = LoggerFactory.getLogger(DefaultMatchFinderManager.class);

    @Inject
    private Provider<List<MatchFinder>> matchFinderProvider;

    @Inject
    @Named("matchable")
    private Visibility matchableVisibility;

    @Inject
    private QueryManager qm;

    @Inject
    private PatientRepository patientRepository;

    @Inject
    private PermissionsManager permissionsManager;


    @Override
    public void findMatchesForAllPatients()
    {
        this.recordFindAllStart();

        List<Patient> patients = this.getPatientsList();

        for (Patient patient : patients) {
            this.logger.debug("Finding matches for patient {}.", patient.getId());
            this.findMatches(patient);
        }

        this.recordFindAllEnd();
    }


    @Override
    public List<PatientMatch> findMatches(Patient patient)
    {
        List<PatientMatch> matches = new LinkedList<>();

        for (MatchFinder service : this.matchFinderProvider.get()) {

            this.logger.error("Using service {}", service.getClass().getSimpleName());

            try {
                List<PatientMatch> foundMatches = service.findMatches(patient);
                matches.addAll(foundMatches);

                this.logger.error("Found {} matches by {}", foundMatches.size(), service.getClass().getSimpleName());

                for (PatientMatch match : foundMatches) {
                    this.logger.debug(match.toString());
                }

            } catch (Exception ex) {
                this.logger.error("Failed to invoke matches finder [{}]",
                    service.getClass().getCanonicalName(), ex);
            }
        }

        return matches;
    }

    private void recordFindAllStart()
    {
        for (MatchFinder service : this.matchFinderProvider.get()) {
            service.recordStartMatchesSearch();
        }
    }

    private void recordFindAllEnd()
    {
        for (MatchFinder service : this.matchFinderProvider.get()) {
            service.recordEndMatchesSearch();
        }
    }

    /**
     * Returns a list of patients with visibility >= matchable.
     */
    private List<Patient> getPatientsList()
    {
        List<Patient> patients = new LinkedList<>();

        try {
            Query q = this.qm.createQuery(
                "select doc.name "
                    + "from Document doc, "
                    + "doc.object(PhenoTips.PatientClass) as patient "
                    + "where patient.identifier is not null order by patient.identifier desc",
                Query.XWQL);
            List<String> potentialPatientIds = q.execute();

            for (String patientId : potentialPatientIds) {
                Patient patient = this.patientRepository.get(patientId);
                if (patient == null) {
                    continue;
                }

                Visibility patientVisibility = this.permissionsManager.getPatientAccess(patient).getVisibility();
                if (patientVisibility.compareTo(this.matchableVisibility) >= 0) {
                    patients.add(patient);
                }
            }
        } catch (Exception e) {
            this.logger.error("Error retrieving a list of patients for matching: {}", e);
        }

        return patients;
    }
}
