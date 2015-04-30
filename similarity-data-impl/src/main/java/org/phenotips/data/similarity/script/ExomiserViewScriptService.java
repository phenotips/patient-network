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
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
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
     * @param k the wanted number of genes
     * @return a sorted list of k gene names with descending scores
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
     * @param patient a valid patient
     * @param g the number of genes to report
     * @param v the maximum number of variants to report per gene
     * @return an array of "g" JSON objects representing the top genes for the patient
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
            boolean restrictVariants = (!this.pm.getPatientAccess(patient).hasAccessLevel(this.editAccess)
                && v > MAXIMUM_UNPRIVILEGED_VARIANTS);
            int n = restrictVariants ? MAXIMUM_UNPRIVILEGED_VARIANTS : v;
            List<Variant> topVariants = patientExome.getTopVariants(geneName);
            n = Math.min(n, topVariants.size());
            for (int i = 0; i < n; i++) {
                Variant variant = topVariants.get(i);
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
