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
import java.util.List;
import java.util.Map;

import net.sf.json.JSONObject;

/**
 * A variant after being annotated by Exomiser.
 *
 * @version $Id$
 */
public class ExomiserVariant implements Variant
{
    /** Info field key for variant effect. */
    private static final String EFFECT_KEY = "FUNCTIONAL_CLASS";

    /** Info field key for variant harmfulness score. */
    private static final String VARIANT_SCORE_KEY = "EXOMISER_VARIANT_SCORE";

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
    private Map<String, String> annotations;

    /**
     * Create a Variant from a line of an Exomiser variant TSV file.
     *
     * @param columns the names of the TSV columns
     * @param values the value in each TSV column
     * @throws IllegalArgumentException if the variant cannot be parsed as an Exomiser variant
     */
    ExomiserVariant(List<String> columns, String[] values) throws IllegalArgumentException
    {
        setChrom(values[0]);
        setPosition(Integer.parseInt(values[1]));
        setGenotype(values[2], values[3], values[6]);

        // Parse into annotation map
        this.annotations = new HashMap<String, String>();
        for (int i = 0; i < Math.min(columns.size(), values.length); i++) {
            this.annotations.put(columns.get(i), values[i]);
        }

        String lineEffect = this.annotations.get(EFFECT_KEY);
        if (lineEffect == null) {
            throw new IllegalArgumentException("Variant missing effect annotation: " + EFFECT_KEY);
        } else {
            setEffect(lineEffect);
        }

        String lineScore = this.annotations.get(VARIANT_SCORE_KEY);
        if (lineScore == null) {
            throw new IllegalArgumentException("Variant missing score annotation: " + VARIANT_SCORE_KEY);
        } else {
            setScore(Double.parseDouble(lineScore));
        }
    }

    /**
     * Set the chromosome of the variant.
     *
     * @param chrom the chromosome string
     */
    private void setChrom(String chrom)
    {
        String chr = chrom.toUpperCase();

        // Standardize without prefix
        if (chr.startsWith("CHR")) {
            chr = chr.substring(3);
        }
        if ("MT".equals(chr)) {
            chr = "M";
        }
        this.chrom = chr;
    }

    /**
     * Set the position of the variant.
     *
     * @param position the variant position (1-indexed)
     */
    private void setPosition(int position)
    {
        this.position = position;
    }

    /**
     * Set the genotype of the variant.
     *
     * @param ref the reference allele
     * @param alt the alternate allele
     * @param gt the genotype (e.g. "0/1")
     */
    private void setGenotype(String ref, String alt, String gt)
    {
        this.ref = ref;
        this.alt = alt;
        this.gt = gt;
    }

    /**
     * Set the genotype of the variant.
     *
     * @param effect the cDNA effect of the variant
     */
    private void setEffect(String effect)
    {
        this.effect = effect;
    }

    /**
     * Set the score of the variant.
     *
     * @param score the score of the variant in [0.0, 1.0], where 1.0 is more harmful
     */
    private void setScore(double score)
    {
        this.score = score;
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
        return this.annotations.get(key);
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
