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

import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * @version $Id$
 */
public class MatchesByPatient
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
    public MatchesByPatient() {
        this.internalMap = new TreeMap<>();
    }

    /**
     * Creates a collection with all given matches.
     *
     * @param matches a collection of PatientMatch-es
     */
    public MatchesByPatient(List<PatientMatch> matches) {
        this();
        for (PatientMatch match : matches) {
            this.add(match);
        }
    }

    /**
     * @return number of matches in collection
     */
    public int size()
    {
        Set<PatientMatch> matches = new HashSet<PatientMatch>();
        for (Map<String, Set<PatientMatch>> matchesForLocalPatient : this.internalMap.values()) {
            for (Set<PatientMatch> set : matchesForLocalPatient.values()) {
                matches.addAll(set);
            }
        }

        return matches.size();
    }


    /**
     * Adds a PatientMatch to the collection.
     *
     * @param match to add
     * @return true if added
     */
    public boolean add(PatientMatch match)
    {
        boolean addedAsRef = false;
        boolean addedAdMatch = false;

        // This will insert a match once or twice, because at least one of the patients is local
        if (match.getReferenceServerId() == null) {
            addedAsRef = this.add(match.getReferencePatientId(), match.getMatchedPatientId(), match);
        }
        if (match.getMatchedServerId() == null) {
            addedAdMatch = this.add(match.getMatchedPatientId(), match.getReferencePatientId(), match);
        }

        return addedAsRef || addedAdMatch;
    }

    /**
     * Removes a match from the collection.
     *
     * @param match to remove
     * @return false if not found
     */
    public boolean remove(PatientMatch match) {
        boolean removedAsRef = false;
        boolean removedAsMatch = false;

        if (match.getReferenceServerId() == null) {
            removedAsRef = this.remove(match.getReferencePatientId(), match.getMatchedPatientId(), match);
        }
        if (match.getMatchedServerId() == null) {
            removedAsMatch = this.remove(match.getMatchedPatientId(), match.getReferencePatientId(), match);
        }

        return removedAsRef || removedAsMatch;
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
    public boolean contains(PatientMatch match) {
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
    private Set<PatientMatch> getAnySet(PatientMatch match) {
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
            boolean insert = true;
            for (PatientMatch n : newSet) {
                if (n.isEquivalent(m)) {
                    insert = false;
                }
            }
            if (insert) {
                newSet.add(m);
            }
        }
        return newSet;
    }
}
