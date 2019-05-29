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
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

            PatientMatchNotificationResponse notificationResult = this.notifier.notify(email);

            if (notificationResult.isSuccessul()) {
                Collection<PatientMatch> updatedMatches =
                    this.updateNotificationHistory(notificationResult, email, "notification");
                notificationResult.setPatientMatches(updatedMatches);
            }

            responses.add(notificationResult);
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
            this.logger.error("No matches found for match id " + matchId);
            return null;
        }

        PatientMatch match = matches.get(0);

        if (!this.currentUserHasViewAccess(match)) {
            throw new AccessControlException("Current user has no rights to notify match id " + matchId);
        }

        PatientMatchEmail email = this.notifier.createUserEmail(match,
            subjectPatientId, subjectServerId, customEmailtext, customEmailSubject);

        PatientMatchNotificationResponse notificationResult = this.notifier.notify(email);

        if (notificationResult == null) {
            this.logger.error("No notification result when sending user email");
            return null;
        }

        if (notificationResult.isSuccessul()) {
            this.updateNotificationHistory(notificationResult, email, "contact");
        }

        return notificationResult;
    }

    private Collection<PatientMatch> updateNotificationHistory(PatientMatchNotificationResponse notificationResult,
        PatientMatchEmail email, String type)
    {
        Collection<PatientMatch> successfulMatches = notificationResult.getPatientMatches();

        JSONObject json = getNotificationHistoryJSON(email, type);

        Collection<PatientMatch> updatedMatches = new LinkedList<>();
        for (PatientMatch match : successfulMatches) {
            updatedMatches.addAll(this.matchStorageManager.updateNotificationHistory(match, json));
        }
        return updatedMatches;
    }

    private JSONObject getNotificationHistoryJSON(PatientMatchEmail email, String type)
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

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd hh:mm");
        json.put("date", sdf.format(new Timestamp(System.currentTimeMillis())));

        return json;
    }

    @Override
    public JSONObject getUserEmailContent(Long matchId, String subjectPatientId, String subjectServerId)
    {
        List<PatientMatch> matches =
            this.matchStorageManager.loadMatchesByIds(Collections.singleton(matchId));

        if (matches.size() == 0) {
            this.logger.error("No matches found for match id " + matchId);
            return null;
        }

        PatientMatch match = matches.get(0);

        if (!this.currentUserHasViewAccess(match)) {
            throw new AccessControlException("Current user has no access to match id " + matchId);
        }

        PatientMatchEmail email = this.notifier.createUserEmail(match,
            subjectPatientId, subjectServerId, null, null);

        return email.getEmail();
    }

    @Override
    public boolean saveIncomingMatches(List<? extends PatientSimilarityView> similarityViews, String patientId,
        String remoteId)
    {
        return this.matchStorageManager.saveRemoteMatches(similarityViews, patientId, remoteId, true) != null;
    }

    @Override
    public boolean saveOutgoingMatches(List<? extends PatientSimilarityView> similarityViews, String patientId,
        String remoteId)
    {
        return this.matchStorageManager.saveRemoteMatches(similarityViews, patientId, remoteId, false) != null;
    }

    @Override
    public PatientMatch setStatus(Long matchId, String status) throws AccessControlException
    {
        PatientMatch match = this.getMatch(matchId);
        if (match != null && this.matchStorageManager.setStatus(match, status)) {
            return match;
        }
        return null;
    }

    @Override
    public PatientMatch setUserContacted(Long matchId, boolean isUserContacted) throws AccessControlException
    {
        PatientMatch match = this.getMatch(matchId);
        if (match != null && this.matchStorageManager.setUserContacted(match, isUserContacted)) {
            return match;
        }
        return null;
    }

    @Override
    public PatientMatch saveComment(Long matchId, String comment) throws AccessControlException
    {
        PatientMatch match = this.getMatch(matchId);
        if (match != null && this.matchStorageManager.saveComment(match, comment)) {
            return match;
        }
        return null;
    }

    @Override
    public PatientMatch addNote(Long matchId, String note) throws AccessControlException
    {
        PatientMatch match = this.getMatch(matchId);
        if (match != null && this.matchStorageManager.addNote(match, note)) {
            return match;
        }
        return null;
    }

    @Override
    public PatientMatch getMatch(Long matchId)
    {
        List<PatientMatch> matches = this.matchStorageManager.loadMatchesByIds(Collections.singleton(matchId));
        if (matches.size() == 0) {
            this.logger.error("No matches found for match id " + matchId);
            return null;
        }
        PatientMatch match = matches.get(0);
        if (!currentUserHasViewAccess(match)) {
            this.logger.error("Current user has no rights to modify match with id " + matchId);
            throw new AccessControlException("Current user has no rights to modify match with id " + matchId);
        }
        return match;
    }

    private boolean currentUserHasViewAccess(PatientMatch match)
    {
        return this.viewAccess.compareTo(match.getReference().getAccess()) <= 0
            || this.viewAccess.compareTo(match.getMatched().getAccess()) <= 0;
    }
}
