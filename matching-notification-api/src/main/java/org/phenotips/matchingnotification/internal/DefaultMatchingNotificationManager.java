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

import org.phenotips.data.permissions.AccessLevel;
import org.phenotips.data.permissions.Visibility;
import org.phenotips.data.similarity.PatientSimilarityView;
import org.phenotips.matchingnotification.MatchingNotificationManager;
import org.phenotips.matchingnotification.match.PatientMatch;
import org.phenotips.matchingnotification.notification.PatientMatchEmail;
import org.phenotips.matchingnotification.notification.PatientMatchNotificationResponse;
import org.phenotips.matchingnotification.notification.PatientMatchNotifier;
import org.phenotips.matchingnotification.notification.internal.AbstractPatientMatchEmail;
import org.phenotips.matchingnotification.storage.MatchStorageManager;

import org.xwiki.component.annotation.Component;

import java.security.AccessControlException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Joiner;

/**
 * @version $Id$
 */
@Component
@Singleton
@SuppressWarnings("checkstyle:ClassFanOutComplexity")
public class DefaultMatchingNotificationManager implements MatchingNotificationManager
{
    private Logger logger = LoggerFactory.getLogger(DefaultMatchingNotificationManager.class);

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
    private MatchStorageManager matchStorageManager;

    @Override
    public List<PatientMatchNotificationResponse> sendAdminNotificationsToLocalUsers(Map<Long, List<String>> matchesIds)
    {
        if (matchesIds == null || matchesIds.size() == 0) {
            return Collections.emptyList();
        }

        List<PatientMatch> matches =
            this.matchStorageManager.loadMatchesByIds(matchesIds.keySet());

        List<PatientMatchEmail> emails = this.notifier.createAdminEmailsToLocalUsers(matches, matchesIds);
        List<PatientMatchNotificationResponse> responses = new LinkedList<>();

        for (PatientMatchEmail email : emails) {

            List<PatientMatchNotificationResponse> notificationResults = this.notifier.notify(email);

            List<PatientMatch> successfulMatches = this.getSuccessfulNotifications(notificationResults);

            if (!this.matchStorageManager.setNotifiedStatus(successfulMatches, true)) {
                this.logger.error("Error marking matches as notified for patient {}.", email.getSubjectPatientId());
            }

            this.saveNotificationHistory(successfulMatches, email, "notification");

            responses.addAll(notificationResults);
        }
        return responses;
    }

    @Override
    public PatientMatchNotificationResponse sendUserNotification(Long matchId,
        String subjectPatientId, String subjectServerId, String customEmailtext, String customEmailSubject)
    {
        List<PatientMatch> matches =
            this.matchStorageManager.loadMatchesByIds(Collections.singleton(matchId));

        if (matches.size() == 0) {
            throw new IllegalArgumentException("MatchId " + matchId + " is not a valid match id");
        }

        PatientMatch match = matches.get(0);

        if (!this.currentUserHasViewAccess(match)) {
            throw new AccessControlException("Current user has no rights to notify MatchId " + matchId);
        }

        PatientMatchEmail email = this.notifier.createUserEmail(match,
            subjectPatientId, subjectServerId, customEmailtext, customEmailSubject);

        List<PatientMatchNotificationResponse> notificationResults = this.notifier.notify(email);

        if (notificationResults.size() == 0) {
            this.logger.error("No notification result when sending user email");
            return null;
        }

        List<PatientMatch> successfulMatches = this.getSuccessfulNotifications(notificationResults);

        if (!this.matchStorageManager.setNotifiedStatus(successfulMatches, true)) {
            this.logger.error("Error marking matches as notified for patient {}.", email.getSubjectPatientId());
        }

        this.saveNotificationHistory(successfulMatches, email, "contact");

        return notificationResults.get(0);
    }

    private void saveNotificationHistory(List<PatientMatch> matches, PatientMatchEmail email, String type)
    {
        JSONObject json = new JSONObject();
        json.put("type", type);

        JSONObject mimeJSON = email.getEmail().getJSONObject(AbstractPatientMatchEmail.EMAIL_RECIPIENTS_KEY);

        JSONObject from = new JSONObject();
        from.put("userinfo", mimeJSON.optJSONObject(AbstractPatientMatchEmail.EMAIL_RECIPIENTS_SENDER));
        from.put("emails", mimeJSON.optJSONArray(AbstractPatientMatchEmail.EMAIL_RECIPIENTS_FROM));

        JSONObject to = new JSONObject();
        to.put("userinfo", mimeJSON.optJSONObject(AbstractPatientMatchEmail.EMAIL_RECIPIENTS_RECIPIENT));
        to.put("emails", mimeJSON.optJSONArray(AbstractPatientMatchEmail.EMAIL_RECIPIENTS_TO));

        json.put("from", from);
        json.put("to", to);
        json.put("cc", mimeJSON.optJSONArray(AbstractPatientMatchEmail.EMAIL_RECIPIENTS_CC));
        json.put("subjectPatientId", mimeJSON.optString(AbstractPatientMatchEmail.EMAIL_SUBJECT_PATIENT_ID));

        for (PatientMatch notifiedMatch : matches) {
            Timestamp date = notifiedMatch.getNotifiedTimestamp();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd");
            json.put("date", date == null ? sdf.format(new Timestamp(System.currentTimeMillis())) : sdf.format(date));

            if (!this.matchStorageManager.updateNotificationHistory(notifiedMatch, json.toString())) {
                this.logger.error("Error saving {} history for match {}", type, notifiedMatch.getId());
            }
        }
    }

    private List<PatientMatch> getSuccessfulNotifications(List<PatientMatchNotificationResponse> notificationResults)
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
        return successfulMatches;
    }

    @Override
    public JSONObject getUserEmailContent(Long matchId, String subjectPatientId, String subjectServerId)
    {
        List<PatientMatch> matches =
            this.matchStorageManager.loadMatchesByIds(Collections.singleton(matchId));

        PatientMatchEmail email = this.notifier.createUserEmail(matches.get(0),
            subjectPatientId, subjectServerId, null, null);

        return email.getEmail();
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
    public boolean setStatus(Set<Long> matchesIds, String status)
    {
        boolean successful = false;
        try {
            List<PatientMatch> matches = this.matchStorageManager.loadMatchesByIds(matchesIds);

            filterNonUsersMatches(matches);

            successful = this.matchStorageManager.setStatus(matches, status);
        } catch (Exception e) {
            this.logger.error("Error while marking matches {} as {}", Joiner.on(",").join(matchesIds), status, e);
        }
        return successful;
    }

    @Override
    public boolean setNotifiedStatus(Set<Long> matchesIds, boolean isNotified)
    {
        boolean successful = false;
        try {
            List<PatientMatch> matches = this.matchStorageManager.loadMatchesByIds(matchesIds);

            filterNonUsersMatches(matches);

            successful = this.matchStorageManager.setNotifiedStatus(matches, isNotified);
        } catch (Exception e) {
            String status = isNotified ? "notified" : "unnotified";
            this.logger.error("Error while marking matches {} as {}",
                Joiner.on(",").join(matchesIds), status, e);
        }
        return successful;
    }

    @Override
    public boolean setComment(Set<Long> matchesIds, String comment)
    {
        boolean successful = false;
        try {
            List<PatientMatch> matches = this.matchStorageManager.loadMatchesByIds(matchesIds);

            filterNonUsersMatches(matches);

            successful = this.matchStorageManager.setComment(matches, comment);
        } catch (Exception e) {
            this.logger.error("Error while setting comment for matches {} as {}",
                Joiner.on(",").join(matchesIds), comment, e);
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
            if (!currentUserHasViewAccess(iterator.next())) {
                iterator.remove();
            }
        }
    }

    private boolean currentUserHasViewAccess(PatientMatch match)
    {
        return this.viewAccess.compareTo(match.getReference().getAccess()) <= 0
            || this.viewAccess.compareTo(match.getMatched().getAccess()) <= 0;
    }
}
