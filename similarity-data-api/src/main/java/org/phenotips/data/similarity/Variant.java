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
package org.phenotips.data.similarity;

import org.xwiki.stability.Unstable;

import org.json.JSONObject;

/**
 * A simple representation of a variant from a VCF file.
 *
 * @version $Id$
 * @since 1.0M1
 */
@Unstable
public interface Variant extends Comparable<Variant>
{
    /**
     * Get the chromosome of the variant, e.g. "1", "2", ..., "X", "Y".
     *
     * @return the chromosome or null if not available
     */
    String getChrom();

    /**
     * Get the position of the variant (1-indexed).
     *
     * @return the (1-indexed) position or {@code null} if not available
     */
    Integer getPosition();

    /**
     * Get the reference allele for the variant (e.g. "A", "CCT"). If the variant is a simple insertion, this might be
     * "-".
     *
     * @return the reference genotype as a string, or {@code null} if not available
     */
    String getRef();

    /**
     * Get the alternate allele for the variant (e.g. "A", "CCT"). If the variant is a simple deletion, this might be
     * "-".
     *
     * @return the alternate genotype as a string, or {@code null} if not available
     */
    String getAlt();

    /**
     * Return whether the variant is homozygous or not.
     *
     * @return {@code true} if the variant is homozygous, {@code false} otherwise, {@code null} if unspecified
     */
    Boolean isHomozygous();

    /**
     * Get the value associated with a particular annotation (such as the INFO field of a VCF file).
     *
     * @param key the key to search for
     * @return the value, an empty string if the key exists without a value, and {@code null} if the key does not exist.
     */
    String getAnnotation(String key);

    /**
     * Get the harmfulness score of the variant.
     *
     * @return the harmfulness score of the variant, or {@code null} if not available
     */
    Double getScore();

    /**
     * Get the variant effect (e.g. "SPLICING", "FS_INSERTION"). If the variant falls into multiple classes, the most
     * harmful should be provided, based upon a reasonable ordering such as Exomiser output.
     *
     * @return the effect of the variant as a string, potentially {@code null}
     */
    String getEffect();

    /**
     * Retrieve a variant's information in a JSON format. For example:
     *
     * <pre>
     *       {
     *         "chrom": "1",
     *         "position": 2014819,
     *         "ref": "A",
     *         "alt": "T",
     *         ...
     *       }
     * </pre>
     *
     * @return the data about this value, using the json-lib classes
     */
    JSONObject toJSON();
}
