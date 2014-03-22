/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.phenotips.data.similarity.internal;

import org.phenotips.data.Feature;
import org.phenotips.data.FeatureMetadatum;
import org.phenotips.data.similarity.AccessType;
import org.phenotips.data.similarity.FeatureClusterView;
import org.phenotips.ontology.OntologyTerm;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

/**
 * Implementation of {@link FeatureClusterView} that always reveals the full patient information; for use in trusted
 * code.
 * 
 * @version $Id$
 * @since 1.0M10
 */
public class DefaultFeatureClusterView implements FeatureClusterView
{
    /** The features in the matched patient. */
    protected Collection<Feature> match;

    /** The features in the reference patient. */
    protected Collection<Feature> reference;

    /** The access level the user has to the patient. */
    protected final AccessType access;

    protected OntologyTerm ancestor;

    protected double score;

    /**
     * Constructor passing the {@link #match matched feature} and the {@link #reference reference feature}.
     * 
     * @param match the features in the matched patient, can be empty
     * @param reference the features in the reference patient, can be empty
     * @param access the access level of the match
     * @param root the root/shared ancestor for the cluster, can be {@code null} to represent unmatched features
     * @param score the score of the feature matching, or 0.0 if unmatched
     * @throws IllegalArgumentException if match or reference are null
     */
    public DefaultFeatureClusterView(Collection<Feature> match, Collection<Feature> reference, AccessType access,
        OntologyTerm root, double score)
    {
        if (match == null || reference == null) {
            throw new IllegalArgumentException("match and reference must not be null");
        }
        this.match = match;
        this.reference = reference;
        this.access = access;
        this.ancestor = root;
        this.score = score;
    }

    @Override
    public Collection<Feature> getReference()
    {
        return Collections.unmodifiableCollection(this.reference);
    }

    @Override
    public Collection<Feature> getMatch()
    {
        return Collections.unmodifiableCollection(this.match);
    }

    @Override
    public OntologyTerm getRoot()
    {
        return this.ancestor;
    }

    @Override
    public double getScore()
    {
        return this.score;
    }

    @Override
    public String getType()
    {
        return "ancestor";
    }

    @Override
    public boolean isPresent()
    {
        // This is not relevant for the ancestor term.
        return false;
    }

    @Override
    public Map<String, ? extends FeatureMetadatum> getMetadata()
    {
        // The ancestor has no metadata.
        return Collections.emptyMap();
    }

    @Override
    public String getId()
    {
        return this.ancestor == null ? "" : this.ancestor.getId();
    }

    @Override
    public String getName()
    {
        return this.ancestor == null ? "Unmatched" : this.ancestor.getName();
    }

    /**
     * {@inheritDoc} Uses {@link #getMatch()} to list features in match patient, so this can be overridden to be
     * access-aware.
     * 
     * @see org.phenotips.data.Feature#toJSON()
     */
    public JSONObject toJSON()
    {
        // Add ancestor info
        JSONObject featureMatchJSON = new JSONObject();
        featureMatchJSON.element("score", this.score);

        JSONObject sharedParentJSON = new JSONObject();
        sharedParentJSON.element("id", getId());
        sharedParentJSON.element("name", getName());
        featureMatchJSON.element("category", sharedParentJSON);

        // Add reference features
        JSONArray referenceJSON = new JSONArray();
        for (Feature term : this.reference) {
            referenceJSON.add(term.getId());
        }
        if (!referenceJSON.isEmpty()) {
            featureMatchJSON.element("reference", referenceJSON);
        }

        // Add match features
        JSONArray matchJSON = new JSONArray();
        for (Feature term : getMatch()) {
            String termId = "";
            if (term != null) {
                termId = term.getId();
            }
            matchJSON.add(termId);
        }
        if (!matchJSON.isEmpty()) {
            featureMatchJSON.element("match", matchJSON);
        }
        
        return featureMatchJSON;
    }
}
