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

import java.util.LinkedList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @version $Id$
 */
@Component
@Named("local")
@Singleton
public class LocalMatchFinder implements MatchFinder
{
    private Logger logger = LoggerFactory.getLogger(LocalMatchFinder.class);

    @Inject
    private SimilarPatientsFinder finder;

    @Override
    public int getPriority() {
        return 200;
    }

    @Override
    public List<PatientMatch> findMatches(Patient patient) {

        this.logger.debug("Finding local matches for patient {}.", patient.getId());

        List<PatientMatch> matches = new LinkedList<>();
        List<PatientSimilarityView> similarPatients = finder.findSimilarPatients(patient);
        for (PatientSimilarityView similarityView : similarPatients) {
            PatientMatch match = new DefaultPatientMatch(similarityView, null, null);
            matches.add(match);
        }

        return matches;
    }
}
