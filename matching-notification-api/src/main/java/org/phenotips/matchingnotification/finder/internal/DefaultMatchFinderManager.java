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

import org.xwiki.component.annotation.Component;
import org.xwiki.query.Query;
import org.xwiki.query.QueryManager;

import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @version $Id$
 */
@Component
@Singleton
public class DefaultMatchFinderManager implements MatchFinderManager
{
    private Logger logger = LoggerFactory.getLogger(DefaultMatchFinderManager.class);

    @Inject
    private Provider<List<MatchFinder>> matchFinderProvider;

    @Inject
    @Named("matchable")
    private Visibility matchableVisibility;

    @Inject
    private QueryManager qm;

    @Override
    public void findMatchesForAllPatients(Set<String> serverIds, boolean onlyCheckPatientsUpdatedAfterLastRun)
    {
        this.logger.error("Finding matches for all patients for servers [{}] (only updated patients: [{}])",
                serverIds.toString(), onlyCheckPatientsUpdatedAfterLastRun);

        List<String> patients = this.getPatientsList();

        for (MatchFinder matchFinder : this.matchFinderProvider.get()) {
            this.logger.debug("Starting matchFinder with priority = [{}]", matchFinder.getPriority());
            matchFinder.findMatches(patients, serverIds, onlyCheckPatientsUpdatedAfterLastRun);
        }
    }

    @Override
    public Response findMatchesForPatient(Patient patient, String serverId)
    {
        this.logger.error("Finding matches for patient [{}] for server [{}]", patient.getId(), serverId);

        for (MatchFinder matchFinder : this.matchFinderProvider.get()) {
            Response response = matchFinder.findMatches(patient, serverId);
            if (response == null) {
                continue;
            }
            return response;
        }
        return null;
    }

    /**
     * Returns a list of patient Ids.
     */
    private List<String> getPatientsList()
    {
        List<String> patients = new LinkedList<>();

        try {
            Query q = this.qm.createQuery(
                "select doc.name "
                    + "from Document doc, "
                    + "doc.object(PhenoTips.PatientClass) as patient "
                    + "where patient.identifier is not null order by patient.identifier desc",
                Query.XWQL);
            patients = q.execute();
        } catch (Exception e) {
            this.logger.error("Error retrieving a list of patients for matching: {}", e);
        }

        return patients;
    }
}
