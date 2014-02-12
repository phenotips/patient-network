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

import org.phenotips.data.Disorder;
import org.phenotips.data.Feature;
import org.phenotips.data.Patient;
import org.phenotips.data.similarity.AccessType;
import org.phenotips.data.similarity.DisorderSimilarityView;
import org.phenotips.data.similarity.FeatureSimilarityView;
import org.phenotips.data.similarity.PatientSimilarityView;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.lang3.ObjectUtils;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

/**
 * Implementation of {@link PatientSimilarityView} that always reveals the full patient information; for use in trusted
 * Java code.
 * 
 * @version $Id$
 * @since 1.0M10
 */
public class DefaultPatientSimilarityView extends AbstractPatientSimilarityView implements PatientSimilarityView
{
    /**
     * Simple constructor passing both {@link #match the patient} and the {@link #reference reference patient}.
     * 
     * @param match the matched patient to represent, must not be {@code null}
     * @param reference the reference patient against which to compare, must not be {@code null}
     * @param access the access level the current user has on the matched patient
     * @throws IllegalArgumentException if one of the patients is {@code null}
     */
    public DefaultPatientSimilarityView(Patient match, Patient reference, AccessType access)
        throws IllegalArgumentException
    {
        super(match, reference, access);
        matchFeatures();
        matchDisorders();
    }

    /**
     * Constructor that copies the data from another patient pair.
     * 
     * @param restrictedView the restricted patient pair to clone
     */
    public DefaultPatientSimilarityView(AbstractPatientSimilarityView restrictedView)
    {
        this(restrictedView.match, restrictedView.reference, restrictedView.access);
    }

    @Override
    public Set<? extends Feature> getFeatures()
    {
        Set<Feature> result = new HashSet<Feature>();
        for (FeatureSimilarityView feature : this.matchedFeatures) {
            if (feature.isMatchingPair() || feature.getId() != null) {
                result.add(feature);
            }
        }

        return result;
    }

    @Override
    public Set<? extends Disorder> getDisorders()
    {
        Set<Disorder> result = new HashSet<Disorder>();
        for (DisorderSimilarityView disorder : this.matchedDisorders) {
            if (disorder.getId() != null) {
                result.add(disorder);
            }
        }

        return result;
    }

    @Override
    public JSONObject toJSON()
    {
        JSONObject result = new JSONObject();

        result.element("id", this.match.getDocument().getName());
        result.element("token", getContactToken());
        result.element("owner", this.match.getReporter().getName());
        result.element("access", this.access.toString());
        result.element("myCase", ObjectUtils.equals(this.reference.getReporter(), this.match.getReporter()));
        result.element("score", getScore());
        result.element("featuresCount", this.match.getFeatures().size());

        Set<? extends Feature> features = getFeatures();
        if (!features.isEmpty()) {
            JSONArray featuresJSON = new JSONArray();
            for (Feature feature : features) {
                featuresJSON.add(feature.toJSON());
            }
            result.element("features", featuresJSON);
        }

        Set<? extends Disorder> disorders = getDisorders();
        if (!disorders.isEmpty()) {
            JSONArray disordersJSON = new JSONArray();
            for (Disorder disorder : disorders) {
                disordersJSON.add(disorder.toJSON());
            }
            result.element("disorders", disordersJSON);
        }

        return result;
    }

    /**
     * Create pairs of matching features, one from the current patient and one from the reference patient. Unmatched
     * values from either side are paired with a {@code null} value.
     */
    private void matchFeatures()
    {
        Set<FeatureSimilarityView> result = new HashSet<FeatureSimilarityView>();
        for (Feature feature : this.match.getFeatures()) {
            Feature matching = findMatchingFeature(feature, this.reference.getFeatures());
            result.add(new DefaultFeatureSimilarityView(feature, matching));
        }
        for (Feature feature : this.reference.getFeatures()) {
            Feature matching = findMatchingFeature(feature, this.match.getFeatures());
            if (matching == null) {
                result.add(new DefaultFeatureSimilarityView(null, feature));
            }
        }
        this.matchedFeatures = Collections.unmodifiableSet(result);
    }

    /**
     * Create pairs of matching disorders, one from the current patient and one from the reference patient. Unmatched
     * values from either side are paired with a {@code null} value.
     */
    private void matchDisorders()
    {
        Set<DisorderSimilarityView> result = new HashSet<DisorderSimilarityView>();
        for (Disorder disorder : this.match.getDisorders()) {
            result.add(new DefaultDisorderSimilarityView(disorder, findMatchingDisorder(disorder,
                this.reference.getDisorders())));
        }
        for (Disorder disorder : this.reference.getDisorders()) {
            if (this.match == null || findMatchingDisorder(disorder, this.match.getDisorders()) == null) {
                result.add(new DefaultDisorderSimilarityView(null, disorder));
            }
        }
        this.matchedDisorders = Collections.unmodifiableSet(result);
    }
}
