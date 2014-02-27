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

import java.util.Map;
import java.util.TreeMap;

import net.sf.json.JSONObject;

import org.bouncycastle.util.Strings;
import org.phenotips.data.similarity.Variant;

/**
 * An annotated variant, as outputted by Exomizer.
 * 
 * @version $Id$
 * @since
 */
public class ExomizerVariant implements Variant
{
    /** See {@link #getChrom()}. */
    private String chrom;

    /** See {@link #getPosition()}. */
    private int position;

    /** See {@link #getRef()}. */
    private String ref;

    /** See {@link #getAlt()}. */
    private String alt;

    /** See {@link #getScore()}. */
    private double score;

    /** See {@link #getEffect()}. */
    private String effect;

    /** See {@link #getGenotype()}. */
    private String gt;

    /** See {@link #getAnnotation(String)}. */
    private Map<String, String> info;

    ExomizerVariant(String line)
    {
        String[] tokens = Strings.split(line, '\t');
        this.chrom = tokens[0].toUpperCase();

        // Standardize chromosome
        if (this.chrom.startsWith("CHR")) {
            this.chrom = this.chrom.substring(3);
        }
        if (this.chrom == "MT") {
            this.chrom = "M";
        }

        this.position = Integer.parseInt(tokens[1]);
        this.ref = tokens[3];
        this.alt = tokens[4];
        this.gt = Strings.split(tokens[9], ':')[0];

        // Parse the INFO field into a dictionary
        this.info = new TreeMap<String, String>();
        String[] infoParts = Strings.split(tokens[7], ';');
        for (String part : infoParts) {
            int splitIndex = part.indexOf('=');
            if (splitIndex >= 0) {
                this.info.put(part.substring(0, splitIndex), part.substring(splitIndex + 1));
            } else {
                this.info.put(part, "");
            }
        }

        this.effect = this.info.get("EFFECT");
        this.score = Double.parseDouble(this.info.get("VARIANT_SCORE"));
    }

    @Override
    public double getScore()
    {
        return this.score;
    }

    @Override
    public String getEffect()
    {
        return this.effect;
    }

    @Override
    public String getChrom()
    {
        return this.chrom;
    }

    @Override
    public Integer getPosition()
    {
        return this.position;
    }

    @Override
    public String getRef()
    {
        return this.ref;
    }

    @Override
    public String getAlt()
    {
        return this.alt;
    }

    @Override
    public boolean isHomozygous()
    {
        return "1/1".equals(this.gt);
    }

    @Override
    public String getAnnotation(String key)
    {
        return this.info.get(key);
    }

    @Override
    public int compareTo(Variant o)
    {
        // negative so that largest score comes first
        return -Double.compare(getScore(), o.getScore());
    }

    @Override
    public JSONObject toJSON()
    {
        JSONObject result = new JSONObject();
        result.element("score", getScore());
        result.element("chrom", getChrom());
        result.element("position", getPosition());
        result.element("ref", getRef());
        result.element("alt", getAlt());
        result.element("type", getEffect());
        return result;
    }

}
