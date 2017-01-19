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
package org.phenotips.data.similarity.internal.mocks;

import org.phenotips.data.Feature;
import org.phenotips.data.FeatureMetadatum;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;

/**
 * Simple mock for a patient feature, responding with pre-specified values.
 *
 * @version $Id$
 */
public class MockFeature implements Feature
{
    private final String id;

    private final String name;

    private final String type;

    private final boolean present;

    private final String notes;

    private final Map<String, FeatureMetadatum> meta;

    public MockFeature(String id, String name, String type, boolean present)
    {
        this(id, name, type, Collections.<String, FeatureMetadatum>emptyMap(), present, "");
    }

    public MockFeature(String id, String name, String type, Map<String, FeatureMetadatum> meta, boolean present)
    {
        this(id, name, type, meta, present, "");
    }

    public MockFeature(String id, String name, String type, Map<String, FeatureMetadatum> meta, boolean present,
        String notes)
    {
        this.id = id;
        this.name = name;
        this.type = type;
        this.meta = meta;
        this.present = present;
        this.notes = notes;
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
    public boolean isPresent()
    {
        return this.present;
    }

    @Override
    public Map<String, ? extends FeatureMetadatum> getMetadata()
    {
        return this.meta;
    }

    @Override
    public String getNotes()
    {
        return this.notes;
    }

    @Override
    public JSONObject toJSON()
    {
        return null;
    }

    @Override
    public String getValue()
    {
        return (StringUtils.isNotBlank(this.id) ? this.id : this.name);
    }

    @Override
    public String getPropertyName()
    {
        return "";
    }

    @Override
    public List<String> getCategories()
    {
        return Collections.emptyList();
    }
}
