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

import org.phenotips.data.similarity.Variant;

import net.sf.json.JSONObject;

/**
 * A variant representation of a candidate gene.
 * 
 * @version $Id$
 * @since
 */
public class CandidateGene implements Variant
{
    @Override
    public double getScore()
    {
        return 1.0;
    }

    @Override
    public String getEffect()
    {
        return null;
    }

    @Override
    public String getChrom()
    {
        return null;
    }

    @Override
    public Integer getPosition()
    {
        return null;
    }

    @Override
    public String getRef()
    {
        return null;
    }

    @Override
    public String getAlt()
    {
        return null;
    }

    @Override
    public Boolean isHomozygous()
    {
        return true;
    }

    @Override
    public String getAnnotation(String key)
    {
        return null;
    }

    @Override
    public int compareTo(Variant o)
    {
        // negative so that largest score comes first
        return -Double.compare(getScore(), o.getScore());
    }

    @Override
    public String toVCFLine()
    {
        return null;
    }

    @Override
    public JSONObject toJSON()
    {
        JSONObject result = new JSONObject();
        result.element("score", getScore());
        return result;
    }

}
