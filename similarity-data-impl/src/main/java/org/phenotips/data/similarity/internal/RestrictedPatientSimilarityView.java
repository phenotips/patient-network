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
import org.phenotips.data.Disorder;
import org.phenotips.data.Feature;
import org.phenotips.data.Patient;
import org.phenotips.data.PatientData;
import org.phenotips.data.permissions.internal.EntityAccessManager;
import org.phenotips.data.similarity.AccessType;
import org.phenotips.data.similarity.PatientGenotypeSimilarityView;
import org.phenotips.data.similarity.genotype.RestrictedPatientGenotypeSimilarityView;

import org.xwiki.component.manager.ComponentManager;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.users.UserManager;

import java.util.Collections;
import java.util.Set;

import org.json.JSONObject;

/**
 * Implementation of {@link org.phenotips.data.similarity.PatientSimilarityView} that reveals the full patient
 * information if the user has full access to the patient, and only limited information for similar features if the
 * patient is matchable; for use in public scripts.
 *
 * @version $Id$
 * @since 1.0M1
 */
public class RestrictedPatientSimilarityView extends DefaultPatientSimilarityView
{
    static {
        if (accessHelper == null && userManager == null) {
            EntityAccessManager pa = null;
            UserManager um = null;
            try {
                ComponentManager ccm = ComponentManagerRegistry.getContextComponentManager();
                pa = ccm.getInstance(EntityAccessManager.class);
                um = ccm.getInstance(UserManager.class);
            } catch (Exception e) {
                LOGGER.error("Error loading static components: {}", e.getMessage(), e);
            }
            accessHelper = pa;
            userManager = um;
        }
    }

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
        if (access == null) {
            throw new IllegalArgumentException("AccessType cannot be null for restricted view");
        }
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
    public String getExternalId()
    {
        return this.access.isOpenAccess() ? this.match.getExternalId() : null;
    }

    @Override
    public DocumentReference getDocumentReference()
    {
        return this.access.isOpenAccess() ? this.match.getDocumentReference() : null;
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
        } else {
            return super.getFeatures();
        }
    }

    @Override
    public Set<? extends Disorder> getDisorders()
    {
        if (this.access.isPrivateAccess()) {
            return Collections.emptySet();
        } else {
            return super.getDisorders();
        }
    }

    @Override
    protected PatientGenotypeSimilarityView createGenotypeSimilarityView(Patient match, Patient reference,
        AccessType access)
    {
        return new RestrictedPatientGenotypeSimilarityView(match, reference, this.access);
    }

    @Override
    public <T> PatientData<T> getData(String name)
    {
        if (this.access.isOpenAccess()
            || (this.access.isLimitedAccess() && this.isLimitedAccessibleField(name))) {
            return super.getData(name);
        }

        return null;
    }

    private boolean isLimitedAccessibleField(String fieldName)
    {
        return ("contact".equals(fieldName) || "genes".equals(fieldName) || "solved".equals(fieldName)
            || "clinical-diagnosis".equals(fieldName));
    }

    @Override
    public JSONObject toJSON()
    {
        if (this.access.isPrivateAccess()) {
            return new JSONObject();
        } else {
            return super.toJSON();
        }
    }
}
