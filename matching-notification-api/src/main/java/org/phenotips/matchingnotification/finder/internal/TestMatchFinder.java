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

import org.phenotips.components.ComponentManagerRegistry;
import org.phenotips.data.Patient;
import org.phenotips.matchingnotification.finder.MatchFinder;
import org.phenotips.matchingnotification.match.PatientMatch;
import org.phenotips.matchingnotification.match.internal.DefaultPatientMatch;
import org.phenotips.matchingnotification.match.internal.DefaultPatientMatchUtils;
import org.phenotips.matchingnotification.match.internal.DefaultPhenotypesMap;

import org.xwiki.component.annotation.Component;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.component.manager.ComponentManager;

import java.util.LinkedList;
import java.util.List;

import javax.inject.Named;
import javax.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @version $Id$
 */
@Component
@Named("test")
@Singleton
public class TestMatchFinder implements MatchFinder
{
    private static final String SERVER1 = "SERVER1";
    private static final String SERVER2 = "SERVER2";

    private static final String Q1 = "Q0000001";
    private static final String Q1_EMAIL = "mailto:q1@server1.com";
    private static final String Q1_GENES = "q1_gene1;q1_gene2";

    private static final String Q2 = "Q0000002";
    private static final String Q2_EMAIL = "mailto:q2@server1.com";
    private static final String Q2_GENES = "q2_gene1;q2_gene2";

    private static final DefaultPatientMatchUtils UTILS;

    static {
        DefaultPatientMatchUtils utils = null;
        try {
            ComponentManager ccm = ComponentManagerRegistry.getContextComponentManager();
            utils = ccm.getInstance(DefaultPatientMatchUtils.class);
        } catch (ComponentLookupException e) {
        }
        UTILS = utils;
    }

    /** */
    public class TestMatchData
    {
        /** */
        public String referencePatientId;
        /** */
        public String referenceServerId;
        /** */
        public String matchedPatientId;
        /** */
        public String matchedServerId;
        /** */
        public Double score;
        /** */
        public Double genotypeScore;
        /** */
        public Double phenotypeScore;
        /** */
        public String email;
        /** */
        public String genes;
        /** */
        public String matchedHref;
        /** */
        public String matchedGenes;
        /** */
        public String phenotypes;
        /** */
        public String matchedPhenotypes;
    }

    private Logger logger = LoggerFactory.getLogger(TestMatchFinder.class);

    @Override
    public int getPriority() {
        return 0;
    }

    @Override
    public List<PatientMatch> findMatches(Patient patient)
    {
        this.logger.debug("Finding test matches for patient {}.", patient.getId());

        List<PatientMatch> matches = new LinkedList<>();
        if (patient.getId().equals("P0000001")) {
            matches.add(this.getMatchedQ1(patient));
            matches.add(this.getReferenceQ1(patient));
        }
        if (patient.getId().equals("P0000002")) {
            matches.add(this.getMatchedQ1(patient));
        }
        if (patient.getId().equals("P0000003")) {
            matches.add(this.getReferenceQ1(patient));
        }

        return matches;
    }

    private PatientMatch getMatchedQ1(Patient patient) {
        TestMatchData td1 = new TestMatchData();
        td1.referencePatientId = patient.getId();
        td1.referenceServerId = null;
        td1.email = UTILS.getOwnerEmail(patient);
        td1.genes = UTILS.setToString(UTILS.getGenes(patient));
        td1.phenotypes = (new DefaultPhenotypesMap(patient)).toString();

        td1.matchedPatientId = Q1;
        td1.matchedServerId = SERVER1;
        td1.matchedHref = Q1_EMAIL;
        td1.matchedGenes = Q1_GENES;
        td1.matchedPhenotypes = (new DefaultPhenotypesMap()).toString();

        td1.score = 0.3;
        td1.genotypeScore = 0.0;
        td1.phenotypeScore = 0.6;

        return new DefaultPatientMatch(td1);
    }

    private PatientMatch getReferenceQ1(Patient patient) {
        TestMatchData td1 = new TestMatchData();
        td1.matchedPatientId = patient.getId();
        td1.matchedServerId = null;
        td1.matchedHref = UTILS.getOwnerEmail(patient);
        td1.matchedGenes = UTILS.setToString(UTILS.getGenes(patient));
        td1.matchedPhenotypes = (new DefaultPhenotypesMap(patient)).toString();

        td1.referencePatientId = Q1;
        td1.referenceServerId = SERVER1;
        td1.email = Q1_EMAIL;
        td1.genes = Q1_GENES;
        td1.phenotypes = (new DefaultPhenotypesMap()).toString();

        td1.score = 0.3;
        td1.genotypeScore = 0.0;
        td1.phenotypeScore = 0.6;

        return new DefaultPatientMatch(td1);
    }
}
