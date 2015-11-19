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
package org.phenotips.data.similarity.script;

import org.phenotips.data.Patient;
import org.phenotips.data.permissions.AccessLevel;
import org.phenotips.data.permissions.PermissionsManager;
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
 * @since 1.0M5
 */
@Unstable
@Component
@Named("patientVariantView")
@Singleton
public class ExomiserViewScriptService implements ScriptService
{
    private static final int MAXIMUM_UNPRIVILEGED_GENES = 10;

    private static final int MAXIMUM_UNPRIVILEGED_VARIANTS = 5;

    @Inject
    private PermissionsManager pm;

    @Inject
    @Named("edit")
    private AccessLevel editAccess;

    /** Manager to allow access to patient exome data. */
    @Inject
    @Named("exomiser")
    private ExomeManager exomeManager;

    /**
     * Checks if a patient has a valid Exomiser genotype.
     *
     * @param patient a valid {@link Patient}
     * @return boolean {@code true} iff the patient has a valid Exomiser genotype
     */
    public boolean hasGenotype(Patient patient)
    {
        if (patient == null) {
            return false;
        }
        return this.exomeManager.getExome(patient) != null;
    }

    /**
     * Outputs the k top genes from a patient as a JSON array.
     *
     * @param patient a valid patient
     * @param g the number of genes to report
     * @param v the maximum number of variants to report per gene
     * @return an array of "g" JSON objects representing the top genes for the patient
     */
    public JSONArray getTopGenesAsJSON(Patient patient, int g, int v)
    {
        JSONArray variantsJSON = new JSONArray();

        if (patient == null || g < 0) {
            return variantsJSON;
        }

        boolean restrictGenes =
            (!this.pm.getPatientAccess(patient).hasAccessLevel(this.editAccess) && g > MAXIMUM_UNPRIVILEGED_GENES);
        int maxGenes = restrictGenes ? MAXIMUM_UNPRIVILEGED_GENES : g;

        Exome patientExome = this.exomeManager.getExome(patient);

        for (String geneName : patientExome.getTopGenes(maxGenes)) {

            Double geneScore = patientExome.getGeneScore(geneName);


            List<Variant> topVariants = patientExome.getTopVariants(geneName);

            boolean restrictVariants = (!this.pm.getPatientAccess(patient).hasAccessLevel(this.editAccess)
                && v > MAXIMUM_UNPRIVILEGED_VARIANTS);
            int maxVars = restrictVariants ? MAXIMUM_UNPRIVILEGED_VARIANTS : v;
            maxVars = Math.min(maxVars, topVariants.size());

            for (int i = 0; i < maxVars; i++) {
                Variant variant = topVariants.get(i);
                if (variant != null) {
                    variantsJSON.add(this.mapToGAVariantJSON(variant, geneName, geneScore));
                }
            }
        }
        return variantsJSON;
    }

    private JSONObject mapToGAVariantJSON(Variant variant, String geneName, Double geneScore)
    {
        JSONObject resultJson = new JSONObject();
        resultJson.put("start", variant.getPosition());
        resultJson.put("referenceBases", variant.getRef());
        resultJson.put("alternateBases", variant.getAlt());
        resultJson.put("referenceName", variant.getChrom());
        resultJson.put("end", variant.getPosition() + variant.getAlt().length());

        JSONObject infoJson = new JSONObject();
        infoJson.put("EXOMISER_VARIANT_SCORE", variant.getScore());
        infoJson.put("EXOMISER_GENE_VARIANT_SCORE", geneScore);
        infoJson.put("GENE_EFFECT", variant.getAnnotation("FUNCTIONAL_CLASS"));
        infoJson.put("GENE", geneName);

        resultJson.put("info", infoJson);
        return resultJson;
    }
}
