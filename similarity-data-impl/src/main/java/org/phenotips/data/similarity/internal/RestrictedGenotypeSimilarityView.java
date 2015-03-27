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

import org.phenotips.data.Patient;
import org.phenotips.data.similarity.AccessType;
import org.phenotips.data.similarity.PatientGenotypeSimilarityView;
import org.phenotips.data.similarity.Variant;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.sf.json.JSONArray;

/**
 * Implementation of {@link PatientGenotypeSimilarityView} that reveals the full patient information if the user has
 * full access to the patient, and only matching reference information if the patient is matchable.
 *
 * @version $Id$
 * @since
 */
public class RestrictedGenotypeSimilarityView extends DefaultGenotypeSimilarityView
{
    /**
     * Simple constructor passing the {@link #match matched patient}, the {@link #reference reference patient}, and the
     * {@link #access patient access type}.
     *
     * @param match the matched patient, which is represented by this match
     * @param reference the reference patient, which the match is against
     * @param access the access type the user has to the match patient
     * @throws IllegalArgumentException if one of the patients or access is {@code null}
     */
    public RestrictedGenotypeSimilarityView(Patient match, Patient reference, AccessType access)
        throws IllegalArgumentException
    {
        super(match, reference, access);
        if (access == null) {
            throw new IllegalArgumentException("AccessType cannot be null for restricted view");
        }
    }

    @Override
    public List<Variant> getTopVariants(String gene)
    {
        if (this.access.isOpenAccess()) {
            return super.getTopVariants(gene);
        } else if (this.access.isLimitedAccess()) {
            List<Variant> restrictedVariants = new ArrayList<Variant>();
            for (Variant v : super.getTopVariants(gene)) {
                restrictedVariants.add(new RestrictedVariant(v));
            }
            return restrictedVariants;
        } else {
            return Collections.emptyList();
        }
    }

    @Override
    public JSONArray toJSON()
    {
        if (this.access.isPrivateAccess()) {
            return null;
        } else {
            return super.toJSON();
        }
    }
}
