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
import org.phenotips.data.PatientRepository;
import org.phenotips.data.permissions.EntityPermissionsManager;
import org.phenotips.data.permissions.Visibility;
import org.phenotips.matchingnotification.finder.MatchFinder;
import org.phenotips.matchingnotification.match.PatientMatch;
import org.phenotips.matchingnotification.storage.MatchStorageManager;

import org.xwiki.model.EntityType;
import org.xwiki.model.reference.EntityReference;

import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Named;
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
public abstract class AbstractMatchFinder implements MatchFinder
{
    private static final EntityReference PHENOMECENTRAL_SPACE = new EntityReference("PhenomeCentral", EntityType.SPACE);

    private static final EntityReference MATCHING_RUN_INFO_CLASS = new EntityReference(
        "MatchingRunInfoClass", EntityType.DOCUMENT, PHENOMECENTRAL_SPACE);

    private static final EntityReference MATCHING_RUN_INFO_DOCUMENT =
        new EntityReference("MatchingUpdateAndInfo", EntityType.DOCUMENT, PHENOMECENTRAL_SPACE);

    private static final String RUN_INFO_DOCUMENT_SERVERNAME = "serverName";

    private static final String RUN_INFO_DOCUMENT_STARTTIME = "startedTime";

    private static final String RUN_INFO_DOCUMENT_ENDTIME = "completedTime";

    private static final String RUN_INFO_DOCUMENT_PATIENTCOUNT = "numPatientsCheckedForMatches";

    private static final String RUN_INFO_DOCUMENT_NUMERRORS = "numErrors";

    private static final String RUN_INFO_DOCUMENT_TOTALMATCHES = "totalMatchesFound";

    private static final String RUN_INFO_DOCUMENT_AVG_TIME_PER_PATIENT = "averageTimePerPatient";

    protected Logger logger = LoggerFactory.getLogger(this.getClass());

    @Inject
    protected MatchStorageManager matchStorageManager;

    protected enum MatchRunStatus
    { NOT_RUN, OK, ERROR };

    @Inject
    private Provider<XWikiContext> provider;

    @Inject
    private PatientRepository patientRepository;

    @Inject
    @Named("secure")
    private EntityPermissionsManager permissionsManager;

    @Inject
    @Named("matchable")
    private Visibility matchableVisibility;

    private DateTimeFormatter dateFormatter = ISODateTimeFormat.dateTime().withZone(DateTimeZone.UTC);

    protected abstract Set<String> getSupportedServerIdList();

    protected abstract MatchRunStatus specificFindMatches(Patient patient, String serverId,
            List<PatientMatch> matchesList);

    @Override
    public List<PatientMatch> findMatches(List<String> patientIds, Set<String> serverIds,
            boolean onlyUpdatedAfterLastRun)
    {
        List<PatientMatch> patientMatches = new LinkedList<>();

        Set<String> supportedServers = this.getSupportedServerIdList();

        for (String serverId : serverIds) {
            try {
                if (!supportedServers.contains(serverId)) {
                    continue;
                }

                Date lastRunTime = this.recordStartMatchesSearch(serverId);

                int numPatientsTestedForMatches = 0;
                int numErrors = 0;
                int totalMatchesFound = 0;
                long totalRunTime = 0;

                for (String patientId : patientIds) {
                    Patient patient = this.getPatientIfShouldBeUsed(patientId, onlyUpdatedAfterLastRun, lastRunTime);
                    if (patient == null) {
                        continue;
                    }

                    int matchesBefore = patientMatches.size();
                    long startTime = System.currentTimeMillis();

                    MatchRunStatus matcherStatus = this.specificFindMatches(patient, serverId, patientMatches);

                    totalRunTime += (System.currentTimeMillis() - startTime);
                    totalMatchesFound += (patientMatches.size() - matchesBefore);

                    if (matcherStatus != MatchRunStatus.NOT_RUN) {
                        numPatientsTestedForMatches++;
                    }
                    if (matcherStatus == MatchRunStatus.ERROR) {
                        numErrors++;
                    }
                }

                this.recordEndMatchesSearch(serverId, numPatientsTestedForMatches,
                        numErrors, totalMatchesFound, totalRunTime);
            } catch (Exception ex) {
                this.logger.error("Error finding matches using server [{}]: [{}]", serverId, ex.getMessage(), ex);
            }
        }

        return patientMatches;
    }

    /*
     * TODO: possibly make this "public" to be able to query which patients will be updated when the matcher
     *       is run before actually running it. But need to rework when `previousStartedTime` is obtained for that
     */
    protected boolean isPatientUpdatedAfterGivenTime(Patient patient, Date time)
    {
        if (time != null) {
            PatientData<String> patientData = patient.<String>getData("metadata");
            DateTime lastModificationDate = this.dateFormatter.parseDateTime(patientData.get("date"));

            if (lastModificationDate.isBefore(new DateTime(time))) {
                return true;
            }
        }
        return false;
    }

    protected boolean patientIsSolved(Patient patient)
    {
        PatientData<String> data = patient.getData("solved");
        if (data != null && data.size() > 0) {
            return "1".equals(data.get("solved"));
        }
        return false;
    }

    protected Patient getPatientIfShouldBeUsed(String patientId, boolean onlyUpdatedAfterLastRun, Date lastRunTime)
    {
        Patient patient = this.patientRepository.get(patientId);
        if (patient == null) {
            return null;
        }

        Visibility patientVisibility = this.permissionsManager.getEntityAccess(patient).getVisibility();
        if (patientVisibility.compareTo(this.matchableVisibility) < 0) {
            return null;
        }

        if (onlyUpdatedAfterLastRun && this.isPatientUpdatedAfterGivenTime(patient, lastRunTime)) {
            return null;
        }

        if (this.patientIsSolved(patient)) {
            return null;
        }

        return patient;
    }

    protected Date recordStartMatchesSearch(String serverId)
    {
        // note: error() is used intentionally since this is important information we always want to have in the logs
        this.logger.error("Starting [{}] match finder for multiple patients...", serverId);

        Date previousStartedTime = this.recordMatchFinderStatus(serverId, RUN_INFO_DOCUMENT_STARTTIME, 0, 0, 0, 0);

        return previousStartedTime;
    }

    protected void recordEndMatchesSearch(String serverId, int numPatientsTestedForMatches,
            int numErrors, int totalMatchesFound, long totalRunTime)
    {
        this.logger.error("Finished running [{}] match finder", serverId);

        this.recordMatchFinderStatus(serverId, RUN_INFO_DOCUMENT_ENDTIME, numPatientsTestedForMatches,
                numErrors, totalMatchesFound, totalRunTime);
    }

    /*
     * TODO: add ability to record a summary/log of the run in the run info record, e.g. a list of
     *       patients which received an error from MME. Need to think about the correct UI for that
     *
     * @return the start time of the previous run of the same matcher for the same server
     */
    private Date recordMatchFinderStatus(String serverId, String timePropertyName, int numPatientsTestedForMatches,
            int numErrors, int totalMatchesFound, long totalRunTime)
    {
        try {
            XWikiDocument runInfoDoc = getMatchingRunInfoDoc();
            if (runInfoDoc == null) {
                return null;
            }

            XWikiContext context = this.provider.get();

            Date previousRunStartedTime = null;

            BaseObject object = runInfoDoc.getXObject(MATCHING_RUN_INFO_CLASS, RUN_INFO_DOCUMENT_SERVERNAME,
                    serverId, false);
            if (object == null) {
                object = runInfoDoc.newXObject(MATCHING_RUN_INFO_CLASS, context);
                object.setStringValue(RUN_INFO_DOCUMENT_SERVERNAME, serverId);
            } else {
                previousRunStartedTime = object.getDateValue(RUN_INFO_DOCUMENT_STARTTIME);
            }
            object.setIntValue(RUN_INFO_DOCUMENT_PATIENTCOUNT, numPatientsTestedForMatches);
            object.setIntValue(RUN_INFO_DOCUMENT_NUMERRORS, numErrors);
            object.setIntValue(RUN_INFO_DOCUMENT_TOTALMATCHES, totalMatchesFound);

            Double averageTimePerPatient = (numPatientsTestedForMatches == 0) ? 0
                    : Math.round((double) totalRunTime / (10 * (double) numPatientsTestedForMatches)) / 100.0;
            object.setDoubleValue(RUN_INFO_DOCUMENT_AVG_TIME_PER_PATIENT, averageTimePerPatient);

            object.setDateValue(timePropertyName, new Date());

            context.getWiki().saveDocument(runInfoDoc, context);

            return previousRunStartedTime;
        } catch (Exception e) {
            this.logger.error("Failed to save [{}] match finder status {}.", serverId, timePropertyName, e);
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
