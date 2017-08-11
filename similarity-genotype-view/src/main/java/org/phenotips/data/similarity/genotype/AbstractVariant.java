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

import org.phenotips.components.ComponentManagerRegistry;
import org.phenotips.data.similarity.Variant;
import org.phenotips.vocabulary.Vocabulary;
import org.phenotips.vocabulary.VocabularyManager;
import org.phenotips.vocabulary.VocabularyTerm;

import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.component.manager.ComponentManager;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A base implementation of a {@link Variant}.
 *
 * @version $Id$
 * @since 1.0M6
 */
public abstract class AbstractVariant implements Variant
{
    /** Manager to allow access to HGNC vocabulary gene data. */
    private static VocabularyManager vocabularyManager;

    /** Logging helper object. */
    private static Logger logger = LoggerFactory.getLogger(AbstractVariant.class);

    /** See {@link #getChrom()}. */
    protected String chrom;

    /** See {@link #getPosition()}. */
    protected Integer position;

    /** See {@link #getRef()}. */
    protected String ref;

    /** See {@link #getAlt()}. */
    protected String alt;

    /** See {@link #getScore()}. */
    protected Double score;

    /** See {@link #getEffect()}. */
    protected String effect;

    /** See {@link #getGenotype()}. */
    protected String gt;

    /** See {@link #getAnnotation(String)}. */
    protected Map<String, String> annotations = new HashMap<String, String>();

    static {
        VocabularyManager vm = null;
        try {
            ComponentManager ccm = ComponentManagerRegistry.getContextComponentManager();
            vm = ccm.getInstance(VocabularyManager.class);
        } catch (ComponentLookupException e) {
            logger.error("Error loading static components: {}", e.getMessage(), e);
        }
        vocabularyManager = vm;
    }

    /**
     * Set the chromosome of the variant.
     *
     * @param chrom the chromosome string
     */
    protected void setChrom(String chrom)
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
    protected void setPosition(int position)
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
    protected void setGenotype(String ref, String alt, String gt)
    {
        this.ref = ref;
        this.alt = alt;
        this.gt = gt;
    }

    /**
     * Set the cDNA effect of the variant.
     *
     * @param effect the cDNA effect of the variant
     */
    protected void setEffect(String effect)
    {
        this.effect = effect;
    }

    /**
     * Set the score of the variant.
     *
     * @param score the score of the variant in [0.0, 1.0], where 1.0 is more harmful
     */
    protected void setScore(Double score)
    {
        this.score = score;
    }

    @Override
    public Double getScore()
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
        return this.annotations == null ? null : this.annotations.get(key);
    }

    @Override
    public void setAnnotation(String key, String value)
    {
        this.annotations.put(key, value);
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
        result.put("start", getPosition());
        result.put("referenceBases", getRef());
        result.put("alternateBases", getAlt());
        result.put("referenceName", getChrom());
        if (getRef() != null) {
            result.put("end", getPosition() + getRef().length() - 1);
        }
        result.put("zygosity", isHomozygous() ? "homozygous" : "heterozygous");
        result.put("score", getScore());
        result.put("effect", getEffect());

        if (this.annotations != null && this.annotations.size() > 0) {
            JSONObject annotationJson = new JSONObject();
            //"geneScore", "geneSymbol"
            for (Entry<String, String> entry : this.annotations.entrySet()) {
                if ("geneEnsemblId".equals(entry.getKey())) {
                    annotationJson.put("geneSymbol", getGeneSymbol(entry.getValue()));
                } else {
                    annotationJson.put(entry.getKey(), entry.getValue());
                }
            }
            result.put("annotations", annotationJson);
        }
        return result;
    }

    private String getGeneSymbol(String geneEnsemblId)
    {
        String symbol = null;
        if (vocabularyManager != null) {
            Vocabulary hgnc = vocabularyManager.getVocabulary("HGNC");
            VocabularyTerm term = hgnc.getTerm(geneEnsemblId);
            symbol = (term != null) ? (String) term.get("symbol") : null;
        }
        return symbol;
    }
}
