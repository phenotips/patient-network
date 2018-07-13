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

    private PatientMatch m13;
    private PatientMatch m14;
    private PatientMatch m15server1;
    private PatientMatch m1server34;
    private PatientMatch m1server44;
    private PatientMatch m1server54;
    private PatientMatch m21;
    private PatientMatch m23;
    private PatientMatch m23server1;
    private PatientMatch m31;
    private PatientMatch m3server12;
    private PatientMatch m41;
    private PatientMatch m41server3;
    private PatientMatch m42;
    private PatientMatch m5server11;
    private PatientMatch m5server12;

    @Before
    public void setup()
    {
        this.m13 = newMatch(1, "P1", null, "P3", null);
        this.m14 = newMatch(2, "P1", null, "P4", null);
        this.m15server1 = newMatch(3, "P1", null, "P5", "server1");
        this.m1server34 = newMatch(4, "P1", "server3", "P4", null);
        this.m1server44 = newMatch(5, "P1", "server4", "P4", null);
        this.m1server54 = newMatch(6, "P1", "server5", "P4", null);

        this.m21 = newMatch(7, "P2", null, "P1", null);
        this.m23 = newMatch(8, "P2", null, "P3", null);
        this.m23server1 = newMatch(9, "P2", null, "P3", "server1");

        this.m31 = newMatch(10, "P3", null, "P1", null);
        this.m3server12 = newMatch(11, "P3", "server1", "P2", null);

        this.m41 = newMatch(12, "P4", null, "P1", null);
        this.m41server3 = newMatch(13, "P4", null, "P1", "server3");
        this.m42 = newMatch(14, "P4", null, "P2", null);

        this.m5server11 = newMatch(15, "P5", "server1", "P1", null);
        this.m5server12 = newMatch(16, "P5", "server1", "P2", null);

        this.mbp = new MatchesByPatient();
        this.mbp.add(this.m13);
        this.mbp.add(this.m14);
        this.mbp.add(this.m15server1);
        this.mbp.add(this.m1server34);
        this.mbp.add(this.m1server44);
        this.mbp.add(this.m1server54);
        this.mbp.add(this.m21);
        this.mbp.add(this.m23);
        this.mbp.add(this.m23server1);
        this.mbp.add(this.m31);
        this.mbp.add(this.m3server12);
        this.mbp.add(this.m41);
        this.mbp.add(this.m41server3);
        this.mbp.add(this.m42);
        this.mbp.add(this.m5server11);
        this.mbp.add(this.m5server12);
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
        Assert.assertTrue(matches.contains(this.m13));
        Assert.assertTrue(matches.contains(this.m14));
        Assert.assertTrue(matches.contains(this.m15server1));
        Assert.assertTrue(matches.contains(this.m21));
        Assert.assertTrue(matches.contains(this.m31));
        Assert.assertTrue(matches.contains(this.m41));
        Assert.assertTrue(matches.contains(this.m5server11));
    }

    @Test
    public void testGetMatches2()
    {
        Collection<PatientMatch> matches = this.mbp.getMatchesForLocalPatientId("P2", false);
        Assert.assertEquals(6, matches.size());
        Assert.assertTrue(matches.contains(this.m21));
        Assert.assertTrue(matches.contains(this.m23));
        Assert.assertTrue(matches.contains(this.m23server1));
        Assert.assertTrue(matches.contains(this.m3server12));
        Assert.assertTrue(matches.contains(this.m42));
        Assert.assertTrue(matches.contains(this.m5server12));
    }

    @Test
    public void testGetMatches3()
    {
        Collection<PatientMatch> matches = this.mbp.getMatchesForLocalPatientId("P3", false);
        Assert.assertEquals(3, matches.size());
        Assert.assertTrue(matches.contains(this.m13));
        Assert.assertTrue(matches.contains(this.m23));
        Assert.assertTrue(matches.contains(this.m31));
    }

    @Test
    public void testGetMatches4()
    {
        Collection<PatientMatch> matches = this.mbp.getMatchesForLocalPatientId("P4", false);
        Assert.assertEquals(7, matches.size());
        Assert.assertTrue(matches.contains(this.m14));
        Assert.assertTrue(matches.contains(this.m1server34));
        Assert.assertTrue(matches.contains(this.m1server44));
        Assert.assertTrue(matches.contains(this.m1server54));
        Assert.assertTrue(matches.contains(this.m41));
        Assert.assertTrue(matches.contains(this.m41server3));
        Assert.assertTrue(matches.contains(this.m42));
    }

    @Test
    public void testContainsAndRemove()
    {
        Assert.assertEquals(this.mbp.size(), 16);
        Assert.assertTrue(this.mbp.contains(this.m14));
        this.mbp.remove(this.m14);
        Assert.assertFalse(this.mbp.contains(this.m14));
        Assert.assertEquals(this.mbp.size(), 15);

        Assert.assertTrue(this.mbp.contains(this.m1server34));
        this.mbp.remove(this.m1server34);
        Assert.assertFalse(this.mbp.contains(this.m1server34));
        Assert.assertEquals(this.mbp.size(), 14);

        Assert.assertTrue(this.mbp.contains(this.m23server1));
        this.mbp.remove(this.m23server1);
        Assert.assertFalse(this.mbp.contains(this.m23server1));
        Assert.assertEquals(this.mbp.size(), 13);
    }

    @Test
    public void testAdd()
    {
        MatchesByPatient mbp2 = new MatchesByPatient();
        Assert.assertEquals(mbp2.size(), 0);
        Assert.assertTrue(mbp2.add(this.m13));
        Assert.assertEquals(mbp2.size(), 1);
        Assert.assertFalse(mbp2.add(this.m13));
        Assert.assertEquals(mbp2.size(), 1);
        Assert.assertFalse(mbp2.add(this.m13));
        Assert.assertEquals(mbp2.size(), 1);

        Assert.assertTrue(mbp2.add(this.m23));
        Assert.assertEquals(mbp2.size(), 2);
        Assert.assertFalse(mbp2.add(this.m23));
        Assert.assertEquals(mbp2.size(), 2);
        Assert.assertFalse(mbp2.add(this.m23));
        Assert.assertEquals(mbp2.size(), 2);

        Assert.assertFalse(mbp2.add(this.m13));
        Assert.assertEquals(mbp2.size(), 2);

        Assert.assertTrue(mbp2.add(this.m15server1));
        Assert.assertEquals(mbp2.size(), 3);
        Assert.assertFalse(mbp2.add(this.m15server1));
        Assert.assertEquals(mbp2.size(), 3);
        Assert.assertFalse(mbp2.add(this.m15server1));
        Assert.assertEquals(mbp2.size(), 3);

        Assert.assertTrue(mbp2.add(this.m1server44));
        Assert.assertEquals(mbp2.size(), 4);
        Assert.assertFalse(mbp2.add(this.m1server44));
        Assert.assertEquals(mbp2.size(), 4);
        Assert.assertFalse(mbp2.add(this.m1server44));
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
        testIterator(new PatientMatch[] { this.m13 });
    }

    @Test
    public void testIterator3()
    {
        testIterator(new PatientMatch[] { this.m13, this.m14, this.m15server1, this.m1server34, this.m1server44,
            this.m1server54 });
    }

    @Test
    public void testIterator4()
    {
        testIterator(new PatientMatch[] { this.m13, this.m14, this.m15server1, this.m1server34, this.m1server44,
            this.m1server54, this.m21, this.m23,
            this.m23server1, this.m31, this.m3server12, this.m41, this.m41server3, this.m42, this.m5server11,
            this.m5server12 });
    }

    @Test
    public void testIterator5()
    {
        testFilteredIterator(new PatientMatch[] {});
    }

    @Test
    public void testIterator6()
    {
        testFilteredIterator(new PatientMatch[] { this.m13 });
    }

    @Test
    public void testIterator65()
    {
        PatientMatch n12 = newMatch(1, "P1", null, "P2", null);
        PatientMatch n13 = newMatch(2, "P1", null, "P3", null);
        PatientMatch n21 = newMatch(3, "P2", null, "P1", null);
        PatientMatch n23 = newMatch(4, "P2", null, "P3", null);
        PatientMatch n31 = newMatch(5, "P3", null, "P1", null);
        PatientMatch n32 = newMatch(6, "P3", null, "P2", null);

        when(n12.isEquivalent(n12)).thenReturn(false);
        when(n12.isEquivalent(n13)).thenReturn(false);
        when(n12.isEquivalent(n21)).thenReturn(true);
        when(n12.isEquivalent(n23)).thenReturn(false);
        when(n12.isEquivalent(n31)).thenReturn(false);
        when(n12.isEquivalent(n32)).thenReturn(false);

        when(n13.isEquivalent(n12)).thenReturn(false);
        when(n13.isEquivalent(n13)).thenReturn(false);
        when(n13.isEquivalent(n21)).thenReturn(false);
        when(n13.isEquivalent(n23)).thenReturn(false);
        when(n13.isEquivalent(n31)).thenReturn(true);
        when(n13.isEquivalent(n32)).thenReturn(false);

        when(n21.isEquivalent(n12)).thenReturn(true);
        when(n21.isEquivalent(n13)).thenReturn(false);
        when(n21.isEquivalent(n21)).thenReturn(false);
        when(n21.isEquivalent(n23)).thenReturn(false);
        when(n21.isEquivalent(n31)).thenReturn(false);
        when(n21.isEquivalent(n32)).thenReturn(false);

        when(n23.isEquivalent(n12)).thenReturn(false);
        when(n23.isEquivalent(n13)).thenReturn(false);
        when(n23.isEquivalent(n21)).thenReturn(false);
        when(n23.isEquivalent(n23)).thenReturn(false);
        when(n23.isEquivalent(n31)).thenReturn(false);
        when(n23.isEquivalent(n32)).thenReturn(true);

        when(n31.isEquivalent(n12)).thenReturn(false);
        when(n31.isEquivalent(n13)).thenReturn(true);
        when(n31.isEquivalent(n21)).thenReturn(false);
        when(n31.isEquivalent(n23)).thenReturn(false);
        when(n31.isEquivalent(n31)).thenReturn(false);
        when(n31.isEquivalent(n32)).thenReturn(false);

        when(n32.isEquivalent(n12)).thenReturn(false);
        when(n32.isEquivalent(n13)).thenReturn(false);
        when(n32.isEquivalent(n21)).thenReturn(false);
        when(n32.isEquivalent(n23)).thenReturn(true);
        when(n32.isEquivalent(n31)).thenReturn(false);
        when(n32.isEquivalent(n32)).thenReturn(false);

        PatientMatch[] toAdd = new PatientMatch[] { n12, n13, n21, n23, n31, n32 };
        testFilteredIterator(toAdd);
    }

    @Test
    public void testRemoveFromIterator1()
    {
        Iterator<PatientMatch> iter = this.mbp.iterator();
        while (iter.hasNext() && !(iter.next().getId() == this.m14.getId())) {
            // Just skipping to the right position
        }
        iter.remove();
        Assert.assertFalse(this.mbp.contains(this.m14));
        Assert.assertTrue(this.mbp.contains(this.m41));
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
        mbp2.add(this.m13);
        Collection<PatientMatch> matches1 = mbp2.getMatchesForLocalPatientId("P3", false);
        Assert.assertTrue(matches1.contains(this.m13));

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
