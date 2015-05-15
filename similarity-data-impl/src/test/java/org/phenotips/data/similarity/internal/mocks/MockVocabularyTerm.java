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
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.phenotips.data.similarity.internal.mocks;

import org.phenotips.vocabulary.Vocabulary;
import org.phenotips.vocabulary.VocabularyTerm;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import net.sf.json.JSON;
import net.sf.json.JSONObject;

/**
 * Simple mock for an vocabulary term, responding with pre-specified values.
 *
 * @version $Id$
 */
public class MockVocabularyTerm implements VocabularyTerm
{
    private final String id;

    private final Set<VocabularyTerm> parents;

    private final Set<VocabularyTerm> ancestors;

    /**
     * Create a simple Mock VocabularyTerm.
     *
     * @param id the id of the term (e.g. "HP:0123456")
     * @param parents the parents of the term (or null)
     */
    public MockVocabularyTerm(String id, Collection<VocabularyTerm> parents)
    {
        this.id = id;
        this.parents = new HashSet<VocabularyTerm>();
        this.ancestors = new HashSet<VocabularyTerm>();

        if (parents != null) {
            this.parents.addAll(parents);
            // Add parents and ancestors of parents to get all ancestors
            this.ancestors.addAll(parents);
            for (VocabularyTerm parent : this.parents) {
                this.ancestors.addAll(parent.getAncestors());
            }
        }
    }

    /**
     * Create a simple Mock VocabularyTerm.
     *
     * @param id the id of the term (e.g. "HP:0123456")
     * @param parents the parents of the term (or null)
     * @param ancestors the ancestors of the term (or null)
     */
    public MockVocabularyTerm(String id, Collection<VocabularyTerm> parents, Collection<VocabularyTerm> ancestors)
    {
        this.id = id;
        this.parents = new HashSet<VocabularyTerm>();
        this.ancestors = new HashSet<VocabularyTerm>();

        if (parents != null) {
            this.parents.addAll(parents);
        }
        if (ancestors != null) {
            this.ancestors.addAll(ancestors);
        }
    }

    @Override
    public String getId()
    {
        return this.id;
    }

    @Override
    public String getName()
    {
        // Not used
        return null;
    }

    @Override
    public String getDescription()
    {
        // Not used
        return null;
    }

    @Override
    public Set<VocabularyTerm> getParents()
    {
        return this.parents;
    }

    @Override
    public Set<VocabularyTerm> getAncestorsAndSelf()
    {
        Set<VocabularyTerm> result = new LinkedHashSet<VocabularyTerm>();
        result.add(this);
        result.addAll(this.ancestors);
        return result;
    }

    @Override
    public Set<VocabularyTerm> getAncestors()
    {
        return this.ancestors;
    }

    @Override
    public Object get(String name)
    {
        // Not used
        return null;
    }

    @Override
    public Vocabulary getOntology()
    {
        // Not used
        return null;
    }

    @Override
    public long getDistanceTo(VocabularyTerm arg0)
    {
        // Not used
        return 0;
    }

    @Override
    public JSON toJson()
    {
         return new JSONObject();
    }
}
