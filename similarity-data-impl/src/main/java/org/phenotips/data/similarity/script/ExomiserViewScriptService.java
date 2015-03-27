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
package org.phenotips.data.similarity.script;

import org.phenotips.data.Patient;
import org.phenotips.data.similarity.Exome;
import org.phenotips.data.similarity.ExomeManager;
import org.phenotips.data.similarity.Variant;

import org.xwiki.component.annotation.Component;
import org.xwiki.script.service.ScriptService;
import org.xwiki.stability.Unstable;

import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

/**
 * Allows management of patient phenotype and genotype matching features.
 *
 * @version $Id$
 * @since
 */
@Unstable
@Component
@Named("patientVariantView")
@Singleton
public class ExomiserViewScriptService implements ScriptService
{
    /** Manager to allow access to patient exome data. */
    @Inject
    @Named("exomiser")
    private ExomeManager exomeManager;

    /**
     * Checks if a patient has a valid Exomiser genotype.
     *
     * @param patient A valid {@link #Patient}
     * @return boolean true iff the patient has a valid Exomiser genotype
     */
    public boolean hasGenotype(Patient patient)
    {
        if (patient == null) {
            return false;
        }
        return exomeManager.getExome(patient) != null;
    }

    /**
     * Outputs the k top genes from a patient as a JSON array.
     *
     * @param patient A valid patient
     * @param g The number of genes to report
     * @param v The maximum number of variants to report per gene
     * @return An array of "g" JSON objects representing the top genes for the patient
     */
    public JSONArray getTopGenesAsJSON(Patient patient, int g, int v)
    {
        JSONArray result = new JSONArray();
        if (patient == null || g < 0) {
            return result;
        }

        Exome patientExome = exomeManager.getExome(patient);
        for (String geneName : patientExome.getTopGenes(g)) {
            JSONObject geneJSON = new JSONObject();
            geneJSON.element("name", geneName);
            geneJSON.element("score", patientExome.getGeneScore(geneName));

            JSONArray variantsJSON = new JSONArray();
            List<Variant> topVariants = patientExome.getTopVariants(geneName);
            for (int j = 0; j < Math.min(v, topVariants.size()); j++) {
                Variant variant = topVariants.get(j);
                if (variant != null) {
                    variantsJSON.add(variant.toJSON());
                }
            }
            geneJSON.element("variants", variantsJSON);
            result.add(geneJSON);
        }
        return result;
    }
}
