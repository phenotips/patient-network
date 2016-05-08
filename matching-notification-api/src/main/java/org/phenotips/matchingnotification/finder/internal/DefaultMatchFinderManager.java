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
import org.phenotips.data.permissions.Visibility;
import org.phenotips.matchingnotification.finder.MatchFinder;
import org.phenotips.matchingnotification.finder.MatchFinderManager;
import org.phenotips.matchingnotification.match.PatientMatch;

import org.xwiki.component.annotation.Component;

import java.util.LinkedList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.slf4j.Logger;

/**
 * @version $Id$
 */
@Component
@Singleton
public class DefaultMatchFinderManager implements MatchFinderManager
{
    @Inject
    private Logger logger;

    @Inject
    private Provider<List<MatchFinder>> matchFinderProvider;

    @Inject
    @Named("matchable")
    private Visibility matchableVisibility;

    @Override
    public List<PatientMatch> findMatches(Patient patient)
    {
        List<PatientMatch> matches = new LinkedList<PatientMatch>();

        for (MatchFinder service : this.matchFinderProvider.get()) {
            try {
                matches.addAll(service.findMatches(patient));
            } catch (Exception ex) {
                this.logger.error("Failed to invoke matches finder [{}]",
                    service.getClass().getCanonicalName(), ex);
            }
        }

        this.logger.info("Found {} matches: ", matches.size());
        for (PatientMatch match : matches) {
            this.logger.info(match.toString());
        }

        return matches;
    }
}
