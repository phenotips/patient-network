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
package org.phenotips.matchingnotification.internal;

import org.phenotips.matchingnotification.match.PatientMatch;

import java.util.AbstractCollection;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * @version $Id$
 */
public class MatchesByPatient extends AbstractCollection<PatientMatch>
{
    /*
     * Main key of internalMap is id of local patient in match. Secondary key is id of the other patient.
     * If both patients are local, a match will be inserted twice.
     * Examples with match expressed as
     *    (reference patient id, server patient id, matched patient id, matched server id)
     *
     * 1. Adding m1=("p1", -, "p2", -). m1 will be found in both sets
     *    internalMap.get("p1").get("p2") and
     *    internalMap.get("p2").get("p1").
     * 2. Adding m2=("p2", -, "p1", -) after m1 was added.
     *    internalMap.get("p1").get("p2") returns set s1 containing m1 and m2.
     *    internalMap.get("p2").get("p1") returns set s2 containing m1 and m2.
     * 3. Adding m3=("p3","server1","p1",-). m3 will be found in the set
     *    internalMap.get("p1").get("p3") (and no other sets).
     * 4. Adding m4=("p1",-,"p3","server1"). Match will be found in the same set as 3 and not other sets.
     * 5. Adding m5=("p1",-,"p2","server1"). Match will be found in
     *    internalMap.get("p1").get("p2") in addition to m1 and m2.
     *
     * The set where matches are saved is ordered, and matches where the local patient is the matched patient come
     * before matches where it is the reference. This way, when creating a return value collection for
     * getMatchesForLocalPatientId(id, true), matches where id is matched are preferred. That is, if m1 and m2 are
     * equivalent and m1.getMatchedPatientId()==id then m1 is returned and m2 is not.
     */
    private Map<String, Map<String, Set<PatientMatch>>> internalMap;

    private int size;

    /**
     * @version $Id$
     */
    public class MatchesByPatientIterator implements Iterator<PatientMatch>
    {
        private MatchesByPatient mbp;

        private Iterator<Map<String, Set<PatientMatch>>> primaryKeyIterator;
        private Iterator<Set<PatientMatch>> secondaryKeyIterator;
        private Iterator<PatientMatch> setIterator;

        private PatientMatch returnNext;
        private PatientMatch returnedLast;

        private Set<PatientMatch> returnedSet;

        private boolean skipEquivalents;

        /**
         * @param skipEquivalents skip any match if its equivalent was retrieved
         * @param mbp MatchesByPatient object that this iterator iterates over
         */
        public MatchesByPatientIterator(MatchesByPatient mbp, boolean skipEquivalents)
        {
            this.mbp = mbp;
            this.skipEquivalents = skipEquivalents;
            this.returnedSet = new HashSet<>();
            this.initialize();
        }

        private void initialize()
        {
            this.primaryKeyIterator = this.mbp.internalMap.values().iterator();
            if (this.primaryKeyIterator.hasNext()) {
                this.secondaryKeyIterator = this.primaryKeyIterator.next().values().iterator();
                if (this.secondaryKeyIterator.hasNext()) {
                    this.setIterator = this.secondaryKeyIterator.next().iterator();
                    if (this.setIterator.hasNext()) {
                        this.returnNext = this.setIterator.next();
                    }
                }
            }
        }

        @Override
        public boolean hasNext()
        {
            return this.returnNext != null;
        }

        @Override
        public PatientMatch next()
        {
            this.returnedLast = this.returnNext;

            this.returnedSet.add(returnedLast);
            boolean keepLooking = true;
            while (keepLooking) {
                this.moveToNext();

                keepLooking = false;
                if (this.returnNext != null) {
                    if (this.returnedSet.contains(this.returnNext)) {
                        keepLooking = true;
                    }
                    if (this.skipEquivalents && containsEquivalent(returnedSet, this.returnNext)) {
                        keepLooking = true;
                    }
                }
            }

            return returnedLast;
        }

        private void moveToNext()
        {
            this.returnNext = null;

            if (this.setReturnNext()) {
                return;
            }

            this.moveSecondary();
            if (this.setReturnNext()) {
                return;
            }

            this.movePrimary();
            this.moveSecondary();
            this.setReturnNext();

            // if this.setReturnNext() returned false, it's the end of the collection.
        }

        private void movePrimary()
        {
            while (!this.secondaryKeyIterator.hasNext() && this.primaryKeyIterator.hasNext()) {
                this.secondaryKeyIterator = this.primaryKeyIterator.next().values().iterator();
            }
        }

        private void moveSecondary()
        {
            while (!this.setIterator.hasNext() && this.secondaryKeyIterator.hasNext()) {
                this.setIterator = this.secondaryKeyIterator.next().iterator();
            }
        }

        // Return value - true if returned next set. If false, continue looking for next item.
        private boolean setReturnNext() {
            if (this.setIterator != null && this.setIterator.hasNext()) {
                this.returnNext = this.setIterator.next();
                return true;
            }
            return false;
        }

        @Override
        public void remove()
        {
            // Note: this.returnNext cannot be the same as this.returnedLast because of the use of this.returnedSet
            MatchesByPatient.this.remove(this.returnedLast);
        }

    }

    private class PatientMatchSetComparator implements Comparator<PatientMatch>
    {
        private String localPatientId;

        PatientMatchSetComparator(String localPatientId) {
            this.localPatientId = localPatientId;
        }

        @Override
        // Matches where key patient (first key in internalMap) is matched come before matches where it's a reference.
        // In all other cases, order is not important (provided that m1!=m2 <==> compare(m1,m2)!=0).
        public int compare(PatientMatch m1, PatientMatch m2) {
            boolean m1ref = m1.isReference(this.localPatientId, null);
            boolean m2ref = m2.isReference(this.localPatientId, null);
            if (m1ref && !m2ref) {
                return +1;
            } else if (!m1ref && m2ref) {
                return -1;
            } else {
                return Integer.compare(m1.hashCode(), m2.hashCode());
            }
        }
    }

    /**
     * Creates an empty collection.
     */
    public MatchesByPatient()
    {
        this.initialize();
    }

    /**
     * Creates a collection with all given matches.
     *
     * @param matches a collection of PatientMatch-es
     */
    public MatchesByPatient(Collection<PatientMatch> matches)
    {
        this.initialize();
        this.addAll(matches);
    }

    private void initialize()
    {
        this.internalMap = new TreeMap<>();
        this.size = 0;
    }

    /**
     * @return number of matches in collection
     */
    @Override
    public int size()
    {
        return this.size;
    }


    /**
     * Adds a PatientMatch to the collection.
     *
     * @param match to add
     * @return true if added
     */
    @Override
    public boolean add(PatientMatch match)
    {
        boolean added = false;

        // This will insert a match once or twice, because at least one of the patients is local
        if (match.getReferenceServerId() == null) {
            if (this.add(match.getReferencePatientId(), match.getMatchedPatientId(), match)) {
                added = true;
            }
        }
        if (match.getMatchedServerId() == null) {
            if (this.add(match.getMatchedPatientId(), match.getReferencePatientId(), match)) {
                added = true;
            }
        }

        if (added) {
            size++;
            return true;
        } else {
            return false;
        }
    }

    /**
     * @param skipEquivalents skip any match if its equivalent was retrieved
     * @return iterator over the collection
     */
    public Iterator<PatientMatch> iterator(boolean skipEquivalents)
    {
        return new MatchesByPatientIterator(this, skipEquivalents);
    }

    /**
     * @return iterator over the collection
     */
    @Override
    public Iterator<PatientMatch> iterator()
    {
        return iterator(false);
    }

    /**
     * Removes a match from the collection.
     *
     * @param match to remove
     * @return false if not found
     */
    @Override
    public boolean remove(Object o)
    {
        if (!(o instanceof PatientMatch)) {
            return false;
        }

        PatientMatch match = (PatientMatch) o;
        boolean removed = false;

        if (match.getReferenceServerId() == null) {
            if (this.remove(match.getReferencePatientId(), match.getMatchedPatientId(), match)) {
                removed = true;
            }
        }
        if (match.getMatchedServerId() == null) {
            if (this.remove(match.getMatchedPatientId(), match.getReferencePatientId(), match)) {
                removed = true;
            }
        }

        if (removed) {
            size--;
            return true;
        } else {
            return false;
        }
    }

    /**
     * @return a collection of all ids of local patients.
     */
    public Collection<String> getLocalPatientIds()
    {
        return this.internalMap.keySet();
    }

    /**
     * Returns all matches for a local patient id. This will return matches where the patient is both
     * reference patient and matched patient.
     *
     * @param localPatientId id of local patient
     * @param filterEquivalents if true, the return value will contain no equivalent matches
     * @return collection of all matches for {@code localPatientId}
     */
    public Collection<PatientMatch> getMatchesForLocalPatientId(String localPatientId, boolean filterEquivalents)
    {
        List<PatientMatch> list = new LinkedList<>();

        Map<String, Set<PatientMatch>> matchesForPatient = this.internalMap.get(localPatientId);
        for (Set<PatientMatch> set : matchesForPatient.values()) {
            if (filterEquivalents) {
                list.addAll(this.filterEquivalents(set));
            } else {
                list.addAll(set);
            }
        }
        return list;
    }

    /**
     * Returns an equivalent match, or null if doesn't exist.
     *
     * @param match to find an equivalent to
     * @return an equivalent PatientMatch or null if doesn't exist
     */
    public PatientMatch getEquivalentMatch(PatientMatch match)
    {
        Set<PatientMatch> setToSearch = this.getAnySet(match);
        if (setToSearch == null) {
            return null;
        }

        for (PatientMatch m : setToSearch) {
            if (match.isEquivalent(m)) {
                return m;
            }
        }

        return null;
    }

    private boolean add(String localPatientId, String otherPatientId, PatientMatch match)
    {
        Map<String, Set<PatientMatch>> map = this.internalMap.get(localPatientId);
        if (map == null) {
            map = new TreeMap<>();
            this.internalMap.put(localPatientId, map);
        }

        Set<PatientMatch> set = map.get(otherPatientId);
        if (set == null) {
            set = new TreeSet<>(new PatientMatchSetComparator(localPatientId));
            map.put(otherPatientId, set);
        }

        return set.add(match);
    }

    private boolean remove(String localPatientId, String otherPatientId, PatientMatch match) {
        Map<String, Set<PatientMatch>> map = this.internalMap.get(localPatientId);
        if (map == null) {
            return false;
        }

        Set<PatientMatch> set = map.get(otherPatientId);
        if (set == null) {
            return false;
        }

        if (!set.contains(match)) {
            return false;
        }

        set.remove(match);
        return true;
    }

    /**
     * Checks if the collection contains a match.
     *
     * @param match to check
     * @return true if the collection contains the match
     */
    @Override
    public boolean contains(Object o)
    {
        if (!(o instanceof PatientMatch)) {
            return false;
        }

        PatientMatch match = (PatientMatch) o;

        Set<PatientMatch> set = this.getAnySet(match);
        if (set == null) {
            return false;
        }

        return set.contains(match);
    }

    /*
     * Returns only one of the two possible sets where a match can be found. This method is good only for query methods
     * because it is not guaranteed which set is returned.
     */
    private Set<PatientMatch> getAnySet(PatientMatch match)
    {
        if (match.getReferenceServerId() == null) {
            return this.getSet(match.getReferencePatientId(), match.getMatchedPatientId());
        } else {
            return this.getSet(match.getMatchedPatientId(), match.getReferencePatientId());
        }
    }

    private Set<PatientMatch> getSet(String localPatientId, String otherPatientId)
    {
        Map<String, Set<PatientMatch>> map = this.internalMap.get(localPatientId);
        if (map == null) {
            return null;
        }

        return map.get(otherPatientId);
    }

    /*
     * Receives an ordered set and returns the largest possible subset so that for every two matches m1, m2 in the
     * set, it is true that !m1.isEquivalent(m2). The parameter set is expected to contain only a few matches.
     *
     * The set is ordered so that matches where key local patient is matched come first, so these matches are
     * preferred. See comment regarding internalMap for more details.
     */
    private Set<PatientMatch> filterEquivalents(Set<PatientMatch> set)
    {
        Set<PatientMatch> newSet = new HashSet<>();
        for (PatientMatch m : set) {
            if (!this.containsEquivalent(newSet, m)) {
                newSet.add(m);
            }
        }
        return newSet;
    }

    private boolean containsEquivalent(Collection<PatientMatch> c, PatientMatch match)
    {
        for (PatientMatch m : c) {
            if (match.isEquivalent(m)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void clear()
    {
        this.initialize();
    }
}
