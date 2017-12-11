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
import org.phenotips.data.permissions.AccessLevel;
import org.phenotips.data.permissions.PermissionsManager;
import org.phenotips.data.permissions.Visibility;
import org.phenotips.data.similarity.PatientSimilarityView;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
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

    /** Needed for checking if a given access level provides read access to patients. */
    @Inject
    @Named("view")
    private AccessLevel viewAccess;

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
            if (matchesForPatient.isEmpty()) {
                continue;
            }

            this.filterMatchesByScore(matchesForPatient, score);
            if (matchesForPatient.isEmpty()) {
                continue;
            }

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
                "select doc.name "
                    + "from Document doc, "
                    + "doc.object(PhenoTips.PatientClass) as patient "
                    + "where patient.identifier is not null order by patient.identifier desc",
                Query.XWQL);
            potentialPatientIds = q.execute();
        } catch (QueryException e) {
            this.logger.error("Error retrieving a list of patients for matching: {}", e);
            return null;
        }

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

        return patients;
    }

    @Override
    public List<PatientMatchNotificationResponse> sendNotifications(Map<Long, String> matchesIds)
    {
        if (matchesIds == null || matchesIds.size() == 0) {
            return Collections.emptyList();
        }

        List<PatientMatch> matches =
            this.matchStorageManager.loadMatchesByIds(new ArrayList<Long>(matchesIds.keySet()));

        filterNonUsersMatches(matches);

        List<PatientMatchEmail> emails = this.notifier.createEmails(matches, matchesIds);
        List<PatientMatchNotificationResponse> responses = new LinkedList<>();

        for (PatientMatchEmail email : emails) {

            Session session = this.matchStorageManager.beginNotificationMarkingTransaction();
            List<PatientMatchNotificationResponse> notificationResults = this.notifier.notify(email);
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
                this.logger.error("Error on sending email for match {}: {}.", match, response.getErrorMessage());
            }
        }

        this.matchStorageManager.markNotified(session, successfulMatches);
    }

    /*
     * Removes all matches from list with score lower than score.
     */
    private void filterMatchesByScore(List<PatientMatch> matches, double score)
    {
        List<PatientMatch> toRemove = new LinkedList<>();
        for (PatientMatch match : matches) {
            if (match.getScore() < score) {
                toRemove.add(match);
            }
        }
        matches.removeAll(toRemove);
    }

    @Override
    public boolean saveLocalMatchesViews(List<PatientSimilarityView> similarityViews, String patientId)
    {
        return this.matchStorageManager.saveLocalMatchesViews(similarityViews, patientId);
    }

    @Override
    public boolean saveIncomingMatches(List<? extends PatientSimilarityView> similarityViews, String patientId,
        String remoteId)
    {
        return this.matchStorageManager.saveRemoteMatches(similarityViews, patientId, remoteId, true);
    }

    @Override
    public boolean saveOutgoingMatches(List<? extends PatientSimilarityView> similarityViews, String patientId,
        String remoteId)
    {
        return this.matchStorageManager.saveRemoteMatches(similarityViews, patientId, remoteId, false);
    }

    @Override
    public boolean setStatus(List<Long> matchesIds, String status)
    {
        boolean successful = false;
        try {
            List<PatientMatch> matches = this.matchStorageManager.loadMatchesByIds(matchesIds);

            filterNonUsersMatches(matches);

            Session session = this.matchStorageManager.beginNotificationMarkingTransaction();
            this.matchStorageManager.setStatus(session, matches, status);
            successful = this.matchStorageManager.endNotificationMarkingTransaction(session);
        } catch (HibernateException e) {
            this.logger.error("Error while marking matches {} as {}", Joiner.on(",").join(matchesIds), status, e);
        }
        return successful;
    }

    /**
     * Filters out matches that user does not own or has access to.
     */
    private void filterNonUsersMatches(List<PatientMatch> matches)
    {
        ListIterator<PatientMatch> iterator = matches.listIterator();
        while (iterator.hasNext()) {
            PatientMatch match = iterator.next();
            boolean hasViewAccess = this.viewAccess.compareTo(match.getReference().getAccess()) <= 0
                && this.viewAccess.compareTo(match.getMatched().getAccess()) <= 0;
            if (!hasViewAccess) {
                iterator.remove();
            }
        }
    }
}
