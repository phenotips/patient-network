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
package org.phenotips.data.similarity.genotype;

import java.util.HashMap;
import java.util.List;

/**
 * A variant after being annotated by Exomiser.
 *
 * @version $Id$
 * @since 1.0M2
 */
public class FileSystemExomeVariant extends AbstractVariant
{
    /** Info field key for variant effect. */
    private static final String EFFECT_KEY = "FUNCTIONAL_CLASS";

    /** Info field key for variant harmfulness score. */
    private static final String VARIANT_SCORE_KEY = "EXOMISER_VARIANT_SCORE";

    /**
     * Create a {@link Variant} from a line of an Exomiser variant TSV file.
     *
     * @param columns the names of the TSV columns
     * @param values the value in each TSV column
     * @throws IllegalArgumentException if the variant cannot be parsed
     */
    FileSystemExomeVariant(List<String> columns, String[] values) throws IllegalArgumentException
    {
        setChrom(values[0]);
        setPosition(Integer.parseInt(values[1]));
        setGenotype(values[2], values[3], values[6]);

        // Parse into annotation map
        this.annotations = new HashMap<>();
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
