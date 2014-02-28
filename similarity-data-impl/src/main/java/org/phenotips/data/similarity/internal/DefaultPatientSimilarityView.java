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

import java.util.Set;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.apache.commons.lang3.ObjectUtils;
import org.phenotips.data.Disorder;
import org.phenotips.data.Feature;
import org.phenotips.data.Patient;
import org.phenotips.data.similarity.AccessType;
import org.phenotips.data.similarity.DisorderSimilarityView;
import org.phenotips.data.similarity.FeatureSimilarityView;
import org.phenotips.data.similarity.PatientSimilarityView;

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
     * Simple constructor passing both {@link #match the patient}, the {@link #reference reference patient}, and the
     * {@link #access patient access type}.
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
    protected FeatureSimilarityView createFeatureSimilarityView(Feature match, Feature reference, AccessType access) {
        return new DefaultFeatureSimilarityView(match, reference);
    }

    @Override
    protected DisorderSimilarityView createDisorderSimilarityView(Disorder match, Disorder reference, AccessType access) {
        return new DefaultDisorderSimilarityView(match, reference, this.access);
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

        Set< ? extends Feature> features = getFeatures();
        if (!features.isEmpty()) {
            JSONArray featuresJSON = new JSONArray();
            for (Feature feature : features) {
                featuresJSON.add(feature.toJSON());
            }
            result.element("features", featuresJSON);
        }

        Set< ? extends Disorder> disorders = getDisorders();
        if (!disorders.isEmpty()) {
            JSONArray disordersJSON = new JSONArray();
            for (Disorder disorder : disorders) {
                disordersJSON.add(disorder.toJSON());
            }
            result.element("disorders", disordersJSON);
        }

        return result;
    }


}
