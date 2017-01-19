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

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

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
        this.m1_3 = newMatch(1, "P1", null, "P3", null);
        this.m1_4 = newMatch(2, "P1", null, "P4", null);
        this.m1_5server1 = newMatch(3, "P1", null, "P5", "server1");
        this.m1server3_4 = newMatch(4, "P1", "server3", "P4", null);
        this.m1server4_4 = newMatch(5, "P1", "server4", "P4", null);
        this.m1server5_4 = newMatch(6, "P1", "server5", "P4", null);

        this.m2_1 = newMatch(7, "P2", null, "P1", null);
        this.m2_3 = newMatch(8, "P2", null, "P3", null);
        this.m2_3server1 = newMatch(9, "P2", null, "P3", "server1");

        this.m3_1 = newMatch(10, "P3", null, "P1", null);
        this.m3server1_2 = newMatch(11, "P3", "server1", "P2", null);

        this.m4_1 = newMatch(12, "P4", null, "P1", null);
        this.m4_1server3 = newMatch(13, "P4", null, "P1", "server3");
        this.m4_2 = newMatch(14, "P4", null, "P2", null);

        this.m5server1_1 = newMatch(15, "P5", "server1", "P1", null);
        this.m5server1_2 = newMatch(16, "P5", "server1", "P2", null);

        this.mbp = new MatchesByPatient();
        this.mbp.add(this.m1_3);
        this.mbp.add(this.m1_4);
        this.mbp.add(this.m1_5server1);
        this.mbp.add(this.m1server3_4);
        this.mbp.add(this.m1server4_4);
        this.mbp.add(this.m1server5_4);
        this.mbp.add(this.m2_1);
        this.mbp.add(this.m2_3);
        this.mbp.add(this.m2_3server1);
        this.mbp.add(this.m3_1);
        this.mbp.add(this.m3server1_2);
        this.mbp.add(this.m4_1);
        this.mbp.add(this.m4_1server3);
        this.mbp.add(this.m4_2);
        this.mbp.add(this.m5server1_1);
        this.mbp.add(this.m5server1_2);
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
        Collection<PatientMatch> matches = this.mbp.getMatchesForLocalPatientId("P1", false);
        Assert.assertEquals(7, matches.size());
        Assert.assertTrue(matches.contains(this.m1_3));
        Assert.assertTrue(matches.contains(this.m1_4));
        Assert.assertTrue(matches.contains(this.m1_5server1));
        Assert.assertTrue(matches.contains(this.m2_1));
        Assert.assertTrue(matches.contains(this.m3_1));
        Assert.assertTrue(matches.contains(this.m4_1));
        Assert.assertTrue(matches.contains(this.m5server1_1));
    }

    @Test
    public void testGetMatches2()
    {
        Collection<PatientMatch> matches = this.mbp.getMatchesForLocalPatientId("P2", false);
        Assert.assertEquals(6, matches.size());
        Assert.assertTrue(matches.contains(this.m2_1));
        Assert.assertTrue(matches.contains(this.m2_3));
        Assert.assertTrue(matches.contains(this.m2_3server1));
        Assert.assertTrue(matches.contains(this.m3server1_2));
        Assert.assertTrue(matches.contains(this.m4_2));
        Assert.assertTrue(matches.contains(this.m5server1_2));
    }

    @Test
    public void testGetMatches3()
    {
        Collection<PatientMatch> matches = this.mbp.getMatchesForLocalPatientId("P3", false);
        Assert.assertEquals(3, matches.size());
        Assert.assertTrue(matches.contains(this.m1_3));
        Assert.assertTrue(matches.contains(this.m2_3));
        Assert.assertTrue(matches.contains(this.m3_1));
    }

    @Test
    public void testGetMatches4()
    {
        Collection<PatientMatch> matches = this.mbp.getMatchesForLocalPatientId("P4", false);
        Assert.assertEquals(7, matches.size());
        Assert.assertTrue(matches.contains(this.m1_4));
        Assert.assertTrue(matches.contains(this.m1server3_4));
        Assert.assertTrue(matches.contains(this.m1server4_4));
        Assert.assertTrue(matches.contains(this.m1server5_4));
        Assert.assertTrue(matches.contains(this.m4_1));
        Assert.assertTrue(matches.contains(this.m4_1server3));
        Assert.assertTrue(matches.contains(this.m4_2));
    }

    @Test
    public void testContainsAndRemove()
    {
        Assert.assertEquals(this.mbp.size(), 16);
        Assert.assertTrue(this.mbp.contains(this.m1_4));
        this.mbp.remove(this.m1_4);
        Assert.assertFalse(this.mbp.contains(this.m1_4));
        Assert.assertEquals(this.mbp.size(), 15);

        Assert.assertTrue(this.mbp.contains(this.m1server3_4));
        this.mbp.remove(this.m1server3_4);
        Assert.assertFalse(this.mbp.contains(this.m1server3_4));
        Assert.assertEquals(this.mbp.size(), 14);

        Assert.assertTrue(this.mbp.contains(this.m2_3server1));
        this.mbp.remove(this.m2_3server1);
        Assert.assertFalse(this.mbp.contains(this.m2_3server1));
        Assert.assertEquals(this.mbp.size(), 13);
    }

    @Test
    public void testAdd()
    {
        MatchesByPatient mbp2 = new MatchesByPatient();
        Assert.assertEquals(mbp2.size(), 0);
        Assert.assertTrue(mbp2.add(this.m1_3));
        Assert.assertEquals(mbp2.size(), 1);
        Assert.assertFalse(mbp2.add(this.m1_3));
        Assert.assertEquals(mbp2.size(), 1);
        Assert.assertFalse(mbp2.add(this.m1_3));
        Assert.assertEquals(mbp2.size(), 1);

        Assert.assertTrue(mbp2.add(this.m2_3));
        Assert.assertEquals(mbp2.size(), 2);
        Assert.assertFalse(mbp2.add(this.m2_3));
        Assert.assertEquals(mbp2.size(), 2);
        Assert.assertFalse(mbp2.add(this.m2_3));
        Assert.assertEquals(mbp2.size(), 2);

        Assert.assertFalse(mbp2.add(this.m1_3));
        Assert.assertEquals(mbp2.size(), 2);

        Assert.assertTrue(mbp2.add(this.m1_5server1));
        Assert.assertEquals(mbp2.size(), 3);
        Assert.assertFalse(mbp2.add(this.m1_5server1));
        Assert.assertEquals(mbp2.size(), 3);
        Assert.assertFalse(mbp2.add(this.m1_5server1));
        Assert.assertEquals(mbp2.size(), 3);

        Assert.assertTrue(mbp2.add(this.m1server4_4));
        Assert.assertEquals(mbp2.size(), 4);
        Assert.assertFalse(mbp2.add(this.m1server4_4));
        Assert.assertEquals(mbp2.size(), 4);
        Assert.assertFalse(mbp2.add(this.m1server4_4));
        Assert.assertEquals(mbp2.size(), 4);
    }

    @Test
    public void testIterator1()
    {
        testIterator(new PatientMatch[] {});
    }

    @Test
    public void testIterator2()
    {
        testIterator(new PatientMatch[] { this.m1_3 });
    }

    @Test
    public void testIterator3()
    {
        testIterator(new PatientMatch[] { this.m1_3, this.m1_4, this.m1_5server1, this.m1server3_4, this.m1server4_4,
            this.m1server5_4 });
    }

    @Test
    public void testIterator4()
    {
        testIterator(new PatientMatch[] { this.m1_3, this.m1_4, this.m1_5server1, this.m1server3_4, this.m1server4_4,
            this.m1server5_4, this.m2_1, this.m2_3,
            this.m2_3server1, this.m3_1, this.m3server1_2, this.m4_1, this.m4_1server3, this.m4_2, this.m5server1_1,
            this.m5server1_2 });
    }

    @Test
    public void testIterator5()
    {
        testFilteredIterator(new PatientMatch[] {});
    }

    @Test
    public void testIterator6()
    {
        testFilteredIterator(new PatientMatch[] { this.m1_3 });
    }

    @Test
    public void testIterator6_5()
    {
        PatientMatch n1_2 = newMatch(1, "P1", null, "P2", null);
        PatientMatch n1_3 = newMatch(2, "P1", null, "P3", null);
        PatientMatch n2_1 = newMatch(3, "P2", null, "P1", null);
        PatientMatch n2_3 = newMatch(4, "P2", null, "P3", null);
        PatientMatch n3_1 = newMatch(5, "P3", null, "P1", null);
        PatientMatch n3_2 = newMatch(6, "P3", null, "P2", null);

        when(n1_2.isEquivalent(n1_2)).thenReturn(false);
        when(n1_2.isEquivalent(n1_3)).thenReturn(false);
        when(n1_2.isEquivalent(n2_1)).thenReturn(true);
        when(n1_2.isEquivalent(n2_3)).thenReturn(false);
        when(n1_2.isEquivalent(n3_1)).thenReturn(false);
        when(n1_2.isEquivalent(n3_2)).thenReturn(false);

        when(n1_3.isEquivalent(n1_2)).thenReturn(false);
        when(n1_3.isEquivalent(n1_3)).thenReturn(false);
        when(n1_3.isEquivalent(n2_1)).thenReturn(false);
        when(n1_3.isEquivalent(n2_3)).thenReturn(false);
        when(n1_3.isEquivalent(n3_1)).thenReturn(true);
        when(n1_3.isEquivalent(n3_2)).thenReturn(false);

        when(n2_1.isEquivalent(n1_2)).thenReturn(true);
        when(n2_1.isEquivalent(n1_3)).thenReturn(false);
        when(n2_1.isEquivalent(n2_1)).thenReturn(false);
        when(n2_1.isEquivalent(n2_3)).thenReturn(false);
        when(n2_1.isEquivalent(n3_1)).thenReturn(false);
        when(n2_1.isEquivalent(n3_2)).thenReturn(false);

        when(n2_3.isEquivalent(n1_2)).thenReturn(false);
        when(n2_3.isEquivalent(n1_3)).thenReturn(false);
        when(n2_3.isEquivalent(n2_1)).thenReturn(false);
        when(n2_3.isEquivalent(n2_3)).thenReturn(false);
        when(n2_3.isEquivalent(n3_1)).thenReturn(false);
        when(n2_3.isEquivalent(n3_2)).thenReturn(true);

        when(n3_1.isEquivalent(n1_2)).thenReturn(false);
        when(n3_1.isEquivalent(n1_3)).thenReturn(true);
        when(n3_1.isEquivalent(n2_1)).thenReturn(false);
        when(n3_1.isEquivalent(n2_3)).thenReturn(false);
        when(n3_1.isEquivalent(n3_1)).thenReturn(false);
        when(n3_1.isEquivalent(n3_2)).thenReturn(false);

        when(n3_2.isEquivalent(n1_2)).thenReturn(false);
        when(n3_2.isEquivalent(n1_3)).thenReturn(false);
        when(n3_2.isEquivalent(n2_1)).thenReturn(false);
        when(n3_2.isEquivalent(n2_3)).thenReturn(true);
        when(n3_2.isEquivalent(n3_1)).thenReturn(false);
        when(n3_2.isEquivalent(n3_2)).thenReturn(false);

        PatientMatch[] toAdd = new PatientMatch[] { n1_2, n1_3, n2_1, n2_3, n3_1, n3_2 };
        testFilteredIterator(toAdd);
    }

    @Test
    public void testRemoveFromIterator1()
    {
        Iterator<PatientMatch> iter = this.mbp.iterator();
        while (iter.hasNext() && !(iter.next().getId() == this.m1_4.getId())) {
            // Just skipping to the right position
        }
        iter.remove();
        Assert.assertFalse(this.mbp.contains(this.m1_4));
        Assert.assertTrue(this.mbp.contains(this.m4_1));
        Assert.assertEquals(this.mbp.size(), 15);

        while (iter.hasNext()) {
            iter.next();
        }
        iter.remove();
        Assert.assertFalse(iter.hasNext());
        Assert.assertEquals(this.mbp.size(), 14);
    }

    @Test
    public void testRemoveFromIterator2()
    {
        MatchesByPatient mbp2 = new MatchesByPatient();
        mbp2.add(this.m1_3);
        Collection<PatientMatch> matches1 = mbp2.getMatchesForLocalPatientId("P3", false);
        Assert.assertTrue(matches1.contains(this.m1_3));

        Iterator<PatientMatch> iter = mbp2.iterator();
        Assert.assertTrue(iter.hasNext());
        iter.next();
        Assert.assertFalse(iter.hasNext());

        iter.remove();
        Collection<PatientMatch> matches2 = mbp2.getMatchesForLocalPatientId("P3", false);
        Assert.assertTrue(matches2.isEmpty());
    }

    private void testIterator(PatientMatch[] toAdd)
    {
        Set<PatientMatch> toAddSet = new HashSet<>(Arrays.asList(toAdd));
        MatchesByPatient mbp2 = new MatchesByPatient(toAddSet);
        Assert.assertEquals(mbp2.size(), toAdd.length);

        Iterator<PatientMatch> iterator = mbp2.iterator(false);

        Set<PatientMatch> newSet = new HashSet<>();
        for (int i = 0; i < toAddSet.size(); i++) {
            Assert.assertTrue(iterator.hasNext());
            newSet.add(iterator.next());
        }
        Assert.assertFalse(iterator.hasNext());

        Assert.assertEquals(toAddSet, newSet);
    }

    private void testFilteredIterator(PatientMatch[] toAdd)
    {
        Set<PatientMatch> toAddSet = new HashSet<>(Arrays.asList(toAdd));
        MatchesByPatient mbp2 = new MatchesByPatient(toAddSet);
        Assert.assertEquals(mbp2.size(), toAdd.length);

        Iterator<PatientMatch> iterator = mbp2.iterator(true);

        Set<PatientMatch> newSet = new HashSet<>();
        while (iterator.hasNext()) {
            newSet.add(iterator.next());
        }

        // Check that every item that was not returned by iterator, has an equivalent in MatchesByPatient

        Set<PatientMatch> notInNewSet = new HashSet<>(toAddSet);
        notInNewSet.removeAll(newSet);

        for (PatientMatch match : notInNewSet) {
            Assert.assertNotNull(mbp2.getEquivalentMatch(match));
        }
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
