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
package org.phenotips.data.similarity;

import net.sf.json.JSONObject;

import org.xwiki.stability.Unstable;

/**
 * A simple representation of a variant from a VCF file.
 * 
 * @version $Id$
 * @since
 */
@Unstable
public interface Variant extends Comparable<Variant>
{
    /**
     * Get the chromosome of the variant, e.g. "1", "2", ..., "X", "Y".
     * 
     * @return the chromosome or null if not available.
     */
    String getChrom();

    /**
     * Get the position of the variant (1-indexed).
     * 
     * @return the (1-indexed) position or null if not available.
     */
    Integer getPosition();

    /**
     * Get the reference allele for the variant (e.g. "A", "CCT"). If the variant is a simple insertion, this might be
     * "-".
     * 
     * @return the reference genotype as a string, or null if not available.
     */
    String getRef();

    /**
     * Get the alternate allele for the variant (e.g. "A", "CCT"). If the variant is a simple deletion, this might be
     * "-".
     * 
     * @return the alternate genotype as a string, or null if not available.
     */
    String getAlt();

    /**
     * Return whether the variant is homozygous or not.
     * 
     * @return true if the variant is homozygous, false otherwise, null if unspecified.
     */
    Boolean isHomozygous();

    /**
     * Get the value associated with a particular annotation (such as the INFO field of a VCF file).
     * 
     * @param key the key to search for.
     * @return the value, an empty string if the key exists without a value, and null if the key does not exist.
     */
    String getAnnotation(String key);

    /**
     * Get the harmfulness score of the variant.
     * 
     * @return the harmfulness score of the variant, default 0.0.
     */
    double getScore();

    /**
     * Get the variant effect (e.g. "SPLICING", "FS_INSERTION"). If the variant falls into multiple classes, the most
     * harmful should be provided, based upon a reasonable ordering such as Exomizer output.
     * 
     * @return the effect of the variant as a string, potentially null.
     */
    String getEffect();

    /**
     * Get the VCF-line equivalent of the variant.
     * 
     * @return the line of the VCF file without a terminal newline, or null if not available.
     */
    String toVCFLine();
    
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
     * @return the data about this value, using the json-lib classes.
     */
    JSONObject toJSON();
}
