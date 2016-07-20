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
package org.phenotips.matchingnotification.internal.test;

import org.phenotips.matchingnotification.internal.MatchesByPatient;
import org.phenotips.matchingnotification.match.PatientMatch;

import java.util.Collection;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.google.common.base.Objects;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @version $Id$
 */
public class MatchesByPatientTest
{
    private MatchesByPatient mbp;

    private PatientMatch m1_3, m1_4, m1_5server1, m1server3_4, m1server4_4, m1server5_4,
        m2_1, m2_3, m2_3server1,
        m3_1, m3server1_2,
        m4_1, m4_1server3, m4_2,
        m5server1_1, m5server1_2;

    @Before
    public void setup()
    {
        m1_3 = newMatch(1, "P1", null, "P3", null);
        m1_4 = newMatch(2, "P1", null, "P4", null);
        m1_5server1 = newMatch(3, "P1", null, "P5", "server1");
        m1server3_4 = newMatch(4, "P1", "server3", "P4", null);
        m1server4_4 = newMatch(5, "P1", "server4", "P4", null);
        m1server5_4 = newMatch(6, "P1", "server5", "P4", null);

        m2_1 = newMatch(7, "P2", null, "P1", null);
        m2_3 = newMatch(8, "P2", null, "P3", null);
        m2_3server1 = newMatch(9, "P2", null, "P3", "server1");

        m3_1 = newMatch(10, "P3", null, "P1", null);
        m3server1_2 = newMatch(11, "P3", "server1", "P2", null);

        m4_1 = newMatch(12, "P4", null, "P1", null);
        m4_1server3 = newMatch(13, "P4", null, "P1", "server3");
        m4_2 = newMatch(14, "P4", null, "P2", null);

        m5server1_1 = newMatch(15, "P5", "server1", "P1", null);
        m5server1_2 = newMatch(16, "P5", "server1", "P2", null);

        this.mbp = new MatchesByPatient();
        mbp.add(m1_3);
        mbp.add(m1_4);
        mbp.add(m1_5server1);
        mbp.add(m1server3_4);
        mbp.add(m1server4_4);
        mbp.add(m1server5_4);
        mbp.add(m2_1);
        mbp.add(m2_3);
        mbp.add(m2_3server1);
        mbp.add(m3_1);
        mbp.add(m3server1_2);
        mbp.add(m4_1);
        mbp.add(m4_1server3);
        mbp.add(m4_2);
        mbp.add(m5server1_1);
        mbp.add(m5server1_2);
    }

    @Test
    public void testSize()
    {
        Assert.assertEquals(16, this.mbp.size());
    }

    @Test
    public void testPatientIds()
    {
        Collection<String> patientIds = this.mbp.getLocalPatientIds();
        Assert.assertEquals(patientIds.size(), 4);
        Assert.assertTrue(patientIds.contains("P1"));
        Assert.assertTrue(patientIds.contains("P2"));
        Assert.assertTrue(patientIds.contains("P3"));
        Assert.assertTrue(patientIds.contains("P4"));
    }

    @Test
    public void testGetMatches1()
    {
        Collection<PatientMatch> matches = mbp.getMatchesForLocalPatientId("P1", false);
        Assert.assertEquals(7, matches.size());
        Assert.assertTrue(matches.contains(m1_3));
        Assert.assertTrue(matches.contains(m1_4));
        Assert.assertTrue(matches.contains(m1_5server1));
        Assert.assertTrue(matches.contains(m2_1));
        Assert.assertTrue(matches.contains(m3_1));
        Assert.assertTrue(matches.contains(m4_1));
        Assert.assertTrue(matches.contains(m5server1_1));
    }

    @Test
    public void testGetMatches2()
    {
        Collection<PatientMatch> matches = mbp.getMatchesForLocalPatientId("P2", false);
        Assert.assertEquals(6, matches.size());
        Assert.assertTrue(matches.contains(m2_1));
        Assert.assertTrue(matches.contains(m2_3));
        Assert.assertTrue(matches.contains(m2_3server1));
        Assert.assertTrue(matches.contains(m3server1_2));
        Assert.assertTrue(matches.contains(m4_2));
        Assert.assertTrue(matches.contains(m5server1_2));
    }

    @Test
    public void testGetMatches3()
    {
        Collection<PatientMatch> matches = mbp.getMatchesForLocalPatientId("P3", false);
        Assert.assertEquals(3, matches.size());
        Assert.assertTrue(matches.contains(m1_3));
        Assert.assertTrue(matches.contains(m2_3));
        Assert.assertTrue(matches.contains(m3_1));
    }

    @Test
    public void testGetMatches4()
    {
        Collection<PatientMatch> matches = mbp.getMatchesForLocalPatientId("P4", false);
        Assert.assertEquals(7, matches.size());
        Assert.assertTrue(matches.contains(m1_4));
        Assert.assertTrue(matches.contains(m1server3_4));
        Assert.assertTrue(matches.contains(m1server4_4));
        Assert.assertTrue(matches.contains(m1server5_4));
        Assert.assertTrue(matches.contains(m4_1));
        Assert.assertTrue(matches.contains(m4_1server3));
        Assert.assertTrue(matches.contains(m4_2));
    }

    private PatientMatch newMatch(long id, String referencePatientId, String referenceServerId,
        String matchedPatientId, String matchedServerId)
    {
        PatientMatch m = mock(PatientMatch.class);
        when(m.getId()).thenReturn(id);
        when(m.getReferencePatientId()).thenReturn(referencePatientId);
        when(m.getReferenceServerId()).thenReturn(referenceServerId);
        when(m.getMatchedPatientId()).thenReturn(matchedPatientId);
        when(m.getMatchedServerId()).thenReturn(matchedServerId);
        when(m.toString()).thenReturn(Objects.toStringHelper(m).add("id", id).toString());
        return m;
    }
}