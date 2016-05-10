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
import org.phenotips.matchingnotification.notification.PatientMatchEmail;
import org.phenotips.matchingnotification.notification.PatientMatchNotificationResponse;
import org.phenotips.matchingnotification.notification.PatientMatchNotifier;
import org.phenotips.matchingnotification.storage.MatchStorageManager;

import org.xwiki.component.annotation.Component;
import org.xwiki.query.Query;
import org.xwiki.query.QueryException;
import org.xwiki.query.QueryManager;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
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
    private PatientMatchNotifier notifier;

    @Inject
    private MatchFinderManager matchFinderManager;

    @Inject
    private MatchStorageManager matchStorageManager;

    @Override
    public List<PatientMatch> findAndSaveMatches(double score)
    {
        List<PatientMatch> addedMatches = new LinkedList<>();

        List<Patient> patients = this.getPatientsList();
        for (Patient patient : patients) {

            this.logger.debug("Finding matches for patient {}.", patient.getId());

            List<PatientMatch> matchesForPatient = this.matchFinderManager.findMatches(patient);
            this.filterMatchesByScore(matchesForPatient, score);
            this.filterExistingMatches(matchesForPatient);
            this.matchStorageManager.saveMatches(matchesForPatient);

            addedMatches.addAll(matchesForPatient);
        }

        return addedMatches;
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

    @Override
    public List<PatientMatchNotificationResponse> sendNotifications(List<Long> matchesIds)
    {
        if (matchesIds == null || matchesIds.size() == 0) {
            return Collections.emptyList();
        }

        List<PatientMatch> matches = matchStorageManager.loadMatchesByIds(matchesIds);
        List<PatientMatchEmail> emails = notifier.createEmails(matches);
        List<PatientMatchNotificationResponse> responses = new LinkedList<>();

        for (PatientMatchEmail email : emails) {
            List<PatientMatchNotificationResponse> notificationResults = notifier.notify(email);
            this.markSuccessfulNotification(notificationResults);

            responses.addAll(notificationResults);
        }
        return responses;
    }

    private void markSuccessfulNotification(List<PatientMatchNotificationResponse> notificationResults)
    {
        List<PatientMatch> successfulMatches = new LinkedList<>();
        for (PatientMatchNotificationResponse response : notificationResults) {
            if (response.isSuccessul()) {
                PatientMatch match = response.getPatientMatch();
                successfulMatches.add(match);
            }
        }

        this.matchStorageManager.markNotified(successfulMatches);
    }

    /*
     * Removes all matches from list with score lower than score.
     */
    private void filterMatchesByScore(List<PatientMatch> matches, double score)
    {
        List<PatientMatch> toRemove = new LinkedList<PatientMatch>();
        for (PatientMatch match : matches) {
            if (match.getScore() < score) {
                toRemove.add(match);
            }
        }
        matches.removeAll(toRemove);
    }

    /*
     * Gets a list of matches and removes matches that already exist in the database. When this is more mature, it might
     * belong in a separate component.
     */
    private void filterExistingMatches(List<PatientMatch> matches) {
        Map<String, List<PatientMatch>> matchesByPatientId = new HashMap<>();
        List<PatientMatch> toRemove = new LinkedList<>();
        for (PatientMatch match : matches) {

            // Read existing matches for same patient id, from db or cache
            String patientId = match.getPatientId();
            List<PatientMatch> matchesForPatient = matchesByPatientId.get(patientId);
            if (matchesForPatient == null) {
                matchesForPatient = this.matchStorageManager.loadMatchesByReferencePatientId(patientId);
                matchesByPatientId.put(patientId, matchesForPatient);
            }

            // Filter out existing matches from list parameter
            for (PatientMatch existingMatch : matchesForPatient) {
                if (this.matchesAreEqual(match, existingMatch)) {
                    toRemove.add(match);
                }
            }

        }
        matches.removeAll(toRemove);
    }

    /*
     * Compares two matches and returns true if they have the same unique key, meaning they represent the same match
     * between a local patient and another patient, that is either local or remote. The two matches can still differ in
     * some details, like score, patient's owner email.
     */
    private boolean matchesAreEqual(PatientMatch newMatch, PatientMatch existingMatch)
    {
        if (StringUtils.equals(existingMatch.getMatchedPatientId(), newMatch.getMatchedPatientId())
            && StringUtils.equals(existingMatch.getRemoteId(), newMatch.getRemoteId())
            && existingMatch.isIncoming() == newMatch.isIncoming()) {

            this.logger.debug("Match already exists in table: ", newMatch.toString());

            if (newMatch.getScore() != existingMatch.getScore()) {
                this.logger.debug(
                    "Match differs from existing match in score. New match: {}, existing match: {}.",
                    newMatch.getScore(), existingMatch.getScore());
            }

            if (!StringUtils.equals(newMatch.getOwnerEmail(), existingMatch.getOwnerEmail())) {
                this.logger.debug(
                    "Match differs from existing match in owner email. New match: {}, existing match: {}.",
                    newMatch.getOwnerEmail(), existingMatch.getOwnerEmail());
            }

            return true;
        } else {
            return false;
        }
    }
}
