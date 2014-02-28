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

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.apache.commons.lang3.ObjectUtils;
import org.phenotips.data.Disorder;
import org.phenotips.data.Feature;
import org.phenotips.data.Patient;
import org.phenotips.data.PatientData;
import org.phenotips.data.similarity.AccessType;
import org.phenotips.data.similarity.DisorderSimilarityView;
import org.phenotips.data.similarity.FeatureSimilarityView;
import org.phenotips.data.similarity.PatientSimilarityView;
import org.xwiki.model.reference.DocumentReference;

/**
 * Implementation of {@link PatientSimilarityView} that reveals the full patient information if the user has full access
 * to the patient, and only limited information for similar features if the patient is matchable; for use in public
 * scripts.
 * 
 * @version $Id$
 * @since 1.0M8
 */
public class RestrictedPatientSimilarityView extends AbstractPatientSimilarityView
{
    /**
     * Simple constructor passing both {@link #match the patient} and the {@link #reference reference patient}.
     * 
     * @param match the matched patient to represent, must not be {@code null}
     * @param reference the reference patient against which to compare, must not be {@code null}
     * @param access the access level the current user has on the matched patient
     * @throws IllegalArgumentException if one of the patients is {@code null}
     */
    public RestrictedPatientSimilarityView(Patient match, Patient reference, AccessType access)
        throws IllegalArgumentException
    {
        super(match, reference, access);
    }

    /**
     * Constructor that copies the data from another patient pair.
     * 
     * @param openView the open patient pair to clone
     */
    public RestrictedPatientSimilarityView(AbstractPatientSimilarityView openView)
    {
        this(openView.match, openView.reference, openView.access);
    }

    @Override
    public DocumentReference getDocument()
    {
        return this.access.isOpenAccess() ? this.match.getDocument() : null;
    }

    @Override
    public DocumentReference getReporter()
    {
        return this.access.isOpenAccess() ? this.match.getReporter() : null;
    }

    @Override
    public Set<? extends Feature> getFeatures()
    {
        if (this.access.isPrivateAccess()) {
            return Collections.emptySet();
        }

        Set<Feature> result = new HashSet<Feature>();
        for (FeatureSimilarityView feature : this.matchedFeatures) {
            if (feature.isMatchingPair() || this.access.isOpenAccess() && feature.getId() != null) {
                result.add(feature);
            }
        }

        return result;
    }
    
    @Override
    public Set<? extends Disorder> getDisorders()
    {
        if (!this.access.isOpenAccess()) {
            return Collections.emptySet();
        } else {
            return super.getDisorders();
        }
    }

    @Override
    protected FeatureSimilarityView createFeatureSimilarityView(Feature match, Feature reference, AccessType access) {
        return new RestrictedFeatureSimilarityView(match, reference, this.access);
    }
    
    @Override
    protected DisorderSimilarityView createDisorderSimilarityView(Disorder match, Disorder reference, AccessType access) {
        return new RestrictedDisorderSimilarityView(match, reference, this.access);
    }
    
    @Override
    public <T> PatientData<T> getData(String name)
    {
        if (this.access.isOpenAccess()) {
            return this.match.getData(name);
        }
        return null;
    }

    @Override
    public JSONObject toJSON()
    {
        if (this.access.isPrivateAccess()) {
            return new JSONObject(true);
        }
        JSONObject result = new JSONObject();

        if (this.access.isOpenAccess()) {
            result.element("id", this.match.getDocument().getName());
            result.element("owner", this.match.getReporter().getName());
        }
        result.element("token", getContactToken());
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
        
        result.element("genes", this.matchedGenes.toJSON());
        
        return result;
    }
}
