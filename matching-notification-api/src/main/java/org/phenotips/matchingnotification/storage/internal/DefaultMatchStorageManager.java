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
package org.phenotips.matchingnotification.storage.internal;

import org.phenotips.data.similarity.PatientSimilarityView;
import org.phenotips.groups.Group;
import org.phenotips.groups.GroupManager;
import org.phenotips.matchingnotification.match.PatientMatch;
import org.phenotips.matchingnotification.match.internal.DefaultPatientMatch;
import org.phenotips.matchingnotification.storage.MatchStorageManager;

import org.xwiki.component.annotation.Component;
import org.xwiki.query.QueryManager;
import org.xwiki.users.User;
import org.xwiki.users.UserManager;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.Criteria;
import org.hibernate.HibernateException;
import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.criterion.Criterion;
import org.hibernate.criterion.Restrictions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.xpn.xwiki.store.hibernate.HibernateSessionFactory;

/**
 * @version $Id$
 */
@Component
@Singleton
public class DefaultMatchStorageManager implements MatchStorageManager
{
    /** A query used to delete all local matches for the given local patient (ID == localId). */
    private static final String HQL_DELETE_LOCAL_MATCHES_FOR_LOCAL_PATIENT =
        "delete DefaultPatientMatch where referenceServerId = '' and matchedServerId =''"
            + " and (referencePatientId = :localId or matchedPatientId = :localId)";

    /** A query used to delete all matches (including MME) for the given local patient (ID == localId). */
    private static final String HQL_DELETE_ALL_MATCHES_FOR_LOCAL_PATIENT =
        "delete DefaultPatientMatch where (referenceServerId = '' and referencePatientId = :localId)"
            + " or (matchedServerId ='' and matchedPatientId = :localId)";

    /** A query used to delete all outgoing MME matches for the given local patient (ID == localId)
     *  and givenremote server (ID == remoteServerId). */
    private static final String HQL_DELETE_ALL_OUTGOING_MATCHES_FOR_LOCAL_PATIENT =
        "delete DefaultPatientMatch where notified = false and status != 'rejected'"
            + " and referenceServerId = '' and referencePatientId = :localId"
            + " and matchedServerId = :remoteServerId";

    /** A query used to delete all incoming MME matches for the given remote patient (ID == remoteId)
     *  and givenremote server (ID == remoteServerId). */
    private static final String HQL_DELETE_ALL_INCOMING_MATCHES_FOR_REMOTE_PATIENT =
        "delete DefaultPatientMatch where notified = false and status != 'rejected'"
            + " and referenceServerId = :remoteServerId and referencePatientId = :remoteId";

    /**
     *  A sub-query for the query below, to find matches similar to the given match, but that has been notified.
     *  A special case is when there is a similar local match, but where matched and reference patients are reversed.
     */
    private static final String HQL_QUERY_SAME_PATIENT_BUT_NOTIFIED =
        "from DefaultPatientMatch as m2 where m2.notified = true"
            + " and ("
            + " ("
            + "  m.referencePatientId = m2.referencePatientId"
            + "  and m.referenceServerId = m2.referenceServerId"
            + "  and m.matchedPatientId = m2.matchedPatientId"
            + "  and m.matchedServerId = m2.matchedServerId"
            + " ) or ("
            + "  m.referencePatientId = m2.matchedPatientId"
            + "  and m.matchedPatientId = m2.referencePatientId"
            + "  and m.matchedServerId = m2.referenceServerId"
            + "  and m.referenceServerId = m2.matchedServerId)"
            + ")";

    /**
     * A query to find all matches with a score greater than given (score == minScore).
     *
     * One complication is that for notified matches there may be both a notified and an un-notified versions
     * of the match, so need to show only one of them (the notified one).
     * */
    private static final String HQL_QUERY_FIND_ALL_MATCHES_BY_SCORE =
        "from DefaultPatientMatch as m where"
            + " score >= :minScore and phenotypeScore >= :phenScore and genotypeScore >= :genScore and"
            + " (m.notified = true or (not exists (" + HQL_QUERY_SAME_PATIENT_BUT_NOTIFIED + ")))";

    /** A query used to get the number of all remote matches. */
    private static final String HQL_QUERY_REMOTE_MATCHES =
        "select count(*) from DefaultPatientMatch as m where m.referenceServerId != '' or m.matchedServerId !=''";

    /** Handles persistence. */
    @Inject
    private HibernateSessionFactory sessionFactory;

    /** Runs queries for finding patients for filtering. */
    @Inject
    private QueryManager qm;

    @Inject
    private UserManager users;

    @Inject
    private GroupManager groupManager;

    /** Logging helper object. */
    private Logger logger = LoggerFactory.getLogger(DefaultMatchStorageManager.class);

    @Override
    public List<PatientMatch> getMatchesToBePlacedIntoNotificationTable(List<PatientSimilarityView> inputMatches)
    {
        List<PatientMatch> matches = new LinkedList<>();
        for (PatientSimilarityView similarityView : inputMatches) {
            PatientMatch match = new DefaultPatientMatch(similarityView, null, null);

            // filter out matches owned by the same user(s), as those are not shown in matching notification anyway
            // and they break match count calculation if they are included
            if (match.getReference().getEmails().size() > 0 && CollectionUtils.isEqualCollection(
                match.getReference().getEmails(), match.getMatched().getEmails())) {
                continue;
            }

            matches.add(match);
        }
        return matches;
    }

    @Override
    public boolean saveLocalMatches(List<PatientMatch> matches, String patientId)
    {
        Session session = this.beginTransaction();
        boolean transactionCompleted = false;

        try {
            Set<String> refPatients = new HashSet<>();
            refPatients.add(patientId);

            for (PatientMatch match : matches) {
                String refPatientID = match.getReferencePatientId();
                if (!patientId.equals(refPatientID)) {
                    refPatients.add(refPatientID);
                    this.logger.error("A list of matches for local patient {} also constains matches for patient {}",
                        patientId, refPatientID);
                }

                // if the same un-notified match already exists, we preserve the original comment and match found date
                List<PatientMatch> sameExistingMatches = this.loadMatchesBetweenPatients(match.getReferencePatientId(),
                    match.getReferenceServerId(), match.getMatchedPatientId(), match.getMatchedServerId());
                for (PatientMatch existingMatch : sameExistingMatches) {
                    if (!existingMatch.isNotified()) {
                        match.setFoundTimestamp(existingMatch.getFoundTimestamp());
                        match.setComment(existingMatch.getComment());
                    }
                }
            }

            for (String ptId : refPatients) {
                this.deleteLocalMatchesForLocalPatient(session, ptId, false, false);
            }
            this.saveMatches(session, matches);
            transactionCompleted = true;
        } catch (Exception ex) {
            this.logger.error("Error saving local matches: [{}]", ex.getMessage(), ex);
        } finally {
            transactionCompleted = this.endTransaction(session, transactionCompleted) && transactionCompleted;
        }
        return transactionCompleted;
    }

    @Override
    public boolean saveRemoteMatches(List<? extends PatientSimilarityView> similarityViews, String patientId,
        String serverId, boolean isIncoming)
    {
        Session session = this.beginTransaction();
        boolean transactionCompleted = false;

        try {
            Set<String> refPatients = new HashSet<>();
            refPatients.add(patientId);

            List<PatientMatch> matchesToSave = new LinkedList<>();
            for (PatientSimilarityView similarityView : similarityViews) {
                String refPatientID = similarityView.getReference().getId();
                if (!patientId.equals(refPatientID)) {
                    refPatients.add(refPatientID);
                    if (isIncoming) {
                        this.logger.error(
                            "A list of incoming matches for remote patient {} also constains matches for patient {}",
                            patientId, refPatientID);
                    } else {
                        this.logger.error(
                            "A list of outgoing matches for local patient {} also constains matches for patient {}",
                            patientId, refPatientID);
                    }
                }
                PatientMatch match = isIncoming ? new DefaultPatientMatch(similarityView, serverId, "")
                    : new DefaultPatientMatch(similarityView, "", serverId);
                matchesToSave.add(match);
            }

            for (String ptId : refPatients) {
                // delete all un-notified un-rejected matches of the same kind for the given reference patient
                if (isIncoming) {
                    this.deleteIncomingMatchesByPatientId(session, ptId, serverId);
                } else {
                    this.deleteOutgoingMatchesByPatientId(session, ptId, serverId);
                }
            }

            this.saveMatches(session, matchesToSave);
            transactionCompleted = true;

            this.logger.debug("Saved {} new MME matches (server: [{}], incoming: [{}])",
                matchesToSave.size(), serverId, isIncoming);
        } catch (Exception ex) {
            this.logger.error("Error saving remote matches: [{}]", ex.getMessage(), ex);
        } finally {
            transactionCompleted = this.endTransaction(session, transactionCompleted) && transactionCompleted;
        }
        return transactionCompleted;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<PatientMatch> loadMatches(double score, double phenScore, double genScore,
        boolean onlyCurrentUserAccessible)
    {
        // ...else it is more complicated: need to return all matches, but
        // also exclude un-notified matches that have a similar match that have been notified
        //
        // This is because if owners of a match were notified, the notified version of the match
        // will be stored as-is in the database, but new verisons of the match will still keep being
        // placed in the DB, resulting in two similar matche sin the DB:
        // first: patientA - patientB - notified - <data at the moment of notification>
        // second: patientA - patientB - NOT notified - <data as of last match check>

        Session session = this.sessionFactory.getSessionFactory().openSession();
        try {
            Query query = session.createQuery(HQL_QUERY_FIND_ALL_MATCHES_BY_SCORE);
            query.setParameter("minScore", score);
            query.setParameter("phenScore", phenScore);
            query.setParameter("genScore", genScore);

            List<PatientMatch> result = query.list();

            this.logger.debug("Retrieved [{}] matches with score > [{}], phenotypical score > [{}],"
                + " genotypical score > [{}]", result.size(), score, phenScore, genScore);

            if (onlyCurrentUserAccessible) {
                this.filterMatchesForCurrentUser(result);
            }

            return result;
        } catch (Exception ex) {
            this.logger.error("Load matches failed [{}]", ex.getMessage(), ex);
            return Collections.emptyList();
        } finally {
            session.close();
        }
    }

    private void filterMatchesForCurrentUser(List<PatientMatch> matches)
    {
        User currentUser = this.users.getCurrentUser();

        // a set of all entity names that share their access rights with the user
        Set<String> userEntities = new HashSet<>();
        // ...add user profile document to the set
        userEntities.add(currentUser.getProfileDocument().toString());
        // ...add all user groups to the set
        Set<Group> userGroups = this.groupManager.getGroupsForUser(currentUser);
        for (Group g : userGroups) {
            userEntities.add(g.getReference().toString());
        }

        try {
            //
            // for performance reasons, using various AccessManager-s is too slow when
            // checking permissions for potentially 100s of patients at once. So the query below
            // (adapted from LiveTableMacros.xml's "generateentitypermissions" macro)
            // retrieves names of all patient documents that current user has access to, either directly
            // or by being a collaborator, or a member of a group that is a collaborator.
            //
            // Since it is only used for filtering the list of matches, it does not need to do extra checks
            // (e.g. doc.name != PatientTemplate), since those patients will never be part of a match.
            // Similarly, no check for "matchable" or alike is done, since if a match is in the matching
            // notification table, it will be shown as long as the user has access to the patient.
            //
            org.xwiki.query.Query q = this.qm.createQuery(
                "select doc.name from Document as doc, "
                + "BaseObject as obj, BaseObject accessObj, StringProperty accessProp "
                + "where obj.name = doc.fullName and obj.className = 'PhenoTips.PatientClass' and "
                + "accessObj.name = doc.fullName and accessProp.id.id = accessObj.id and "
                + "((accessObj.className = 'PhenoTips.OwnerClass' and accessProp.value in (:userEntityList)) "
                + "or (accessObj.className = 'PhenoTips.CollaboratorClass' and accessProp.value in (:userEntityList)) "
                + "or (accessObj.className = 'PhenoTips.VisibilityClass' and accessProp.value in ('public', 'open')))",
                org.xwiki.query.Query.XWQL);
            q.bindValue("userEntityList", userEntities);
            List<String> rawDocNames = q.execute();

            Set<String> patientNames = new HashSet<>(rawDocNames);

            // FIXME: to be removed after testing is complete. In some cases it is easier to spot errors in this
            // list than to find incorrect behaviors of higher-level code which requires a specific match
            // to appear or disappear from the list
            this.logger.error("List of patients current user has access to: [{}]", String.join(", ", patientNames));

            ListIterator<PatientMatch> iterator = matches.listIterator();
            while (iterator.hasNext()) {
                PatientMatch match = iterator.next();
                if (match.getMatched().isLocal() && patientNames.contains(match.getMatched().getPatientId())) {
                    continue;
                }
                if (match.getReference().isLocal() && patientNames.contains(match.getReference().getPatientId())) {
                    continue;
                }
                // neither of the patients is a local patient that current user has access to => exclude
                iterator.remove();
            }
        } catch (Exception ex) {
            this.logger.error("Failed to query all patients that current user has access to [{}]: {}",
                ex.getMessage());
        }
    }

    @Override
    public List<PatientMatch> loadMatchesByIds(Set<Long> matchesIds)
    {
        if (matchesIds != null && matchesIds.size() > 0) {
            return this.loadMatchesByCriteria(
                new Criterion[] { Restrictions.in("id", matchesIds.toArray()) });
        } else {
            return Collections.emptyList();
        }
    }

    @Override
    public List<PatientMatch> loadMatchesBetweenPatients(String patientId1, String serverId1,
        String patientId2, String serverId2)
    {
        if (StringUtils.isNotEmpty(patientId1) && StringUtils.isNotEmpty(patientId2)) {
            return this.loadMatchesByCriteria(
                // either patientId1 is reference and patientId2 is a match, or the other way around
                new Criterion[] { Restrictions.or(
                    Restrictions.and(this.patientIsReference(patientId1, serverId1),
                        this.patientIsMatch(patientId2, serverId2)),
                    Restrictions.and(this.patientIsReference(patientId2, serverId2),
                        this.patientIsMatch(patientId1, serverId1))) });
        } else {
            return Collections.emptyList();
        }
    }

    private String getStoredServerId(String serverId)
    {
        if (serverId == null) {
            return "";
        }
        return serverId;
    }

    private Criterion patientIsReference(String patientId, String serverId)
    {
        return Restrictions.and(Restrictions.eq("referencePatientId", patientId),
            Restrictions.eq("referenceServerId", this.getStoredServerId(serverId)));
    }

    private Criterion patientIsMatch(String patientId, String serverId)
    {
        return Restrictions.and(Restrictions.eq("matchedPatientId", patientId),
            Restrictions.eq("matchedServerId", this.getStoredServerId(serverId)));
    }

    @SuppressWarnings("unchecked")
    private List<PatientMatch> loadMatchesByCriteria(Criterion[] criteriaToApply)
    {
        List<PatientMatch> matches = null;
        Session session = this.sessionFactory.getSessionFactory().openSession();
        try {
            Criteria criteria = session.createCriteria(PatientMatch.class);
            if (criteriaToApply != null) {
                for (Criterion c : criteriaToApply) {
                    criteria.add(c);
                }
            }
            matches = criteria.list();
        } catch (HibernateException ex) {
            this.logger.error("Error loading matches by criteria. Criteria: {},  ERROR: [{}]",
                Arrays.toString(criteriaToApply), ex);
        } finally {
            session.close();
        }
        return matches;
    }

    @Override
    public boolean setNotifiedStatus(List<PatientMatch> matches, boolean isNotified)
    {
        Session session = this.beginTransaction();
        boolean transactionCompleted = false;

        try {
            for (PatientMatch match : matches) {
                match.setNotified(isNotified);
                session.update(match);
            }
            transactionCompleted = true;
        } catch (Exception ex) {
            String status = isNotified ? "notified" : "unnotified";
            this.logger.error("Error marking matches as {}: [{}]", status, ex.getMessage(), ex);
        } finally {
            transactionCompleted = this.endTransaction(session, transactionCompleted) && transactionCompleted;
        }
        return transactionCompleted;
    }

    @Override
    public boolean setUserContacted(List<PatientMatch> matches, boolean isUserContacted)
    {
        Session session = this.beginTransaction();
        boolean transactionCompleted = false;

        try {
            for (PatientMatch match : matches) {
                match.setUserContacted(isUserContacted);
                session.update(match);
            }
            transactionCompleted = true;
        } catch (Exception ex) {
            String status = isUserContacted ? "Error while marking match as user-contacted"
                : "Error while marking match as not user-contacted";
            this.logger.error(status, ex.getMessage(), ex);
        } finally {
            transactionCompleted = this.endTransaction(session, transactionCompleted) && transactionCompleted;
        }
        return transactionCompleted;
    }

    @Override
    public boolean updateNotificationHistory(PatientMatch match, String notificationRecord)
    {
        Session session = this.beginTransaction();
        boolean transactionCompleted = false;

        try {
            match.updateNotificationHistory(notificationRecord);
            session.update(match);
            transactionCompleted = true;
        } catch (Exception ex) {
            this.logger.error("Error updating notification history: [{}]", ex.getMessage(), ex);
        } finally {
            transactionCompleted = this.endTransaction(session, transactionCompleted) && transactionCompleted;
        }
        return transactionCompleted;
    }

    @Override
    public boolean setStatus(List<PatientMatch> matches, String status)
    {
        Session session = this.beginTransaction();
        boolean transactionCompleted = false;

        try {
            for (PatientMatch match : matches) {
                match.setStatus(status);
                session.update(match);
            }
            transactionCompleted = true;
        } catch (Exception ex) {
            this.logger.error("Error saving matches statuses: [{}]", ex.getMessage(), ex);
        } finally {
            transactionCompleted = this.endTransaction(session, transactionCompleted) && transactionCompleted;
        }
        return transactionCompleted;
    }

    @Override
    public boolean setComment(List<PatientMatch> matches, String comment)
    {
        Session session = this.beginTransaction();
        boolean transactionCompleted = false;

        try {
            for (PatientMatch match : matches) {
                match.setComment(comment);
                session.update(match);
            }
            transactionCompleted = true;
        } catch (Exception ex) {
            this.logger.error("Error saving matches comments: [{}]", ex.getMessage(), ex);
        } finally {
            transactionCompleted = this.endTransaction(session, transactionCompleted) && transactionCompleted;
        }
        return transactionCompleted;
    }

    /**
     * Initializes a transaction. See also {@code endTransaction}.
     *
     * @return the session. Null if initialization failed.
     */
    private Session beginTransaction()
    {
        Session session = this.sessionFactory.getSessionFactory().openSession();
        session.beginTransaction();
        return session;
    }

    /**
     * Commits the transaction. See also {@code startTransaction} and
     * {@code markNotified}.
     *
     * @param session the transaction session created for marking.
     * @param commitChanges wheather transaction should be committed or rolled back
     * @return true if successful
     */
    private boolean endTransaction(Session session, boolean commitChanges)
    {
        Transaction t = null;
        try {
            t = session.getTransaction();
            if (commitChanges) {
                t.commit();
                session.flush();
            } else {
                t.rollback();
            }
        } catch (HibernateException ex) {
            this.logger.error("ERROR committing changes to the matching notification database", ex);
            if (t != null) {
                t.rollback();
            }
            return false;
        } finally {
            session.close();
        }
        return true;
    }

    @Override
    public boolean deleteMatchesForLocalPatient(String patientId)
    {
        Session session = this.beginTransaction();
        boolean transactionCompleted = false;

        try {
            Query query = session.createQuery(HQL_DELETE_ALL_MATCHES_FOR_LOCAL_PATIENT);
            query.setParameter("localId", patientId);

            int numDeleted = query.executeUpdate();
            transactionCompleted = true;

            this.logger.debug("Removed all [{}] stored matches for patient [{}]", numDeleted, patientId);
        } catch (Exception ex) {
            this.logger.error("Error deleting matches for local patients: [{}]", ex.getMessage(), ex);
        } finally {
            transactionCompleted = this.endTransaction(session, transactionCompleted) && transactionCompleted;
        }
        return transactionCompleted;
    }

    private void deleteLocalMatchesForLocalPatient(Session session, String patientId,
        boolean deleteNotified, boolean deleteRejected)
    {
        Query query = session.createQuery(HQL_DELETE_LOCAL_MATCHES_FOR_LOCAL_PATIENT
            + (deleteNotified ? "" : " and notified = false")
            + (deleteRejected ? "" : " and status != 'rejected'"));
        query.setParameter("localId", patientId);

        int numDeleted = query.executeUpdate();

        this.logger.debug("Removed [{}] stored local matches for patient [{}]", numDeleted, patientId);
    }

    /*
     * Deletes all stored un-notified un-rejected matchs for the given patient and server.
     */
    private void deleteOutgoingMatchesByPatientId(Session session, String localPatientId, String remoteServerId)
    {
        if (StringUtils.isNotEmpty(localPatientId) && StringUtils.isNotEmpty(remoteServerId)) {

            Query query = session.createQuery(HQL_DELETE_ALL_OUTGOING_MATCHES_FOR_LOCAL_PATIENT);
            query.setParameter("localId", localPatientId);
            query.setParameter("remoteServerId", remoteServerId);

            int numDeleted = query.executeUpdate();

            this.logger.debug("Removed all [{}] stored outgoing  matches for patient [{}] and server [{}]",
                numDeleted, localPatientId, remoteServerId);
        }
    }

    /*
     * Deletes all stored un-notified un-rejected matchs for the given patient and server.
     */
    private void deleteIncomingMatchesByPatientId(Session session, String remotePatientId, String remoteServerId)
    {
        if (StringUtils.isNotEmpty(remotePatientId) && StringUtils.isNotEmpty(remoteServerId)) {
            Query query = session.createQuery(HQL_DELETE_ALL_INCOMING_MATCHES_FOR_REMOTE_PATIENT);
            query.setParameter("remoteId", remotePatientId);
            query.setParameter("remoteServerId", remoteServerId);

            int numDeleted = query.executeUpdate();

            this.logger.debug("Removed all [{}] stored incoming  matches for remote patient [{}] and server [{}]",
                numDeleted, remotePatientId, remoteServerId);
        }
    }

    private void saveMatches(Session session, List<PatientMatch> matches)
    {
        for (PatientMatch match : matches) {
            session.save(match);
        }
    }

    @Override
    public Long getNumberOfRemoteMatches()
    {
        Session session = this.beginTransaction();
        try {
            Query query = session.createQuery(HQL_QUERY_REMOTE_MATCHES);
            return (Long) query.uniqueResult();
        } catch (Exception ex) {
            this.logger.error("Error geting remote matches: [{}]", ex.getMessage(), ex);
        } finally {
            session.close();
        }
        return null;
    }
}
