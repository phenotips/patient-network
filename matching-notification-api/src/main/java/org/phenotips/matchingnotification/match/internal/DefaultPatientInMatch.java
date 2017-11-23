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

import org.phenotips.components.ComponentManagerRegistry;
import org.phenotips.data.ContactInfo;
import org.phenotips.data.Patient;
import org.phenotips.data.PatientData;
import org.phenotips.data.PatientRepository;
import org.phenotips.data.permissions.AccessLevel;
import org.phenotips.data.permissions.EntityPermissionsManager;
import org.phenotips.data.similarity.PatientGenotype;
import org.phenotips.data.similarity.PatientGenotypeManager;
import org.phenotips.data.similarity.PatientSimilarityView;
import org.phenotips.matchingnotification.match.PatientInMatch;
import org.phenotips.matchingnotification.match.PhenotypesMap;
import org.phenotips.matchingnotification.notification.PatientMatchNotifier;
import org.phenotips.vocabulary.Vocabulary;
import org.phenotips.vocabulary.VocabularyManager;
import org.phenotips.vocabulary.VocabularyTerm;
import org.phenotips.vocabulary.internal.solr.SolrVocabularyTerm;

import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.component.manager.ComponentManager;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @version $Id$
 */
public class DefaultPatientInMatch implements PatientInMatch
{
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultPatientInMatch.class);

    private static final PatientMatchNotifier NOTIFIER;

    private static final PatientGenotypeManager PATIENT_GENOTYPE_MANAGER;

    private static final PatientRepository PATIENT_REPOSITORY;

    private static final VocabularyManager VOCABULARY_MANAGER;

    private static final EntityPermissionsManager PERMISSIONS_MANAGER;

    private static final String GENES = "genes";

    private static final String PHENOTYPES = "phenotypes";

    private static final String AGE_ON_ONSET = "age_of_onset";

    private static final String MODE_OF_INHERITANCE = "mode_of_inheritance";

    private String patientId;

    private String serverId;

    private String ageOfOnset;

    private Set<String> modeOfInheritance;

    private String href;

    private Set<String> genes;

    private PhenotypesMap phenotypes;

    private Patient patient;

    private PatientGenotype genotype;

    /** The access level the user has to this patient. */
    private AccessLevel access;

    static {
        PatientMatchNotifier notifier = null;
        PatientGenotypeManager pgm = null;
        PatientRepository patientRepository = null;
        VocabularyManager vm = null;
        EntityPermissionsManager pm = null;
        try {
            ComponentManager ccm = ComponentManagerRegistry.getContextComponentManager();
            notifier = ccm.getInstance(PatientMatchNotifier.class);
            pgm = ccm.getInstance(PatientGenotypeManager.class);
            patientRepository = ccm.getInstance(PatientRepository.class);
            vm = ccm.getInstance(VocabularyManager.class);
            pm = ccm.getInstance(EntityPermissionsManager.class);
        } catch (ComponentLookupException e) {
            LOGGER.error("Error loading static components: {}", e.getMessage(), e);
        }
        NOTIFIER = notifier;
        PATIENT_GENOTYPE_MANAGER = pgm;
        PATIENT_REPOSITORY = patientRepository;
        VOCABULARY_MANAGER = vm;
        PERMISSIONS_MANAGER = pm;
    }

    /**
     * Builds an object from PatientMatch and Patient objects. This is used when a PatientMatch object is built the
     * first time.
     *
     * @param patient the patient that this object represents
     * @param serverId id of server where patient is found
     */
    public DefaultPatientInMatch(Patient patient, String serverId)
    {
        this.patient = patient;
        this.patientId = patient.getId();
        this.serverId = serverId;
        this.genotype = PATIENT_GENOTYPE_MANAGER.getGenotype(this.patient);
        setAccess();
        this.populateContactInfo(null);
        this.readDetails(patient);
    }

    /**
     * Builds an object for a patient from a PatientMatch and the result of a call to getDetailsColumn. This is used
     * when a PatientMatch is retrieved from the DB.
     *
     * @param href the href to remote patient that was stored in db in match object, null if match is local
     * @param patientId the id of the patient that this object represents
     * @param serverId id of server where patient is found
     * @param patientDetails the result of a previous call to getDetailsColumn.
     */
    public DefaultPatientInMatch(String href, String patientId, String serverId, String patientDetails)
    {
        this.patientId = patientId;
        this.serverId = serverId;
        this.patient = getLocalPatient();
        this.genotype = PATIENT_GENOTYPE_MANAGER.getGenotype(this.patient);
        setAccess();
        populateContactInfo(href);
        this.rebuildDetails(patientDetails);
    }

    @Override
    public JSONObject toJSON()
    {
        JSONObject json = new JSONObject();
        json.put("patientId", this.getPatientId());
        json.put("externalId", this.getExternalId());
        json.put("serverId", this.getServerId());
        json.put("emails", this.getEmails());
        if (this.access != null) {
            json.put("access", this.access.getName());
        }

        // Merge in all items from details column
        JSONObject detailsColumn = this.getDetailsColumnJSON();
        for (String key : detailsColumn.keySet()) {
            json.put(key, detailsColumn.get(key));
        }

        json.put("hasExomeData", this.hasExomeData());
        json.put("genesStatus", this.genotype.getGenesStatus());

        return json;
    }

    /*
     * All data exported here should be imported in {@link rebuildDetails()}.
     */
    @Override
    public String getDetailsColumn()
    {
        return getDetailsColumnJSON().toString();
    }

    private JSONObject getDetailsColumnJSON()
    {
        JSONObject json = new JSONObject();
        json.put(GENES, this.genes);
        json.put(PHENOTYPES, this.phenotypes.toJSON());
        json.put(MODE_OF_INHERITANCE, new JSONArray(this.modeOfInheritance));
        json.put(AGE_ON_ONSET, this.ageOfOnset);
        return json;
    }

    @Override
    public boolean isLocal()
    {
        return this.getServerId() == null;
    }

    @Override
    public Collection<String> getEmails()
    {
        Collection<String> emails = new LinkedList<>();
        if (this.isLocal()) {
            // Note: The patient is not saved because sometimes (often?) this method is run not on an object that
            // was just created, but on one created from a column from the DB. In this case, we might not have
            // a Patient object when the patient is remote.
            emails.addAll(NOTIFIER.getNotificationEmailsForPatient(this.patient));
        } else {
            if (StringUtils.isNotEmpty(this.href)) {
                emails.add(this.href);
            }
        }
        return emails;
    }

    @Override
    public Set<String> getCandidateGenes()
    {
        return this.genes;
    }

    @Override
    public PhenotypesMap getPhenotypes()
    {
        return this.phenotypes;
    }

    @Override
    public String getPatientId()
    {
        return this.patientId;
    }

    @Override
    public String getExternalId()
    {
        return isLocal() ? this.patient.getExternalId() : "";
    }

    @Override
    public String getServerId()
    {
        return this.serverId;
    }

    @Override
    public String getAgeOfOnset()
    {
        return this.ageOfOnset;
    }

    @Override
    public Collection<String> getModeOfInheritance()
    {
        return this.modeOfInheritance;
    }

    @Override
    public Patient getPatient()
    {
        return this.patient;
    }

    @Override
    public String getHref()
    {
        return this.href;
    }

    @Override
    public boolean hasExomeData()
    {
        // if the patient is remote, we return false for now
        if (this.patient == null) {
            return false;
        }
        return this.genotype != null && this.genotype.hasExomeData();
    }

    @Override
    public AccessLevel getAccess()
    {
        return this.access;
    }

    /*
     * Data read from {@code patientDetails} was exported in {@link getDetailsColumn}. However, it is possible that some
     * data is missing in case more details added in newer versions. So, it is ok for some values to be missing (but not
     * genes or phenotypes).
     */
    private void rebuildDetails(String patientDetails)
    {
        JSONObject json = new JSONObject(patientDetails);

        this.genes = getGeneSymbols(jsonArrayToSet(json.getJSONArray(GENES)));
        this.phenotypes = new DefaultPhenotypesMap(json.getJSONObject(PHENOTYPES));
        this.ageOfOnset = json.getString(AGE_ON_ONSET);
        this.modeOfInheritance = jsonArrayToSet(json.getJSONArray(MODE_OF_INHERITANCE));
    }

    // Returns an unmodifiable set of Strings
    private static Set<String> jsonArrayToSet(JSONArray jsonArray)
    {
        Set<String> set = new HashSet<>();
        Iterator<Object> iterator = jsonArray.iterator();
        while (iterator.hasNext()) {
            set.add((String) iterator.next());
        }
        return Collections.unmodifiableSet(set);
    }

    private void readDetails(Patient patient)
    {
        this.genes = this.getGenes(patient);
        this.phenotypes = new DefaultPhenotypesMap(patient);

        PatientData<List<SolrVocabularyTerm>> globalControllers = patient.getData("global-qualifiers");
        this.ageOfOnset = this.getAgeOfOnset(globalControllers);
        this.modeOfInheritance = this.getModeOfInheritance(globalControllers);
    }

    private Set<String> getGenes(Patient patient)
    {
        if (this.genotype != null && this.genotype.hasGenotypeData()) {
            Set<String> set = getGeneSymbols(this.genotype.getCandidateGenes());
            return Collections.unmodifiableSet(set);
        } else {
            return Collections.emptySet();
        }
    }

    // convert gene Ensembl IDs to gene symbols
    private Set<String> getGeneSymbols(Set<String> set)
    {
        Set<String> result = new HashSet<>();
        Vocabulary hgnc = VOCABULARY_MANAGER.getVocabulary("HGNC");
        for (String geneEnsemblId : set) {
            VocabularyTerm term = hgnc.getTerm(geneEnsemblId);
            String symbol = (term != null) ? (String) term.get("symbol") : null;
            symbol = StringUtils.isBlank(symbol) ? geneEnsemblId : symbol;
            result.add(symbol);
        }
        return result;
    }

    private Set<String> getModeOfInheritance(PatientData<List<SolrVocabularyTerm>> globalControllers)
    {
        Set<String> modes = new HashSet<>();
        if (globalControllers != null) {
            List<SolrVocabularyTerm> modeTermList = globalControllers.get("global_mode_of_inheritance");
            for (SolrVocabularyTerm term : modeTermList) {
                modes.add(term.getName());
            }
        }
        return Collections.unmodifiableSet(modes);
    }

    private String getAgeOfOnset(PatientData<List<SolrVocabularyTerm>> globalControllers)
    {
        if (globalControllers != null) {
            List<SolrVocabularyTerm> modeTermList = globalControllers.get("global_age_of_onset");
            if (modeTermList.size() == 1) {
                return modeTermList.get(0).getName();
            }
        }
        return "";
    }

    private Patient getLocalPatient()
    {
        if (this.isLocal()) {
            return PATIENT_REPOSITORY.get(this.patientId);
        } else {
            return null;
        }
    }

    private void populateContactInfo(String href)
    {
        this.href = null;
        // if the patient is remote, we use whatever is passed by from DB
        if (this.patient == null) {
            this.href = href;
            return;
        }
        PatientData<ContactInfo> data = this.patient.getData("contact");
        if (data != null && data.size() > 0) {
            ContactInfo contact = data.get(0);
            if (contact != null) {
                this.href = contact.getUrl();
            }
        }
    }

    private void setAccess()
    {
        if (this.patient == null) {
            // Remote patient, assume we have access
            this.access = PERMISSIONS_MANAGER.resolveAccessLevel("view");
        } else if (this.patient instanceof PatientSimilarityView) {
            this.access = ((PatientSimilarityView) this.patient).getAccess();
        } else {
            this.access = PERMISSIONS_MANAGER.getEntityAccess(this.patient).getAccessLevel();
        }
    }
}
