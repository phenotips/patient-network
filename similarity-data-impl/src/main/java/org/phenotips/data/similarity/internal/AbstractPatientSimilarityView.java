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

import org.phenotips.data.ContactInfo;
import org.phenotips.data.Patient;
import org.phenotips.data.PatientData;
import org.phenotips.data.permissions.AccessLevel;
import org.phenotips.data.permissions.internal.PatientAccessHelper;
import org.phenotips.data.similarity.AccessType;
import org.phenotips.data.similarity.PatientSimilarityView;
import org.phenotips.vocabulary.VocabularyTerm;

import org.xwiki.model.reference.DocumentReference;
import org.xwiki.users.User;
import org.xwiki.users.UserManager;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for implementing {@link PatientSimilarityView}.
 *
 * @version $Id$
 * @since 1.0M1
 */
public abstract class AbstractPatientSimilarityView implements PatientSimilarityView
{
    protected static final Logger LOGGER = LoggerFactory.getLogger(AbstractPatientSimilarityView.class);

    protected static PatientAccessHelper accessHelper;

    protected static UserManager userManager;

    private static final String MATCHED_GLOBAL_MODE_OF_INHERITANCE = "matched_global_mode_of_inheritance";

    private static final String MATCHED_GLOBAL_AGE_OF_ONSET = "matched_global_age_of_onset";

    private static final String GLOBAL_MODE_OF_INHERITANCE = "global_mode_of_inheritance";

    private static final String GLOBAL_AGE_OF_ONSET = "global_age_of_onset";

    private static final String GLOBAL_QUALIFIERS = "global-qualifiers";

    private static final String PUBMED_ID = "pubmedId";

    private static final String ID_KEY = "id";

    private static final String OWNER_JSON_KEY = "owner";

    /** The matched patient to represent. */
    protected final Patient match;

    /** The reference patient against which to compare. */
    protected final Patient reference;

    /** The access level the user has to this patient. */
    protected final AccessType access;

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

    private String getAgeOfOnset(PatientData<List<VocabularyTerm>> globalControllers)
    {
        if (globalControllers != null) {
            List<VocabularyTerm> ageOfOnset = globalControllers.get(GLOBAL_AGE_OF_ONSET);
            if (ageOfOnset.size() == 1) {
                return ageOfOnset.get(0).getName();
            }
        }
        return "";
    }

    private Set<String> getModeOfInheritance(PatientData<List<VocabularyTerm>> globalControllers)
    {
        Set<String> modes = new HashSet<>();
        if (globalControllers != null) {
            List<VocabularyTerm> modeTermList = globalControllers.get(GLOBAL_MODE_OF_INHERITANCE);
            for (VocabularyTerm term : modeTermList) {
                modes.add(term.getName());
            }
        }
        return Collections.unmodifiableSet(modes);
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
     * {@inheritDoc} Adds data using access-level-aware getters: {@link #getId()}, {@link #getAccess()}, etc.
     *
     * @see org.phenotips.data.Patient#toJSON()
     */
    @Override
    public JSONObject toJSON()
    {
        JSONObject result = new JSONObject();

        result.put(ID_KEY, getId());
        result.put(OWNER_JSON_KEY, getOwnerJSON());
        if (this.access != null) {
            result.put("access", this.access.toString());
        }
        result.put("myCase", isUserOwner(this.match));
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

        // The controllers are assigned by this.match.getData(..) and not this.getData(..) because the latter can
        // be restricted in overriding methods (like RestrictedPatientSimilarityView). The age of onset and mode
        // of inheritance should be exposed for the matching display even if the rest of the patient is not.
        PatientData<List<VocabularyTerm>> matchedControllers = this.match.getData(GLOBAL_QUALIFIERS);
        PatientData<List<VocabularyTerm>> referenceControllers = this.reference.getData(GLOBAL_QUALIFIERS);

        // Age of onset
        result.put(GLOBAL_AGE_OF_ONSET, this.getAgeOfOnset(referenceControllers));
        result.put(MATCHED_GLOBAL_AGE_OF_ONSET, this.getAgeOfOnset(matchedControllers));

        // Mode of inheritance
        result.put(GLOBAL_MODE_OF_INHERITANCE, this.getModeOfInheritance(referenceControllers));
        result.put(MATCHED_GLOBAL_MODE_OF_INHERITANCE, this.getModeOfInheritance(matchedControllers));

        PatientData<String> patientData = this.match.getData("solved");
        if (patientData != null && patientData.size() > 0) {
            result.put("solved", "1".equals(patientData.get("solved")));
            result.put(PUBMED_ID, patientData.get("solved__pubmed_id"));
        }

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

    private JSONObject getOwnerJSON()
    {
        PatientData<ContactInfo> data = this.match.getData("contact");
        if (data != null && data.size() > 0) {
            ContactInfo contact = data.get(0);
            if (contact != null) {
                return contact.toJSON();
            }
        }
        return null;
    }

    /**
     * Checks if current user is owner of one patient.
     */
    private boolean isUserOwner(Patient patient)
    {
        User user = userManager.getCurrentUser();
        DocumentReference userRef = user.getProfileDocument();
        if (patient != null && accessHelper.getOwner(patient) != null
            && userRef.equals(accessHelper.getOwner(patient).getUser())) {
            return true;
        }
        return false;
    }
}
