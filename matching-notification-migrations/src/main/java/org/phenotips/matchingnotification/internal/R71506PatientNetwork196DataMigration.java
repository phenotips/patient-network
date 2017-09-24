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

import org.phenotips.data.PatientData;
import org.phenotips.data.similarity.PatientSimilarityView;
import org.phenotips.data.similarity.PatientSimilarityViewFactory;
import org.phenotips.matchingnotification.match.PatientInMatch;
import org.phenotips.matchingnotification.match.PatientMatch;
import org.phenotips.matchingnotification.match.internal.DefaultPatientMatch;

import org.xwiki.component.annotation.Component;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReferenceSerializer;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.hibernate.HibernateException;
import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.slf4j.Logger;

import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.store.hibernate.HibernateSessionFactory;
import com.xpn.xwiki.store.migration.DataMigrationException;
import com.xpn.xwiki.store.migration.XWikiDBVersion;
import com.xpn.xwiki.store.migration.hibernate.AbstractHibernateDataMigration;
import com.xpn.xwiki.store.migration.hibernate.HibernateDataMigration;

/**
 * Migration for PatientNetwork issue PN-196: Create migrator to delete matches with deleted local patients and update
 * local matches with modified patients.
 *
 * @version $Id$
 * @since 1.2
 */
@Component(roles = { HibernateDataMigration.class })
@Named("R71506PatientNetwork196")
@Singleton
public class R71506PatientNetwork196DataMigration extends AbstractHibernateDataMigration
{
    /** Logging helper object. */
    @Inject
    private Logger logger;

    /** Resolves unprefixed document names to the current wiki. */
    @Inject
    @Named("current")
    private DocumentReferenceResolver<String> resolver;

    /** Serializes the PatientMatch class name. */
    @Inject
    @Named("compactwiki")
    private EntityReferenceSerializer<String> serializer;

    /** Handles persistence. */
    @Inject
    private HibernateSessionFactory sessionFactory;

    @Inject
    @Named("restricted")
    private PatientSimilarityViewFactory factory;

    @Override
    public String getDescription()
    {
        return "Delete matches with deleted local patients and update local matches with modified patients.";
    }

    @Override
    public XWikiDBVersion getVersion()
    {
        return new XWikiDBVersion(71506);
    }

    @Override
    public void hibernateMigrate() throws DataMigrationException, XWikiException
    {
        Session session = this.sessionFactory.getSessionFactory().openSession();
        Query q = session.createQuery("from " + PatientMatch.class.getCanonicalName());
        @SuppressWarnings("unchecked")
        List<PatientMatch> matches = q.list();

        // store patient IDs of local patients that were deleted to delete all matches for them
        List<String> deletedPatients = new ArrayList<>();

        Transaction t = session.beginTransaction();
        try {
            for (PatientMatch match : matches) {

                PatientInMatch referencePatient = match.getReference();
                PatientInMatch matchedPatient = match.getMatched();

                // if match has a local patient that was deleted, remove the match
                if (matchHasDeletedPatient(matchedPatient, referencePatient, deletedPatients)) {
                    session.delete(match);
                    continue;
                }

                // if match is local and any of its patients were modified since the match was saved, update the match
                if (match.isLocal() && wasModifiedAfterMatch(match)) {
                    // re-compute the match with just fetched two local patients
                    PatientSimilarityView similarityView = this.factory.makeSimilarPatient(matchedPatient.getPatient(),
                        referencePatient.getPatient());
                    PatientMatch newMatch = new DefaultPatientMatch(similarityView, null, null);
                    updateMatch(match, newMatch);
                    session.update(match);
                }
            }
            t.commit();
        } catch (HibernateException ex) {
            R71506PatientNetwork196DataMigration.this.logger.warn("Failed to migrate PatientMatch status: {}",
                ex.getMessage());
            if (t != null) {
                t.rollback();
            }
        } finally {
            session.close();
        }
    }

    /*
     * Check if match has a local patient that was deleted.
     */
    private boolean matchHasDeletedPatient(PatientInMatch matchedPatient, PatientInMatch referencePatient,
        List<String> deletedPatients)
    {
        String referencePatientId = referencePatient.getPatientId();
        String matchedPatientId = matchedPatient.getPatientId();

        // if we already know that reference or matched patient was deleted, remove the match
        if (deletedPatients.contains(referencePatientId) || deletedPatients.contains(matchedPatientId)) {
            return true;
        }

        // if reference or matched patient is local and was deleted, remove the match
        if (referencePatient.isLocal() && referencePatient.getPatient() == null) {
            deletedPatients.add(referencePatientId);
            return true;
        }

        if (matchedPatient.isLocal() && matchedPatient.getPatient() == null) {
            deletedPatients.add(matchedPatientId);
            return true;
        }

        return false;
    }

    /*
     * Check if reference or matched patients have been changed since the match was found.
     */
    private boolean wasModifiedAfterMatch(PatientMatch match)
    {
        DateTimeFormatter dateFormatter = ISODateTimeFormat.dateTime().withZone(DateTimeZone.UTC);

        PatientData<String> patientData = match.getReference().getPatient().<String>getData("metadata");
        String date = patientData.get("date");
        DateTime referenceLastModifiedDate = dateFormatter.parseDateTime(date);

        patientData = match.getMatched().getPatient().<String>getData("metadata");
        date = patientData.get("date");
        DateTime matchedLastModifiedDate = dateFormatter.parseDateTime(date);

        Timestamp matchTimestamp = match.getFoundTimestamp();
        DateTime matchTimestampFormatted = new DateTime(matchTimestamp.getTime());

        return (matchTimestampFormatted.isBefore(referenceLastModifiedDate)
            || matchTimestampFormatted.isBefore(matchedLastModifiedDate));
    }

    private void updateMatch(PatientMatch match, PatientMatch newMatch)
    {
        // update the existing match with new properties of just re-computed match
        match.setFoundTimestamp(newMatch.getFoundTimestamp());
        match.setScore(newMatch.getScore());
        match.setGenotypeScore(newMatch.getGenotypeScore());
        match.setPhenotypeScore(newMatch.getPhenotypeScore());
        match.setReferenceDetails(newMatch.getReferenceDetails());
        match.setReferencePatientInMatch(newMatch.getReference());
        match.setMatchedDetails(newMatch.getMatchedDetails());
        match.setMatchedPatientInMatch(newMatch.getMatched());
    }
}
