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

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.phenotips.data.Patient;
import org.phenotips.data.permissions.AccessLevel;
import org.phenotips.data.permissions.PermissionsManager;
import org.phenotips.data.similarity.AccessType;
import org.phenotips.data.similarity.PatientSimilarityView;
import org.phenotips.data.similarity.PatientSimilarityViewFactory;
import org.xwiki.component.annotation.Component;

/**
 * Implementation of {@link PatientSimilarityViewFactory} which only allows access to public or shared information.
 * 
 * @version $Id$
 * @since 1.0M8
 */
@Component
@Named("restricted")
@Singleton
public class RestrictedPatientSimilarityViewFactory implements PatientSimilarityViewFactory
{
    /** Computes the real access level for a patient. */
    @Inject
    protected PermissionsManager permissions;

    /** Needed by {@link DefaultAccessType} for checking if a given access level provides read access to patients. */
    @Inject
    @Named("view")
    protected AccessLevel viewAccess;

    /** Needed by {@link DefaultAccessType} for checking if a given access level provides limited access to patients. */
    @Inject
    @Named("match")
    protected AccessLevel matchAccess;

    @Override
    public PatientSimilarityView makeSimilarPatient(Patient match, Patient reference) throws IllegalArgumentException
    {
        if (match == null || reference == null) {
            throw new IllegalArgumentException("Similar patients require both a match and a reference");
        }
        AccessType access = new DefaultAccessType(this.permissions.getPatientAccess(match).getAccessLevel(),
            this.viewAccess, this.matchAccess);
        return new RestrictedPatientSimilarityView(match, reference, access);
    }

    @Override
    public PatientSimilarityView convert(PatientSimilarityView patientPair)
    {
        if (patientPair instanceof AbstractPatientSimilarityView) {
            return new RestrictedPatientSimilarityView((AbstractPatientSimilarityView) patientPair);
        }
        AccessType access = new DefaultAccessType(patientPair.getAccess(), this.viewAccess, this.matchAccess);
        return new RestrictedPatientSimilarityView(patientPair, patientPair.getReference(), access);
    }
}
