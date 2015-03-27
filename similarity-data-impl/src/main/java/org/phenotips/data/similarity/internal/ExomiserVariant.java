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

import java.util.HashMap;
import java.util.List;

/**
 * A variant after being annotated by Exomiser.
 *
 * @version $Id$
 */
public class ExomiserVariant extends AbstractVariant
{
    /** Info field key for variant effect. */
    private static final String EFFECT_KEY = "FUNCTIONAL_CLASS";

    /** Info field key for variant harmfulness score. */
    private static final String VARIANT_SCORE_KEY = "EXOMISER_VARIANT_SCORE";

    /**
     * Create a {@link #Variant} from a line of an Exomiser variant TSV file.
     *
     * @param columns the names of the TSV columns
     * @param values the value in each TSV column
     * @throws IllegalArgumentException if the variant cannot be parsed
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
}
