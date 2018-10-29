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
import org.phenotips.data.PatientRepository;
import org.phenotips.data.permissions.AccessLevel;
import org.phenotips.data.permissions.EntityPermissionsManager;
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

import org.json.JSONArray;
import org.json.JSONObject;

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
    @Named("secure")
    private EntityPermissionsManager pm;

    @Inject
    @Named("edit")
    private AccessLevel editAccess;

    /** Manager to allow access to patient exome data. */
    @Inject
    @Named("exomiser")
    private ExomeManager exomeManager;

    @Inject
    private PatientRepository patients;

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
     * @param patientID a valid patient ID
     * @param g the number of genes to report
     * @param v the maximum number of variants to report per gene
     * @return an array of "g" JSON objects representing the top genes for the patient
     */
    public JSONArray getTopGenesAsJSON(String patientID, int g, int v)
    {
        JSONArray result = new JSONArray();
        Patient patient = this.patients.get(patientID);
        if (patient == null || g <= 0) {
            return result;
        }

        Exome patientExome = this.exomeManager.getExome(patient);
        if (patientExome == null) {
            return result;
        }

        int maxGenes = g;
        int maxVars = v;
        if (!this.pm.getEntityAccess(patient).hasAccessLevel(this.editAccess)) {
            maxGenes = Math.min(g, MAXIMUM_UNPRIVILEGED_GENES);
            maxVars = Math.min(v, MAXIMUM_UNPRIVILEGED_VARIANTS);
        }

        for (String geneName : patientExome.getTopGenes(maxGenes)) {
            JSONObject geneJSON = new JSONObject();
            geneJSON.put("name", geneName);
            geneJSON.put("score", patientExome.getGeneScore(geneName));

            JSONArray variantsJSON = new JSONArray();
            List<Variant> topVariants = patientExome.getTopVariants(geneName, maxVars);
            for (int i = 0; i < Math.min(maxVars, topVariants.size()); i++) {
                Variant variant = topVariants.get(i);
                if (variant != null) {
                    variantsJSON.put(variant.toJSON());
                }
            }
            geneJSON.put("variants", variantsJSON);
            result.put(geneJSON);
        }
        return result;
    }
}
