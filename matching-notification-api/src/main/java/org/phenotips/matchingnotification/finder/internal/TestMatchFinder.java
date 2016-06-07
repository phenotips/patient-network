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
import org.phenotips.matchingnotification.finder.MatchFinder;
import org.phenotips.matchingnotification.match.PatientMatch;
import org.phenotips.matchingnotification.match.internal.DefaultPatientMatch;

import org.xwiki.component.annotation.Component;

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

    /** */
    public class TestMatchData
    {
        /** */
        public String patientId;
        /** */
        public String matchedPatientId;
        /** */
        public String remoteId;
        /** */
        public Boolean outgoingRequest;
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
        matches.add(this.getMatch1(patient));
        matches.add(this.getMatch2(patient));
        matches.add(this.getMatch3(patient));
        matches.add(this.getMatch4(patient));

        return matches;
    }

    private PatientMatch getMatch1(Patient patient) {
        TestMatchData td1 = new TestMatchData();
        td1.patientId = patient.getId();
        td1.matchedPatientId = "Q0000001";
        td1.remoteId = SERVER1;
        td1.outgoingRequest = true;
        td1.score = 0.3;
        td1.genotypeScore = 0.0;
        td1.phenotypeScore = 0.6;
        td1.email = "aaa@server.com";
        td1.matchedHref = "mailto:matchedaaa@server.com";
        td1.genes = "gene1;gene2";
        td1.matchedGenes = "gene2;gene3";
        return new DefaultPatientMatch(td1);
    }

    private PatientMatch getMatch2(Patient patient) {
        TestMatchData td2 = new TestMatchData();
        td2.patientId = patient.getId();
        td2.matchedPatientId = "Q0000002";
        td2.remoteId = SERVER1;
        td2.outgoingRequest = true;
        td2.score = 0.4;
        td2.genotypeScore = 0.2;
        td2.phenotypeScore = 0.6;
        td2.email = "bbb@server.com";
        td2.matchedHref = "https://decipher.org/patients/123412";
        td2.genes = "gene1";
        td2.matchedGenes = "gene1;gene3";
        return new DefaultPatientMatch(td2);
    }

    private PatientMatch getMatch3(Patient patient) {
        TestMatchData td3 = new TestMatchData();
        td3.patientId = patient.getId();
        td3.matchedPatientId = "Q0000009";
        td3.remoteId = "server3";
        td3.outgoingRequest = true;
        td3.score = 0.1;
        td3.genotypeScore = 0.0;
        td3.phenotypeScore = 0.2;
        td3.email = "ccc@server.com";
        td3.matchedHref = "mailto:matchedccc@server.com";
        td3.genes = "gene3";
        td3.matchedGenes = "";
        return new DefaultPatientMatch(td3);
    }

    private PatientMatch getMatch4(Patient patient) {
        TestMatchData td4 = new TestMatchData();
        td4.patientId = patient.getId();
        td4.matchedPatientId = "Q0000010";
        td4.remoteId = "server2";
        td4.outgoingRequest = true;
        td4.score = 0.7;
        td4.genotypeScore = 0.8;
        td4.phenotypeScore = 0.6;
        td4.email = "ddd@server.com";
        td4.matchedHref = "mailto:matchedddd@server.com";
        td4.genes = "";
        td4.matchedGenes = "";
        return new DefaultPatientMatch(td4);
    }

}
