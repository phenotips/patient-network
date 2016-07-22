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

import org.phenotips.data.Patient;
import org.phenotips.data.PatientRepository;
import org.phenotips.data.permissions.PermissionsManager;
import org.phenotips.data.permissions.Visibility;
import org.phenotips.data.similarity.PatientSimilarityView;
import org.phenotips.matchingnotification.MatchingNotificationManager;
import org.phenotips.matchingnotification.finder.MatchFinderManager;
import org.phenotips.matchingnotification.match.PatientMatch;
import org.phenotips.matchingnotification.match.internal.DefaultPatientMatch;
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

import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Joiner;

/**
 * @version $Id$
 */
@Component
@Singleton
public class DefaultMatchingNotificationManager implements MatchingNotificationManager
{
    private Logger logger = LoggerFactory.getLogger(DefaultMatchingNotificationManager.class);

    @Inject
    private QueryManager qm;

    @Inject
    private PatientRepository patientRepository;

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
     * Returns a list of patients with visibility>=matchable
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

            if (patientVisibility.compareTo(matchableVisibility) >= 0) {
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

            Session session = this.matchStorageManager.beginNotificationMarkingTransaction();
            List<PatientMatchNotificationResponse> notificationResults = notifier.notify(email);
            this.markSuccessfulNotification(session, notificationResults);
            boolean successful = this.matchStorageManager.endNotificationMarkingTransaction(session);

            if (!successful) {
                this.logger.error("Error on committing transaction for matching notification for patient {}.",
                    email.getSubjectPatientId());
            }

            responses.addAll(notificationResults);
        }
        return responses;
    }

    private void markSuccessfulNotification(Session session, List<PatientMatchNotificationResponse> notificationResults)
    {
        List<PatientMatch> successfulMatches = new LinkedList<>();
        for (PatientMatchNotificationResponse response : notificationResults) {
            PatientMatch match = response.getPatientMatch();
            if (response.isSuccessul()) {
                successfulMatches.add(match);
            } else {
                this.logger.error("Error on sending email for match {}.", match);
            }
        }

        this.matchStorageManager.markNotified(session, successfulMatches);
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
    private void filterExistingMatches(List<PatientMatch> matches)
    {
        Map<String, List<PatientMatch>> matchesByPatientId = new HashMap<>();
        List<PatientMatch> toRemove = new LinkedList<>();
        for (PatientMatch match : matches) {

            // Read existing matches for same patient id, from db or cache
            String patientId = match.getReferencePatientId();
            List<PatientMatch> matchesForPatient = matchesByPatientId.get(patientId);
            if (matchesForPatient == null) {
                // TODO use loadMatchesByIds instead.
                matchesForPatient = this.matchStorageManager.loadMatchesByReferencePatientId(patientId);
                matchesByPatientId.put(patientId, matchesForPatient);
            }

            // Filter out existing matches from list parameter
            if (matchesForPatient.contains(match)) {
                toRemove.add(match);
            }
        }
        matches.removeAll(toRemove);
    }

    @Override
    public boolean saveIncomingMatches(List<PatientSimilarityView> similarityViews, String remoteId)
    {
        List<PatientMatch> matches = new LinkedList<>();
        for (PatientSimilarityView view : similarityViews) {
            PatientMatch match = new DefaultPatientMatch(view, null, remoteId);
            matches.add(match);
        }

        this.filterExistingMatches(matches);
        this.matchStorageManager.saveMatches(matches);

        return true;
    }

    @Override
    public boolean markRejected(List<Long> matchesIds, boolean rejected)
    {
        boolean successful = false;
        try {
            List<PatientMatch> matches = matchStorageManager.loadMatchesByIds(matchesIds);

            Session session = this.matchStorageManager.beginNotificationMarkingTransaction();
            this.matchStorageManager.markRejected(session, matches, rejected);
            successful = this.matchStorageManager.endNotificationMarkingTransaction(session);
        } catch (HibernateException e) {
            this.logger.error("Error while marking matches {} as rejected.", Joiner.on(",").join(matchesIds), e);
        }
        return successful;
    }
}
