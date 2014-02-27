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

import org.phenotips.data.Disorder;
import org.phenotips.data.similarity.AccessType;
import org.phenotips.data.similarity.DisorderSimilarityView;
import org.apache.commons.lang3.StringUtils;

import net.sf.json.JSONObject;

/**
 * Implementation of {@link DisorderSimilarityView} that always reveals its information.
 * 
 * @version $Id$
 * @since 1.0M10
 */
public class DefaultDisorderSimilarityView implements DisorderSimilarityView
{
    /** The matched disorder to represent. */
    private Disorder match;

    /** The reference disorder against which to compare. */
    private Disorder reference;

    /**
     * Simple constructor passing the {@link #match matched disorder} and the {@link #reference reference disorder}.
     * 
     * @param match the matched disorder to represent
     * @param reference the reference disorder against which to compare, can be {@code null}
     */
    public DefaultDisorderSimilarityView(Disorder match, Disorder reference)
    {
        this.match = match;
        this.reference = reference;
    }
    
    /**
     * Simple constructor passing the {@link #match matched disorder}, the {@link #reference reference disorder}, and
     * the {@link #access patient access type}.
     * 
     * @param match the matched disorder to represent
     * @param reference the reference disorder against which to compare, can be {@code null}
     * @param access the access type, unused
     */
    public DefaultDisorderSimilarityView(Disorder match, Disorder reference, AccessType access)
    {
        this(match, reference);
    }
    
    @Override
    public String getId()
    {
        return this.match != null ? this.match.getId() : null;
    }

    @Override
    public String getName()
    {
        return this.match != null ? this.match.getName() : null;
    }

    @Override
    public JSONObject toJSON()
    {
        if (this.match == null && this.reference == null) {
            return new JSONObject(true);
        }

        JSONObject result = new JSONObject();
        if (this.match != null) {
            result.element("id", this.match.getId());
            result.element("name", this.match.getName());
        }
        if (this.reference != null) {
            result.element("queryId", this.reference.getId());
        }
        double score = getScore();
        if (!Double.isNaN(score)) {
            result.element("score", score);
        }

        return result;
    }

    @Override
    public boolean isMatchingPair()
    {
        return this.match != null && this.reference != null;
    }

    @Override
    public Disorder getReference()
    {
        return this.reference;
    }

    @Override
    public double getScore()
    {
        if (this.reference == null || this.match == null) {
            return Double.NaN;
        }
        if (StringUtils.equals(this.match.getId(), this.reference.getId())) {
            return 1;
        }
        return -1;
    }
}
