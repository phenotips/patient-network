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
import org.phenotips.data.similarity.Genotype;
import org.phenotips.data.similarity.Variant;
import org.phenotips.data.similarity.internal.PatientGenotype;

import org.xwiki.component.annotation.Component;
import org.xwiki.script.service.ScriptService;
import org.xwiki.stability.Unstable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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

    /**
     * Gets the top K genes by score from a patient.
     *
     * @param patient A valid patient
     * @param k The wanted number of genes
     * @return A sorted list of k gene names with descending scores
     */
    public List<String> getKTopGenes(Patient patient, int k)
    {
        if (patient == null) {
            return null;
        }
        final Genotype patientGenotype = PatientGenotype.getPatientGenotype(patient);
        List<String> genes = new ArrayList<String>(patientGenotype.getGenes());
        genes.sort(new Comparator<String>()
            {
                @Override
                public int compare(String g1, String g2)
                {
                    // Sort by score, descending
                    return Double.compare(patientGenotype.getGeneScore(g2), patientGenotype.getGeneScore(g1));
                };
            });
        List<String> result = new ArrayList<String>();
        for (int i = 0; i < k; i++) {
            if (genes.get(i) != null) {
                result.add(genes.get(i));
            }
        }
        return result;
    }

    /**
     * Checks if a patient has a valid exomiser genotype.
     *
     * @param patient A valid patient
     * @return boolean value
     */
    public boolean hasGenotype(Patient patient)
    {
        if (patient == null) {
            return false;
        }
        return PatientGenotype.getPatientGenotype(patient) != null;
    }

    /**
     * Outputs the k top genes from a patient as a JSON array.
     *
     * @param patient A valid patient
     * @param g The wanted number of genes
     * @param v The number of variants per gene
     * @return An array of "g" JSON objects representing the top genes for the patient
     */
    public JSONArray getTopGenesAsJSON(Patient patient, int g, int v)
    {
        JSONArray result = new JSONArray();
        if (patient == null) {
            return result;
        }
        List<String> topGeneNames = this.getKTopGenes(patient, g);
        Genotype patientGenotype = PatientGenotype.getPatientGenotype(patient);

        for (String geneName : topGeneNames) {
            JSONObject gene = new JSONObject();
            gene.element("name", geneName);
            gene.element("score", patientGenotype.getGeneScore(geneName));

            JSONArray variants = new JSONArray();
            for (int i = 0; i < v; i++) {
                Variant variant = patientGenotype.getTopVariant(geneName, i);
                if (variant != null) {
                    variants.add(variant.toJSON());
                }
            }
            gene.element("variants", variants);
            result.add(gene);
        }
        return result;
    }
}
