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
package org.phenotips.matchingnotification.match.internal;

import org.phenotips.data.Feature;
import org.phenotips.data.Patient;
import org.phenotips.data.permissions.Owner;
import org.phenotips.data.permissions.PatientAccess;
import org.phenotips.data.permissions.PermissionsManager;
import org.phenotips.data.similarity.PatientGenotype;
import org.phenotips.data.similarity.PatientGenotypeManager;
import org.phenotips.vocabulary.VocabularyManager;
import org.phenotips.vocabulary.VocabularyTerm;

import org.xwiki.component.annotation.Component;
import org.xwiki.model.reference.EntityReference;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.web.Utils;

/**
 * @version $Id$
 */
@Component(roles = { DefaultPatientMatchUtils.class })
@Singleton
public class DefaultPatientMatchUtils
{
    @Inject
    private PermissionsManager permissionsManager;

    @Inject
    private PatientGenotypeManager patientGenotypeManager;

    @Inject
    private VocabularyManager vocabularyManager;

    private Logger logger = LoggerFactory.getLogger(DefaultPatientMatchUtils.class);

    /**
     * Returns a map with {key:phenotype id, value:phenotype name} for a given patient.
     *
     * @param patient to get phenotypes from
     * @return phenotypes map
     */
    public Map<String, String> getPhenotypes(Patient patient)
    {
        Map<String, String> map = new TreeMap<>();
        Set<? extends Feature> features = patient.getFeatures();

        for (Feature feature : features) {
            if (feature.isPresent()) {
                map.put(feature.getId(), feature.getName());
            }
        }

        return map;
    }

    /**
     * Returns a string representation of ids of phenotypes given in a map.
     *
     * @param map phenotypes map as returned by {@code getPhenotypes}
     * @return a string representation of genes
     */
    public String getPhenotypesString(Map<String, String> map)
    {
        return this.setToString(map.keySet());
    }

    /**
     * Converts a string representation of phenotypes into a map, with {key:phenotype id, value:phenotype name}.
     *
     * @param string representation of phenotypes
     * @return phenotypes map
     */
    public Map<String, String> phenotypesStringToMap(String string)
    {
        Set<String> ids = this.stringToSet(string);
        Map<String, String> map = new TreeMap<>();
        for (String id : ids) {
            VocabularyTerm term = this.vocabularyManager.resolveTerm(id);
            if (term != null) {
                map.put(id, term.getName());
            }
        }

        return map;
    }

    /**
     * Returns a set of genes for a given patient.
     *
     * @param patient to get genes from
     * @return set of genes
     */
    public Set<String> getGenes(Patient patient)
    {
        PatientGenotype genotype = this.patientGenotypeManager.getGenotype(patient);
        if (genotype != null && genotype.hasGenotypeData()) {
            Set<String> set = genotype.getCandidateGenes();
            return set;
        } else {
            return Collections.emptySet();
        }
    }

    /**
     * Converts a string representation of a set into a set.
     *
     * @param string representation of a set
     * @return a set
     */
    public Set<String> stringToSet(String string)
    {
        if (StringUtils.isEmpty(string)) {
            return Collections.emptySet();
        } else {
            String[] split = string.split(DefaultPatientMatch.SEPARATOR);
            Set<String> set = new HashSet<>(Arrays.asList(split));
            return set;
        }
    }

    /**
     * Converts a set to a string representation.
     *
     * @param set to convert
     * @return a string representation
     */
    public String setToString(Set<String> set)
    {
        if (set == null || set.isEmpty()) {
            return "";
        } else {
            return StringUtils.join(set, DefaultPatientMatch.SEPARATOR);
        }
    }

    // TODO only works for reference patient. Get HREF for matched patient.
    /**
     * Returns the email of the owner of a patient.
     *
     * @param patient to get owner's email from
     * @return email of owner
     */
    public String getOwnerEmail(Patient patient)
    {
        PatientAccess referenceAccess = this.permissionsManager.getPatientAccess(patient);
        Owner owner = referenceAccess.getOwner();
        if (owner == null) {
            return "";
        }
        EntityReference ownerUser = owner.getUser();

        XWikiContext context = Utils.getContext();
        XWiki xwiki = context.getWiki();
        try {
            return xwiki.getDocument(ownerUser, context).getStringValue("email");
        } catch (XWikiException e) {
            this.logger.error("Error reading owner's email for patient {}.", patient.getId(), e);
            return "";
        }
    }

    /**
     * Converts a map representing phenotypes to JSONArray.
     *
     * @param map with phenotypes data
     * @return JSONArray with phenotypes data
     */
    public JSONArray phenotypesToJSONArray(Map<String, String> map) {
        JSONArray array = new JSONArray();
        for (String id : map.keySet()) {
            JSONObject o = new JSONObject();
            o.put("id", id);
            o.put("name", map.get(id));
            array.put(o);
        }
        return array;
    }

    /**
     * Converts a set to JSONArray.
     *
     * @param set to convert to JSONArray
     * @return JSONArray with set data
     */
    public JSONArray setToJSONArray(Set<?> set)
    {
        JSONArray array = new JSONArray();
        for (Object s : set) {
            array.put(s.toString());
        }
        return array;
    }
}
