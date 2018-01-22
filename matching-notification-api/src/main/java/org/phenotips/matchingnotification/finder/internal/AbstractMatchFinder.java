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
import org.phenotips.matchingnotification.finder.MatchFinder;
import org.phenotips.matchingnotification.match.PatientMatch;
import org.phenotips.matchingnotification.storage.MatchStorageManager;

import org.xwiki.component.phase.Initializable;
import org.xwiki.component.phase.InitializationException;
import org.xwiki.model.EntityType;
import org.xwiki.model.reference.EntityReference;

import java.util.Date;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Provider;

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
public abstract class AbstractMatchFinder implements MatchFinder, Initializable
{
    private static final EntityReference PHENOMECENTRAL_SPACE = new EntityReference("PhenomeCentral", EntityType.SPACE);

    private static final EntityReference MATCHING_RUN_INFO_CLASS = new EntityReference(
        "MatchingRunInfoClass", EntityType.DOCUMENT, PHENOMECENTRAL_SPACE);

    private static final EntityReference MATCHING_RUN_INFO_DOCUMENT =
        new EntityReference("MatchingUpdateAndInfo", EntityType.DOCUMENT, PHENOMECENTRAL_SPACE);

    private static final String RUN_INFO_DOCUMENT_SERVERNAME = "serverName";

    private static final String RUN_INFO_DOCUMENT_STARTTIME = "startedTime";

    private static final String RUN_INFO_DOCUMENT_ENDTIME = "completedTime";

    private static final String RUN_INFO_DOCUMENT_PATIENTCOUNT = "numPatientsUsedForLastMatchRun";

    protected Date previousStartedTime;

    protected Integer numPatientsTestedForMatches;

    protected Logger logger = LoggerFactory.getLogger(this.getClass());

    @Inject
    protected MatchStorageManager matchStorageManager;

    @Inject
    private Provider<XWikiContext> provider;

    private DateTimeFormatter dateFormatter = ISODateTimeFormat.dateTime().withZone(DateTimeZone.UTC);

    private XWikiDocument runInfoDoc;

    @Override
    public void initialize() throws InitializationException
    {
        this.runInfoDoc = this.getMatchingRunInfoDoc();
    }

    /*
     * The list of server names that will be added to the run info document
     */
    protected abstract List<String> getRunInfoDocumentIdList();

    /*
     * TODO: possibly make this "public" to be able to query which patients will be updated when the matcher
     *       is run before actually running it. But need to rework when `previousStartedTime` is obtained for that
     */
    protected boolean isPatientUpdatedAfterLastRun(Patient patient)
    {
        if (this.previousStartedTime != null) {
            PatientData<String> patientData = patient.<String>getData("metadata");
            DateTime lastModificationDate = this.dateFormatter.parseDateTime(patientData.get("date"));

            if (lastModificationDate.isBefore(new DateTime(this.previousStartedTime))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public List<PatientMatch> findMatches(Patient patient)
    {
        return this.findMatches(patient, false);
    }

    @Override
    public void recordStartMatchesSearch()
    {
        // note: error() is used intentionally since this is important information we always want to have in the logs
        this.logger.error("Starting [{}] match finder for multiple patients...", this.getName());

        this.numPatientsTestedForMatches = 0;

        // TODO: need to review how this optimization is implemented. For now we
        //       store the last time this matcher was started, and do not update matches for
        //       patients modified before that time
        this.previousStartedTime = this.recordMatchFinderStatus(RUN_INFO_DOCUMENT_STARTTIME);
    }

    @Override
    public void recordEndMatchesSearch()
    {
        this.logger.error("Finished running [{}] match finder", this.getName());

        this.previousStartedTime = this.recordMatchFinderStatus(RUN_INFO_DOCUMENT_ENDTIME);
    }

    /*
     * TODO: add ability to record the log of the run in the run info record, e.g. a list of
     *       patients which received an error from MME. Need to think about the correct UI for that
     *
     * @return the start time of the previous local matcher run
     */
    private Date recordMatchFinderStatus(String timePropertyName)
    {
        if (this.runInfoDoc == null) {
            return null;
        }

        try {
            XWikiContext context = this.provider.get();

            List<String> remoteIds = this.getRunInfoDocumentIdList();

            Date previousRunStartedTime = new Date();

            for (String serverId : remoteIds) {
                BaseObject object = this.runInfoDoc.getXObject(MATCHING_RUN_INFO_CLASS, RUN_INFO_DOCUMENT_SERVERNAME,
                        serverId, false);
                if (object == null) {
                    object = this.runInfoDoc.newXObject(MATCHING_RUN_INFO_CLASS, context);
                    object.setStringValue(RUN_INFO_DOCUMENT_SERVERNAME, serverId);
                } else {
                    // use the earliest start time for all servers used
                    Date previousRunStartedTimeForThisServer = object.getDateValue(RUN_INFO_DOCUMENT_STARTTIME);
                    if (previousRunStartedTimeForThisServer != null
                        && previousRunStartedTime.after(previousRunStartedTimeForThisServer)) {
                        previousRunStartedTime = previousRunStartedTimeForThisServer;
                    }
                }
                object.setIntValue(RUN_INFO_DOCUMENT_PATIENTCOUNT, this.numPatientsTestedForMatches);
                object.setDateValue(timePropertyName, new Date());
            }
            context.getWiki().saveDocument(this.runInfoDoc, context);

            return previousRunStartedTime;
        } catch (Exception e) {
            this.logger.error("Failed to save [{}] match finder status {}.", this.getName(), timePropertyName, e);
        }
        return null;
    }

    private XWikiDocument getMatchingRunInfoDoc()
    {
        try {
            XWikiContext context = this.provider.get();
            XWikiDocument doc = context.getWiki().getDocument(MATCHING_RUN_INFO_DOCUMENT, context);

            if (doc != null && !doc.isNew()) {
                return doc;
            } else {
                this.logger.error("Matching run info document is blank or missing");
            }

        } catch (Exception e) {
            this.logger.error("Failed to get matching run info document: {}", e.getMessage(), e);
        }

        return null;
    }
}
