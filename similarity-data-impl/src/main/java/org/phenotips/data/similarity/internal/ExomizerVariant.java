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

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

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
    private Integer position;

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

    /** A string representation of the parsed info field, used to write to VCF. */
    private String rawInfo;

    /**
     * Create a Variant from a line of an exomizer-annotated VCF file.
     * 
     * @param line the line of the VCF file
     */
    ExomizerVariant(String line)
    {
        String[] tokens = StringUtils.split(line, '\t');
        init(tokens[0], Integer.parseInt(tokens[1]), tokens[3], tokens[4], tokens[9], tokens[7], null);
    }

    /**
     * Create a Variant from a MedSavant Row using the current database configuration. TODO: make this adapt to the
     * database configuration, rather than use hard-coded column indices.
     * 
     * @param line the line of the VCF file
     */
    ExomizerVariant(JSONArray row)
    {
        init(row.getString(4), row.getInt(5), row.getString(8), row.getString(9),
            StringUtils.split(row.getString(14), ':')[0], row.getString(15), 0.0);
    }

    /**
     * Initialize a variant given a standard set of field values.
     * 
     * @param chrom the chromosome string
     * @param position the variant position (1-indexed)
     * @param ref the reference allele
     * @param alt the alternate allele
     * @param gt the genotype (e.g. "0/1")
     * @param info the info field from a VCF line, may contain "EFFECT" annotation
     * @param score the score (if null, will be parsed from "VARIANT_SCORE" annotation in info field
     */
    private void init(String chrom, Integer position, String ref, String alt, String gt, String info, Double score)
    {
        this.chrom = chrom.toUpperCase();

        // Standardize chromosome
        if (this.chrom.startsWith("CHR")) {
            this.chrom = this.chrom.substring(3);
        }
        if ("MT".equals(this.chrom)) {
            this.chrom = "M";
        }

        this.position = position;
        this.ref = ref;
        this.alt = alt;
        this.gt = gt;

        // Parse the INFO field into a dictionary
        this.rawInfo = info;
        this.info = new HashMap<String, String>();
        String[] infoParts = StringUtils.split(info, ';');
        for (String part : infoParts) {
            int splitIndex = part.indexOf('=');
            if (splitIndex >= 0) {
                this.info.put(part.substring(0, splitIndex), part.substring(splitIndex + 1));
            } else {
                this.info.put(part, "");
            }
        }
        this.effect = this.info.get("EFFECT");

        if (score == null) {
            // Try to read score from info field
            String infoScore = this.info.get("VARIANT_SCORE");
            if (infoScore != null) {
                this.score = Double.parseDouble(infoScore);
                if (this.effect.equals("MISSENSE")) {
                    this.score *= 0.90;
                }
            }
        } else {
            this.score = score;
        }
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
    public Boolean isHomozygous()
    {
        return "1/1".equals(this.gt) || "1|1".equals(this.gt);
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
    public String toVCFLine()
    {
        return String.format("%s\t%s\t.\t%s\t%s\t.\tPASS\t%s\tGT\t%s", this.chrom, this.position, this.ref, this.alt,
            this.rawInfo, this.gt);
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
