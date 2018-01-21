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
import org.phenotips.matchingnotification.match.internal.DefaultPatientMatch;
import org.phenotips.similarity.SimilarPatientsFinder;

import org.xwiki.component.annotation.Component;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

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
    private static final String RUN_INFO_DOCUMENT_LOCALSERVER = "local matches";

    @Inject
    private SimilarPatientsFinder finder;

    @Override
    public int getPriority()
    {
        return 200;
    }

    @Override
    public String getName()
    {
        return "local";
    }

    @Override
    public List<String> getRunInfoDocumentIdList()
    {
        return Arrays.asList(RUN_INFO_DOCUMENT_LOCALSERVER);
    }

    @Override
    public List<PatientMatch> findMatches(Patient patient, boolean onlyUpdatedAfterLastRun)
    {
        List<PatientMatch> matches = new LinkedList<>();

        if (onlyUpdatedAfterLastRun && this.isPatientUpdatedAfterLastRun(patient)) {
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
}
