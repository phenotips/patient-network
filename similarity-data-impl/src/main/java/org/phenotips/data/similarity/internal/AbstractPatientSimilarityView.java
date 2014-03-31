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

import org.phenotips.components.ComponentManagerRegistry;
import org.phenotips.data.Patient;
import org.phenotips.data.PatientData;
import org.phenotips.data.permissions.AccessLevel;
import org.phenotips.data.similarity.AccessType;
import org.phenotips.data.similarity.PatientSimilarityView;
import org.phenotips.messaging.ConnectionManager;

import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.model.reference.DocumentReference;

import java.util.Collection;
import java.util.Objects;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

/**
 * Base class for implementing {@link PatientSimilarityView}.
 * 
 * @version $Id$
 * @since 1.0M10
 */
public abstract class AbstractPatientSimilarityView implements PatientSimilarityView
{
    /** The matched patient to represent. */
    protected final Patient match;

    /** The reference patient against which to compare. */
    protected final Patient reference;

    /** The access level the user has to this patient. */
    protected final AccessType access;

    /** The token for contacting the owner of a patient. */
    protected String contactToken;

    /**
     * Simple constructor passing both {@link #match the patient} and the {@link #reference reference patient}.
     * 
     * @param match the matched patient to represent, must not be {@code null}
     * @param reference the reference patient against which to compare, must not be {@code null}
     * @param access the access level the current user has on the matched patient
     * @throws IllegalArgumentException if one of the patients is {@code null}
     */
    public AbstractPatientSimilarityView(Patient match, Patient reference, AccessType access)
        throws IllegalArgumentException
    {
        if (match == null || reference == null) {
            throw new IllegalArgumentException("Similar patients require both a match and a reference");
        }
        this.match = match;
        this.reference = reference;
        this.access = access;

        // Lazily load contact token.
    }

    /**
     * Get JSON for all features in the patient according to the access level. See {@link #getFeatures()} for the
     * features displayed.
     * 
     * @return the JSON for visible features, empty if no features to display.
     */
    protected abstract JSONArray getFeaturesJSON();

    /**
     * Get JSON for all disorders in the patient according to the access level. See {@link #getDisorders()} for the
     * disorders displayed.
     * 
     * @return the JSON for visible disorders, empty if no disorders to display.
     */
    protected abstract JSONArray getDisordersJSON();

    /**
     * Get JSON for many-to-many feature matches between the reference and the match.
     * 
     * @return a JSON array of feature matches, empty if none to display
     */
    protected abstract JSONArray getFeatureMatchesJSON();

    /**
     * Get JSON for gene matches between the reference and the match.
     * 
     * @return a JSON array of gene, empty if none to display, or null if no data is available
     */
    protected abstract JSONArray getGenesJSON();

    @Override
    public String getId()
    {
        return this.match.getId();
    }

    @Override
    public String getExternalId()
    {
        return this.match.getExternalId();
    }

    @Override
    public DocumentReference getDocument()
    {
        return this.match.getDocument();
    }

    @Override
    public DocumentReference getReporter()
    {
        return this.match.getReporter();
    }

    @Override
    public <T> PatientData<T> getData(String name)
    {
        return this.match.getData(name);
    }

    @Override
    public String getContactToken()
    {
        if (this.contactToken == null) {
            String token = "";
            try {
                ConnectionManager cm =
                    ComponentManagerRegistry.getContextComponentManager().getInstance(ConnectionManager.class);
                token = String.valueOf(cm.getConnection(this).getId());
            } catch (ComponentLookupException e) {
                // This should not happen
            }
            this.contactToken = token;
        }
        return this.contactToken;
    }

    @Override
    public AccessLevel getAccess()
    {
        return this.access.getAccessLevel();
    }

    @Override
    public Patient getReference()
    {
        return this.reference;
    }

    /**
     * {@inheritDoc} Adds data using access-level-aware getters: {@link #getId()}, {@link #getAccess()},
     * {@link #getContactToken()}, etc.
     * 
     * @see org.phenotips.data.Patient#toJSON()
     */
    @Override
    public JSONObject toJSON()
    {
        JSONObject result = new JSONObject();

        result.element("id", getId());
        result.element("token", getContactToken());
        if (getReporter() != null) {
            result.element("owner", getReporter().getName());
        }
        if (this.access != null) {
            result.element("access", this.access.toString());
        }
        result.element("myCase", Objects.equals(this.reference.getReporter(), getReporter()));
        result.element("score", getScore());
        result.element("featuresCount", getFeatures().size());
        // Features visible in the match
        JSONArray featuresJSON = getFeaturesJSON();
        if (!featuresJSON.isEmpty()) {
            result.element("features", featuresJSON);
        }
        // Feature matching
        JSONArray featureMatchesJSON = getFeatureMatchesJSON();
        if (!featureMatchesJSON.isEmpty()) {
            result.element("featureMatches", featureMatchesJSON);
        }
        // Disorder matching
        JSONArray disorderJSON = getDisordersJSON();
        if (!disorderJSON.isEmpty()) {
            result.element("disorders", disorderJSON);
        }
        // Gene variant matching
        result.elementOpt("genes", getGenesJSON());

        return result;
    }

    @Override
    public JSONObject toJSON(Collection<String> onlyFieldNames)
    {
        // FIXME This needs to actually take into account the selected field names
        return toJSON();
    }

    @Override
    public void updateFromJSON(JSONObject json)
    {
        // This is not a real patient, and does not need to be updated from a serialization
    }
}
