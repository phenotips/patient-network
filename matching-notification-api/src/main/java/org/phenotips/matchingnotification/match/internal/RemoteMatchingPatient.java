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
package org.phenotips.matchingnotification.match.internal;

import org.phenotips.data.ContactInfo;
import org.phenotips.data.Disorder;
import org.phenotips.data.Feature;
import org.phenotips.data.Gene;
import org.phenotips.data.IndexedPatientData;
import org.phenotips.data.Patient;
import org.phenotips.data.PatientData;
import org.phenotips.data.PatientWritePolicy;

import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReference;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.json.JSONObject;

import com.xpn.xwiki.api.Document;
import com.xpn.xwiki.doc.XWikiDocument;

/**
 * Implementation of {@link org.phenotips.data.Patient} based on the data obtained from the remote server for use in the
 * similarity search algorithm (which takes a model patient as one of the inputs).
 *
 * @version $Id$
 * @since 1.0M8
 */
public class RemoteMatchingPatient implements Patient
{
    private final String remotePatientId;

    private final String label;

    private final Set<? extends Feature> features;

    private final Set<? extends Disorder> disorders;

    private final Set<Gene> genes;

    private final ContactInfo contactInfo;

    /**
     * Default constructor.
     *
     * @param remotePatientId remotePatientId
     * @param label label
     * @param features features
     * @param disorders disorders
     * @param genes genes
     * @param contactInfo contactInfo
     */
    public RemoteMatchingPatient(String remotePatientId, String label, Set<Feature> features, Set<Disorder> disorders,
        Set<Gene> genes, ContactInfo contactInfo)
    {
        this.remotePatientId = remotePatientId;
        this.label = label;
        this.features = (features != null) ? features : new HashSet<Feature>();
        this.disorders = (disorders != null) ? disorders : new HashSet<Disorder>();
        this.genes = (genes != null) ? genes : new HashSet<Gene>();
        this.contactInfo = contactInfo;
    }

    @Override
    public String getId()
    {
        return this.remotePatientId;
    }

    @Override
    public String getExternalId()
    {
        return this.label;
    }

    /**
     * {@inheritDoc}
     *
     * @deprecated use {@link #getDocumentReference()} instead
     */
    @Deprecated
    @Override
    public DocumentReference getDocument()
    {
        return this.getDocumentReference();
    }

    @Override
    public DocumentReference getDocumentReference()
    {
        return null;
    }

    @Override
    public DocumentReference getReporter()
    {
        return null;
    }

    @Override
    public Set<? extends Feature> getFeatures()
    {
        return this.features;
    }

    @Override
    public Set<? extends Disorder> getDisorders()
    {
        return this.disorders;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> PatientData<T> getData(String name)
    {
        // TODO: somehow reuse GeneController.load()?
        if ("genes".equals(name)) {
            List<Gene> allGenes = new LinkedList<>();
            allGenes.addAll(this.genes);
            return (PatientData<T>) new IndexedPatientData<>("genes", allGenes);
        }

        if ("contact".equals(name)) {
            return (PatientData<T>) new IndexedPatientData<>("contact",
                Collections.singletonList(this.contactInfo));
        }

        return null;
    }

    @Override
    public JSONObject toJSON()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public JSONObject toJSON(Collection<String> selectedFields)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateFromJSON(JSONObject json)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateFromJSON(JSONObject json, PatientWritePolicy policy)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Document getSecureDocument()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public XWikiDocument getXDocument()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getDescription()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getName()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public EntityReference getType()
    {
        throw new UnsupportedOperationException();
    }
}
