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
import org.phenotips.data.similarity.PatientSimilarityView;
import org.phenotips.matchingnotification.finder.MatchFinder;
import org.phenotips.matchingnotification.match.PatientMatch;
import org.phenotips.similarity.SimilarPatientsFinder;

import org.xwiki.component.annotation.Component;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

/**
 * @version $Id$
 */
@Component
@Named("local")
@Singleton
public class LocalMatchFinder extends AbstractMatchFinder implements MatchFinder
{
    private static final String RUN_INFO_DOCUMENT_LOCALSERVER_ID = "local";

    private static final Set<String> SUPPORTED_SERVER_IDS =
            new HashSet<>(Arrays.asList(RUN_INFO_DOCUMENT_LOCALSERVER_ID));

    @Inject
    private SimilarPatientsFinder finder;

    @Override
    public int getPriority()
    {
        return 200;
    }

    @Override
    protected Set<String> getSupportedServerIdList()
    {
        return SUPPORTED_SERVER_IDS;
    }

    @Override
    protected MatchRunStatus specificFindMatches(Patient patient, String serverId, List<PatientMatch> matchesList)
    {
        this.logger.debug("Finding local matches for patient {}.", patient.getId());

        List<PatientSimilarityView> localMatches = this.finder.findSimilarPatients(patient);

        List<PatientMatch> matches = this.matchStorageManager.getMatchesToBePlacedIntoNotificationTable(localMatches);

        this.matchStorageManager.saveLocalMatches(matches, patient.getId());

        matchesList.addAll(matches);

        return MatchRunStatus.OK;
    }
}
