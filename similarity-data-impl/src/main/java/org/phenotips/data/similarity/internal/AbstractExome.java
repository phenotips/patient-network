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

import org.phenotips.data.similarity.Exome;
import org.phenotips.data.similarity.Variant;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

/**
 * Base class for implementing exome-related classes, specifically those that include scored genes with variants.
 *
 * @version $Id $
 */
public abstract class AbstractExome implements Exome
{
    /** The overall score for each gene with a variant. */
    protected Map<String, Double> geneScores;

    /** The variants in each gene. */
    protected Map<String, List<Variant>> variants;

    @Override
    public Set<String> getGenes()
    {
        return Collections.unmodifiableSet(this.variants.keySet());
    }

    @Override
    public Double getGeneScore(String gene)
    {
        return this.geneScores.get(gene);
    }

    @Override
    public List<Variant> getTopVariants(String gene)
    {
        List<Variant> vs = this.variants.get(gene);
        if (vs == null) {
            vs = Collections.emptyList();
        }
        return vs;
    }

    @Override
    public List<String> getTopGenes(int n)
    {
        if (this.geneScores == null || this.geneScores.isEmpty()) {
            return Collections.emptyList();
        }

        // Basic implementation that just sorts all genes and returns the top n
        // TODO: store in a data structure where this is efficient
        final Map<String, Double> scoredGenes = this.geneScores;
        List<String> geneNames = new ArrayList<String>(scoredGenes.keySet());
        Collections.sort(geneNames, new Comparator<String>()
        {
            @Override
            public int compare(String g1, String g2)
            {
                // Reversed to sort by decreasing score
                return Double.compare(scoredGenes.get(g2), scoredGenes.get(g1));
            }
        });

        if (n <= 0 || n > geneNames.size()) {
            return geneNames;
        } else {
            return geneNames.subList(0, n);
        }
    }

    @Override
    public JSONArray toJSON()
    {
        JSONArray geneList = new JSONArray();
        for (String geneName : this.variants.keySet()) {
            JSONObject gene = new JSONObject();
            gene.element("gene", geneName);
            gene.element("score", getGeneScore(geneName));

            JSONArray variantList = new JSONArray();
            List<Variant> vs = this.variants.get(geneName);
            for (Variant v : vs) {
                variantList.add(v.toJSON());
            }
            gene.element("variants", variantList);

            geneList.add(gene);
        }
        return geneList;
    }
}
