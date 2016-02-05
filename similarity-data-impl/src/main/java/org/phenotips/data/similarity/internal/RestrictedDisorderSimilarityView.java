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

import org.phenotips.data.Disorder;
import org.phenotips.data.similarity.AccessType;
import org.phenotips.data.similarity.DisorderSimilarityView;

import org.json.JSONObject;

/**
 * Implementation of {@link DisorderSimilarityView} that reveals information if the user has sufficient access to the
 * patient.
 *
 * @version $Id$
 * @since 1.0M1
 */
public class RestrictedDisorderSimilarityView extends DefaultDisorderSimilarityView implements DisorderSimilarityView
{
    /** The access type the user has to the patient having this disorder. */
    protected AccessType access;

    /**
     * Simple constructor passing the {@link #match matched disorder}, the {@link #reference reference disorder}, and
     * the {@link #access patient access type}.
     *
     * @param match the matched disorder to represent
     * @param reference the reference disorder against which to compare, can be {@code null}
     * @param access the access type the user has to the patient having this disorder
     */
    public RestrictedDisorderSimilarityView(Disorder match, Disorder reference, AccessType access)
    {
        super(match, reference);
        this.access = access;
    }

    @Override
    public String getId()
    {
        return this.access.isPrivateAccess() ? null : super.getId();
    }

    @Override
    public String getName()
    {
        return this.access.isPrivateAccess() ? null : super.getName();
    }

    @Override
    public String getValue()
    {
        return this.access.isPrivateAccess() ? null : super.getValue();
    }

    @Override
    public JSONObject toJSON()
    {
        return this.access.isPrivateAccess() ? null : super.toJSON();
    }
}
