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
import org.phenotips.data.PatientData;
import org.phenotips.data.similarity.PatientSimilarityView;
import org.phenotips.matchingnotification.finder.MatchFinder;
import org.phenotips.matchingnotification.match.PatientMatch;
import org.phenotips.matchingnotification.match.internal.DefaultPatientMatch;
import org.phenotips.matchingnotification.storage.MatchStorageManager;
import org.phenotips.similarity.SimilarPatientsFinder;

import org.xwiki.component.annotation.Component;
import org.xwiki.component.phase.Initializable;
import org.xwiki.component.phase.InitializationException;
import org.xwiki.model.EntityType;
import org.xwiki.model.reference.EntityReference;

import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;

/**
 * @version $Id$
 */
@Component
@Named("local")
@Singleton
public class LocalMatchFinder implements MatchFinder, Initializable
{
    private static final EntityReference PHENOMECENTRAL_SPACE = new EntityReference("PhenomeCentral", EntityType.SPACE);

    private static final EntityReference MATCHING_RUN_INFO_CLASS = new EntityReference(
        "MatchingRunInfoClass", EntityType.DOCUMENT, PHENOMECENTRAL_SPACE);

    private static final EntityReference MATCHING_RUN_INFO_DOCUMENT =
        new EntityReference("MatchingRunInfo", EntityType.DOCUMENT, PHENOMECENTRAL_SPACE);

    private static final String RUN_INFO_DOCUMENT_SERVERNAME = "serverName";

    private static final String RUN_INFO_DOCUMENT_LOCALSERVER = "local matches";

    private static final String RUN_INFO_DOCUMENT_STARTTIME = "startedTime";

    private static final String RUN_INFO_DOCUMENT_ENDTIME = "completedTime";

    private static final String RUN_INFO_DOCUMENT_PATIENTCOUNT = "numPatientsUsedForLastMatchRun";

    private Logger logger = LoggerFactory.getLogger(LocalMatchFinder.class);

    @Inject
    private SimilarPatientsFinder finder;

    @Inject
    private MatchStorageManager matchStorageManager;

    @Inject
    private Provider<XWikiContext> provider;

    private DateTimeFormatter dateFormatter = ISODateTimeFormat.dateTime().withZone(DateTimeZone.UTC);

    private Date previousStartedTime;

    private Integer numPatientsTestedForMatches;

    private XWikiDocument prefsDoc;

    @Override
    public void initialize() throws InitializationException
    {
        try {
            XWikiContext context = this.provider.get();

            this.prefsDoc = context.getWiki().getDocument(MATCHING_RUN_INFO_DOCUMENT, context);

            if (this.prefsDoc != null && !this.prefsDoc.isNew()) {
                BaseObject object = this.prefsDoc.getXObject(MATCHING_RUN_INFO_CLASS,
                        RUN_INFO_DOCUMENT_SERVERNAME, RUN_INFO_DOCUMENT_LOCALSERVER, false);
                if (object == null) {
                    object = this.prefsDoc.newXObject(MATCHING_RUN_INFO_CLASS, context);
                    object.setStringValue(RUN_INFO_DOCUMENT_SERVERNAME, RUN_INFO_DOCUMENT_LOCALSERVER);
                    context.getWiki().saveDocument(this.prefsDoc, context);
                }
            }
        } catch (Exception e) {
            this.logger.error("Failed to modify matching run info document: {}", e.getMessage(), e);
        }
    }

    @Override
    public int getPriority()
    {
        return 200;
    }

    @Override
    public List<PatientMatch> findMatches(Patient patient)
    {
        List<PatientMatch> matches = new LinkedList<>();

        PatientData<String> patientData = patient.<String>getData("metadata");
        String date = patientData.get("date");
        DateTime lastModificationDate = this.dateFormatter.parseDateTime(date);

        if (this.previousStartedTime != null && lastModificationDate.isBefore(new DateTime(this.previousStartedTime))) {
            return matches;
        }

        this.logger.debug("Finding local matches for patient {}.", patient.getId());

        List<PatientSimilarityView> similarPatients = this.finder.findSimilarPatients(patient);
        for (PatientSimilarityView similarityView : similarPatients) {
            PatientMatch match = new DefaultPatientMatch(similarityView, null, null);
            matches.add(match);
        }

        this.numPatientsTestedForMatches++;

        this.matchStorageManager.saveLocalMatches(matches, patient.getId());

        return matches;
    }

    @Override
    public void recordStartMatchesSearch()
    {
        // note: error() is used intentionally since this is important information we always want to have in the logs
        this.logger.error("Starting find all local matches run...");

        this.numPatientsTestedForMatches = 0;

        // TODO: need to review how this optimization is implemented. For now we
        //       store the last time this matcher was started, and do not update matches for
        //       patients modified before that time
        this.previousStartedTime = this.recordMatchSearchStatus(RUN_INFO_DOCUMENT_STARTTIME);
    }

    @Override
    public void recordEndMatchesSearch()
    {
        this.logger.error("Finished find all local matches run");

        this.recordMatchSearchStatus(RUN_INFO_DOCUMENT_ENDTIME);

        // TODO: currently the "find all matches" method structures the loops as:
        //
        //  for (patients) {
        //    for (matchers) {
        //      matcher.findMatches(patient)
        //    }
        //  }
        //
        // which means this.findMatches() does not know if it is called as part of a big loop
        // or as a standalone call for just one patient. So to make sure this.findMatches() always
        // runs when called manually, need to reset this.previousStartedTime
        this.previousStartedTime = null;
    }

    /*
     * @return the start time of the previous local matcher run
     */
    private Date recordMatchSearchStatus(String timePropertyName)
    {
        try {
            XWikiContext context = this.provider.get();

            BaseObject object = this.prefsDoc.getXObject(MATCHING_RUN_INFO_CLASS, RUN_INFO_DOCUMENT_SERVERNAME,
                    RUN_INFO_DOCUMENT_LOCALSERVER, false);

            Date previousRunStartedTime = object.getDateValue(RUN_INFO_DOCUMENT_STARTTIME);

            object.setIntValue(RUN_INFO_DOCUMENT_PATIENTCOUNT, this.numPatientsTestedForMatches);
            object.setDateValue(timePropertyName, new Date());
            context.getWiki().saveDocument(this.prefsDoc, context);

            return previousRunStartedTime;
        } catch (Exception e) {
            this.logger.error("Failed to save matching run status {}.", timePropertyName, e);
        }
        return null;
    }
}
