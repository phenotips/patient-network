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
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.phenotips.data.similarity.internal.mocks;

import org.phenotips.data.FeatureMetadatum;

import net.sf.json.JSONObject;

/**
 * Simple mock for a feature metadatum, responding with pre-specified values.
 *
 * @version $Id$
 */
public class MockFeatureMetadatum implements FeatureMetadatum
{
    private final String id;

    private final String name;

    private final String type;

    public MockFeatureMetadatum(String id, String name, String type)
    {
        this.id = id;
        this.name = name;
        this.type = type;
    }

    @Override
    public String getId()
    {
        return this.id;
    }

    @Override
    public String getName()
    {
        return this.name;
    }

    @Override
    public String getType()
    {
        return this.type;
    }

    @Override
    public JSONObject toJSON()
    {
        return null;
    }
}
