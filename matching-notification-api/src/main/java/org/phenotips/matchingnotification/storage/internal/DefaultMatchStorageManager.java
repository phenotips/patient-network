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
import org.phenotips.matchingnotification.match.internal.CurrentPatientMatch;
import org.phenotips.matchingnotification.match.internal.HistoricPatientMatch;
import org.phenotips.matchingnotification.storage.MatchStorageManager;

import org.xwiki.component.annotation.Component;
import org.xwiki.query.QueryManager;
import org.xwiki.users.User;
import org.xwiki.users.UserManager;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.hibernate.HibernateException;
import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.criterion.Criterion;
import org.hibernate.criterion.Restrictions;
import org.json.JSONObject;
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
    /** A query used to delete matches by IDs.  */
    private static final String HQL_DELETE_MATCHES_BY_IDS =
        "delete CurrentPatientMatch where id in :idlist";

    /** A query used to delete all matches (including MME) for the given local patient (ID == localId). */
    private static final String HQL_DELETE_ALL_MATCHES_FOR_LOCAL_PATIENT =
        "delete CurrentPatientMatch where (referenceServerId = '' and referencePatientId = :localId)"
            + " or (matchedServerId ='' and matchedPatientId = :localId)";

    /** A query used to delete all matches (including MME) for the given local patient (ID == localId). */
    private static final String HQL_DELETE_ALL_HISTORIC_MATCHES_FOR_LOCAL_PATIENT =
        "delete HistoricPatientMatch where (referenceServerId = '' and referencePatientId = :localId)"
            + " or (matchedServerId ='' and matchedPatientId = :localId)";

    /** A query to find all matches with a score(s) greater than given. */
    private static final String HQL_FIND_ALL_MATCHES_BY_SCORE =
        "from CurrentPatientMatch as m where"
            + " score >= :minScore and phenotypeScore >= :phenScore and genotypeScore >= :genScore";

    /** A query to find all matches for a local patient with a score(s) greater than given. */
    private static final String HQL_FIND_ALL_MATCHES_FOR_PATIENT_BY_SCORE =
        "from CurrentPatientMatch as m where (referencePatientId = :patientId and referenceServerId = ''"
            + " or matchedPatientId = :patientId and matchedServerId ='')"
            + " and score >= :minScore and phenotypeScore >= :phenScore and genotypeScore >= :genScore";

    /** A query used to get the number of all remote matches. */
    private static final String HQL_GET_NUMBER_OF_REMOTE_MATCHES =
        "select count(*) from CurrentPatientMatch as m where m.referenceServerId != '' or m.matchedServerId !=''";

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
    public List<PatientMatch> saveLocalMatches(Collection<? extends PatientSimilarityView> similarityViews,
        String patientId)
    {
        return this.saveMatches(similarityViews, patientId, "", "");
    }

    @Override
    public List<PatientMatch> saveRemoteMatches(Collection<? extends PatientSimilarityView> similarityViews,
            String patientId, String serverId, boolean isIncoming)
    {
        String referenceServerId = isIncoming ? serverId : "";
        String matchedServerId = isIncoming ? "" : serverId;

        return this.saveMatches(similarityViews, patientId, referenceServerId, matchedServerId);
    }

    private Map<PatientSimilarityView, PatientMatch>
        convertSimilarityViewsToPatientMatches(Collection<? extends PatientSimilarityView> similarityViews,
            String referenceServerId, String matchedServerId)
    {
        Map<PatientSimilarityView, PatientMatch> matchMapping = new HashMap<>();

        for (PatientSimilarityView similarityView : similarityViews) {
            PatientMatch match = new CurrentPatientMatch(similarityView, referenceServerId, matchedServerId);
            matchMapping.put(similarityView, match);
        }
        return matchMapping;
    }

    private List<PatientMatch> saveMatches(Collection<? extends PatientSimilarityView> similarityViews,
        String patientId, String referenceServerId, String matchedServerId)
    {
        this.logger.debug("[debug] saving [{}] matches for patient [{}] @ server [{}]...",
                similarityViews.size(), patientId, referenceServerId);

        // input data validation: the rest of the code below assumes all matches are for a single local patient
        if (!this.validateAllMatchesForSingleReferencePatient(similarityViews, patientId)) {
            this.logger.error("Not all matches which should be saved involve the same patient: [{}]", patientId);
            return null;
        }

        // convert similarity views to PatientMatch and get the mapping from one to the other
        Map<PatientSimilarityView, PatientMatch> matchMapping =
                this.convertSimilarityViewsToPatientMatches(similarityViews, referenceServerId, matchedServerId);

        Session session = this.beginTransaction();
        boolean transactionCompleted = false;
        List<PatientMatch> matchesToSave = new LinkedList<>();

        try {
            // get existing matches between this patient and other patients on the matchedServerId;
            // to speed up searches matches are returned as a mapping between otherPatientId and a match
            Map<String, PatientMatch> existingMatchesByMatchedPatient =
                    getExistingMatchesByPatient(patientId, referenceServerId, matchedServerId);

            // there are 4 cases:
            //
            // 1) existing match is equivalent (has same match data) to one of the new matches
            //     -> do nothing with the DB
            //     -> put existing match to matchMapping so that it has the right ID
            //
            // 2) existing match is similar to one of the new matches, but not the same
            //     -> copy metadata to new match (found timestamp, notes, history, etc.)
            //     -> copy old match to history
            //     -> remove old match
            //     -> save new match
            //
            // 3) existing match has no equivalent among new matches
            //     -> copy old match to history
            //     -> remove old match
            //
            // 4) new match has no equivalent among existing matches
            //     -> save new match

            for (Map.Entry<PatientSimilarityView, PatientMatch> entry : matchMapping.entrySet()) {
                PatientMatch match = entry.getValue();
                if (match == null) {
                    // this SimilarityView should not be saved in the DB, so can be ignored
                    continue;
                }
                if (existingMatchesByMatchedPatient.containsKey(match.getMatchedPatientId())) {
                    PatientMatch existingMatch = existingMatchesByMatchedPatient.get(match.getMatchedPatientId());

                    if (existingMatch.hasSameMatchData(match)) {
                        // case #1: assign existing match to matchMapping, but no need to do anything with the DB
                        matchMapping.put(entry.getKey(), existingMatch);
                        existingMatchesByMatchedPatient.remove(match.getMatchedPatientId());
                    } else {
                        // case #2: clone metadata, save new match. Keep existing match in matchesByMatchedPatient
                        //          so that it gets saved to history table and gets removed
                        preserveOriginalMatchMetaInfo(match, existingMatch);
                        matchesToSave.add(match);
                    }
                } else {
                    // case #4: new match
                    matchesToSave.add(match);
                }
            }

            // case #3
            // by this point matchesByMatchedPatient only has matches which have no equivalent among
            // newly found matches, or which have equivalent but need to be removed because match data has changed.
            //
            // => so matchesByMatchedPatient has all the matches that need to be removed now, and only such matches.
            this.deleteMatches(session, existingMatchesByMatchedPatient.values());

            // add new matches
            this.saveMatches(session, matchesToSave);

            transactionCompleted = true;
        } catch (Exception ex) {
            this.logger.error("Error saving matches: [{}]", ex.getMessage(), ex);
        } finally {
            transactionCompleted = this.endTransaction(session, transactionCompleted) && transactionCompleted;
        }
        return transactionCompleted ? matchesToSave : null;
    }

    /**
     * For a given patient returns all patients from the specified server matching it, as a map
     * otherPatientId -> PatientMatch.
     *
     * @param patientId ID of the patient that we want to get matches for
     * @param referenceServerId server ID of the server that hold that patient
     * @param matchedServerId only matches with patients located on the server with this ID are returned
     * @return a map otherPatientId -> PatientMatch
     */
    private Map<String, PatientMatch> getExistingMatchesByPatient(String patientId, String referenceServerId,
            String matchedServerId)
    {
        List<PatientMatch> existingMatches =
                this.loadMatchesForPatientAndServer(patientId, referenceServerId, matchedServerId);

        // to speed up searches group matches by the ID of the other (not patientId) patient in a match
        Map<String, PatientMatch> matchesByMatchedPatient = new HashMap<>();
        for (PatientMatch match : existingMatches) {
            if (match.getReferencePatientId().equals(patientId)) {
                matchesByMatchedPatient.put(match.getMatchedPatientId(), match);
            } else {
                matchesByMatchedPatient.put(match.getReferencePatientId(), match);
            }
        }

        return matchesByMatchedPatient;
    }

    private boolean validateAllMatchesForSingleReferencePatient(
            Collection<? extends PatientSimilarityView> similarityViews, String patientId)
    {
        for (PatientSimilarityView match : similarityViews) {
            String refPatientID = match.getReference().getId();
            if (!patientId.equals(refPatientID)) {
                this.logger.error("A list of matches that is supposed to be for reference patient [{}]"
                    + " also constains matches for reference patient [{}]", patientId, refPatientID);
                return false;
            }
        }
        return true;
    }

    private void preserveOriginalMatchMetaInfo(PatientMatch match, PatientMatch existingMatch)
    {
        match.setFoundTimestamp(existingMatch.getFoundTimestamp());
        match.setStatus(existingMatch.getStatus());
        match.setComments(existingMatch.getComments());
        match.setNotificationHistory(existingMatch.getNotificationHistory());
        match.setNotes(existingMatch.getNotes());
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<PatientMatch> loadMatches(String patientId, double score, double phenScore, double genScore,
        boolean onlyCurrentUserAccessible, final Timestamp fromDate, final Timestamp toDate)
    {
        // ...else it is more complicated: need to return all matches, but
        // also exclude un-notified matches that have a similar match that have been notified
        //
        // This is because if owners of a match were notified, the notified version of the match
        // will be stored as-is in the database, but new versions of the match will still keep being
        // placed in the DB, resulting in two similar matches in the DB:
        // first: patientA - patientB - notified - <data at the moment of notification>
        // second: patientA - patientB - NOT notified - <data as of last match check>

        Session session = this.sessionFactory.getSessionFactory().openSession();
        try {
            String queryString = HQL_FIND_ALL_MATCHES_BY_SCORE;
            if (StringUtils.isNotBlank(patientId)) {
                queryString = HQL_FIND_ALL_MATCHES_FOR_PATIENT_BY_SCORE;
            }

            if (fromDate != null) {
                queryString += " and foundTimestamp >= :fromTimestamp";
            }

            if (toDate != null) {
                queryString += " and foundTimestamp <= :toTimestamp";
            }

            Query query = session.createQuery(queryString);
            query.setParameter("minScore", score);
            query.setParameter("phenScore", phenScore);
            query.setParameter("genScore", genScore);

            if (StringUtils.isNotBlank(patientId)) {
                query.setParameter("patientId", patientId);
            }

            if (fromDate != null) {
                query.setTimestamp("fromTimestamp", fromDate);
            }

            if (toDate != null) {
                query.setTimestamp("toTimestamp", toDate);
            }

            long queryStartTime = System.currentTimeMillis();

            List<PatientMatch> result = query.list();

            this.logger.debug("Retrieved [{}] matches with score > [{}], phenotypical score > [{}],"
                + " genotypical score > [{}]", result.size(), score, phenScore, genScore);

            this.logger.error("Retrieved [{}] matches in [{}] ms",
                    result.size(), (System.currentTimeMillis() - queryStartTime));

            if (onlyCurrentUserAccessible) {
                long filterStartTime = System.currentTimeMillis();

                this.filterMatchesForCurrentUser(result);

                this.logger.error("Filtered matches for current user in [{}] ms",
                        (System.currentTimeMillis() - filterStartTime));
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

            this.logger.debug("List of patients current user has access to: [{}]", String.join(", ", patientNames));

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
            return this.loadMatchesByCriteria(Restrictions.in("id", matchesIds.toArray()));
        } else {
            return Collections.emptyList();
        }
    }

    private int deleteMatches(Session session, Collection<PatientMatch> matches)
    {
        if (matches.size() == 0) {
            return 0;
        }

        // first copy matches being removed to the history table
        this.copyToHistory(session, matches);

        Query query = session.createQuery(HQL_DELETE_MATCHES_BY_IDS);
        query.setParameterList("idlist", this.getMatchIds(matches));
        int numDeleted = query.executeUpdate();
        if (numDeleted != matches.size()) {
            this.logger.error("A request to delete {} matches only removed {}", matches.size(), numDeleted);
        }
        return numDeleted;
    }

    private void copyToHistory(Session session, Collection<PatientMatch> matches)
    {
        for (PatientMatch match : matches) {
            PatientMatch copyForHistoryTable = new HistoricPatientMatch(match);
            session.save(copyForHistoryTable);
        }
    }

    private Collection<Long> getMatchIds(Collection<PatientMatch> matches)
    {
        Collection<Long> matchIds = new HashSet<>();
        for (PatientMatch match : matches) {
            matchIds.add(match.getId());
        }
        return matchIds;
    }

    /**
     * Load all matches between a given patient and all patients from a given server (local or remote).
     *
     * If reference patient is local and match server is local also loads matches where referencePatient
     * is matchPatient.
     *
     * @param referencePatientId id of reference patient to load matches for
     * @param referenceServerId id of the server that hosts referencePatientId
     * @param matchedServerId id of the server we want to have matches for
     * @return list of matches
     */
    private List<PatientMatch> loadMatchesForPatientAndServer(String referencePatientId, String referenceServerId,
        String matchedServerId)
    {
        if (StringUtils.isNotEmpty(referencePatientId)) {
            // referencePatientId is "reference patient"
            Criterion directMatch = Restrictions.and(
                    this.patientIsReference(referencePatientId, referenceServerId),
                    Restrictions.eq("matchedServerId", this.getStoredServerId(matchedServerId)));

            // referencePatientId is "match patient"
            Criterion reverseMatch = Restrictions.and(
                    this.patientIsMatch(referencePatientId, referenceServerId),
                    Restrictions.eq("referenceServerId", this.getStoredServerId(matchedServerId)));

            return this.loadMatchesByCriteria(Restrictions.or(directMatch, reverseMatch));
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
    private List<PatientMatch> loadMatchesByCriteria(Criterion criteriaToApply)
    {
        if (criteriaToApply == null) {
            return null;
        }

        List<PatientMatch> matches = null;
        Session session = this.sessionFactory.getSessionFactory().openSession();
        try {
            matches = session.createCriteria(CurrentPatientMatch.class).add(criteriaToApply).list();
        } catch (HibernateException ex) {
            this.logger.error("Error loading matches by criteria. Criteria: {},  ERROR: [{}]",
                    criteriaToApply.toString(), ex);
        } finally {
            session.close();
        }
        return matches;
    }

    @Override
    public boolean setUserContacted(PatientMatch match, boolean isUserContacted)
    {
        Session session = this.beginTransaction();
        boolean transactionCompleted = false;

        try {
            match.setExternallyContacted(isUserContacted);
            session.update(match);
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
    public Collection<PatientMatch> updateNotificationHistory(PatientMatch match, JSONObject notificationRecord)
    {
        Session session = this.beginTransaction();
        boolean transactionCompleted = false;

        Collection<PatientMatch> matchesToUpdate = new ArrayList<>();
        matchesToUpdate.add(match);

        if (!match.isLocal()) {
            String referenceServerId = this.getStoredServerId(match.getReferenceServerId());
            String matchedServerId = this.getStoredServerId(match.getMatchedServerId());

            // Load equivalent remote match
            PatientMatch equivalentMatch = loadMatchBetweenPatients(match.getMatchedPatientId(),
                matchedServerId, match.getReferencePatientId(), referenceServerId);
            if (equivalentMatch != null) {
                matchesToUpdate.add(equivalentMatch);
            }
        }

        Collection<PatientMatch> updatedMatches = new ArrayList<>();
        try {
            for (PatientMatch matchToUpdate : matchesToUpdate) {
                matchToUpdate.updateNotificationHistory(notificationRecord);
                session.update(matchToUpdate);
                updatedMatches.add(matchToUpdate);
            }
            transactionCompleted = true;
        } catch (Exception ex) {
            this.logger.error("Error updating notification history: [{}]", ex.getMessage(), ex);
        } finally {
            transactionCompleted = this.endTransaction(session, transactionCompleted) && transactionCompleted;
        }

        return updatedMatches;
    }

    /**
     * Load {@code match} with specified parameters.
     *
     * @param referencePatientId id of reference patient
     * @param referenceServerId id of the server that hosts reference patient
     * @param matchedPatientId id of matched patient
     * @param matchedServerId id of the server that hosts matched patient
     * @return match
     */
    private PatientMatch loadMatchBetweenPatients(String referencePatientId, String referenceServerId,
        String matchedPatientId, String matchedServerId)
    {
        if (StringUtils.isNotEmpty(referencePatientId)) {
            // referencePatientId is "reference patient"
            Criterion directMatch = Restrictions.and(
                this.patientIsReference(referencePatientId, referenceServerId),
                this.patientIsMatch(matchedPatientId, matchedServerId));
            List<PatientMatch> matches = this.loadMatchesByCriteria(directMatch);
            if (!matches.isEmpty()) {
                return matches.get(0);
            }
        }
        return null;
    }

    @Override
    public boolean updateNotes(PatientMatch match, String note)
    {
        Session session = this.beginTransaction();
        boolean transactionCompleted = false;

        try {
            match.updateNotes(note);
            session.update(match);
            transactionCompleted = true;
        } catch (Exception ex) {
            this.logger.error("Error updating notes: [{}]", ex.getMessage(), ex);
        } finally {
            transactionCompleted = this.endTransaction(session, transactionCompleted) && transactionCompleted;
        }
        return transactionCompleted;
    }

    @Override
    public boolean setStatus(PatientMatch match, String status)
    {
        Session session = this.beginTransaction();
        boolean transactionCompleted = false;

        try {
            match.setStatus(status);
            session.update(match);
            transactionCompleted = true;
        } catch (Exception ex) {
            this.logger.error("Error saving matches statuses: [{}]", ex.getMessage(), ex);
        } finally {
            transactionCompleted = this.endTransaction(session, transactionCompleted) && transactionCompleted;
        }
        return transactionCompleted;
    }

    @Override
    public boolean saveComment(PatientMatch match, String comment)
    {
        Session session = this.beginTransaction();
        boolean transactionCompleted = false;

        try {
            match.updateComments(comment);
            session.update(match);
            transactionCompleted = true;
        } catch (Exception ex) {
            this.logger.error("Error saving matches comments: [{}]", ex.getMessage(), ex);
        } finally {
            transactionCompleted = this.endTransaction(session, transactionCompleted) && transactionCompleted;
        }
        return transactionCompleted;
    }

    @Override
    public boolean addNote(PatientMatch match, String note)
    {
        Session session = this.beginTransaction();
        boolean transactionCompleted = false;

        try {
            match.updateNotes(note);
            session.update(match);
            transactionCompleted = true;
        } catch (Exception ex) {
            this.logger.error("Error saving matches note: [{}]", ex.getMessage(), ex);
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
            this.logger.error("Removed all [{}] stored matches for patient [{}]", numDeleted, patientId);

            query = session.createQuery(HQL_DELETE_ALL_HISTORIC_MATCHES_FOR_LOCAL_PATIENT);
            query.setParameter("localId", patientId);
            numDeleted = query.executeUpdate();
            this.logger.error("Removed all [{}] stored historic matches for patient [{}]", numDeleted, patientId);

            transactionCompleted = true;
        } catch (Exception ex) {
            this.logger.error("Error deleting matches for local patients: [{}]", ex.getMessage(), ex);
        } finally {
            transactionCompleted = this.endTransaction(session, transactionCompleted) && transactionCompleted;
        }
        return transactionCompleted;
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
            Query query = session.createQuery(HQL_GET_NUMBER_OF_REMOTE_MATCHES);
            return (Long) query.uniqueResult();
        } catch (Exception ex) {
            this.logger.error("Error geting remote matches: [{}]", ex.getMessage(), ex);
        } finally {
            session.close();
        }
        return null;
    }
}
