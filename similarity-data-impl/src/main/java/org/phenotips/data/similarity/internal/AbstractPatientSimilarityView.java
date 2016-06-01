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

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Base class for implementing {@link PatientSimilarityView}.
 *
 * @version $Id$
 * @since 1.0M1
 */
public abstract class AbstractPatientSimilarityView implements PatientSimilarityView
{
    private static final String ID_KEY = "id";

    private static final String NAME_KEY = "name";

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
            throw new IllegalArgumentException("Both a match and a reference patient required");
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
    public JSONObject getOwnerJSON()
    {
        // TODO: Update once there is a convenient method for accessing the owner of the patient record.
        // In the meantime, we can access the data we need through the serialized controller data.
        PatientData<String> data = this.match.getData("contact");
        JSONObject contact = new JSONObject();

        if (data != null && data.isNamed()) {
            contact.accumulate(ID_KEY, data.get("user_id"));
            contact.accumulate(NAME_KEY, data.get(NAME_KEY));
        }
        // Fall back on reporter
        if (contact.length() == 0) {
            DocumentReference reporter = getReporter();
            if (reporter != null) {
                contact.accumulate(ID_KEY, reporter.getName());
            }
        }
        return contact;
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
                token = cm.getConnection(this).getToken();
            } catch (ComponentLookupException e) {
                // This should not happen
            } catch (Exception ex) {
                // FIXME: this happens when an attempt to establish a connection between
                // a local and remote patient owners is made
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

        result.put(ID_KEY, getId());
        result.put("token", getContactToken());
        result.put("owner", getOwnerJSON());
        if (this.access != null) {
            result.put("access", this.access.toString());
        }
        result.put("myCase", Objects.equals(this.reference.getReporter(), getReporter()));
        result.put("score", getScore());
        result.put("featuresCount", getFeatures().size());
        // Features visible in the match
        JSONArray featuresJSON = getFeaturesJSON();
        if (featuresJSON.length() > 0) {
            result.put("features", featuresJSON);
        }
        // Feature matching
        JSONArray featureMatchesJSON = getFeatureMatchesJSON();
        if (featureMatchesJSON.length() > 0) {
            result.put("featureMatches", featureMatchesJSON);
        }
        // Disorder matching
        JSONArray disorderJSON = getDisordersJSON();
        if (disorderJSON.length() > 0) {
            result.put("disorders", disorderJSON);
        }
        // Gene variant matching
        result.putOpt("genes", getGenesJSON());

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
