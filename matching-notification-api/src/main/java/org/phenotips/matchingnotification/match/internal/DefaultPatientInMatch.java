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
import org.phenotips.data.Disorder;
import org.phenotips.data.Feature;
import org.phenotips.data.Patient;
import org.phenotips.data.PatientData;
import org.phenotips.data.PatientRepository;
import org.phenotips.data.internal.PhenoTipsDisorder;
import org.phenotips.data.internal.PhenoTipsFeature;
import org.phenotips.data.internal.SolvedData;
import org.phenotips.data.permissions.AccessLevel;
import org.phenotips.data.permissions.EntityAccess;
import org.phenotips.data.permissions.EntityPermissionsManager;
import org.phenotips.data.permissions.Visibility;
import org.phenotips.data.permissions.internal.EntityAccessManager;
import org.phenotips.data.similarity.AccessType;
import org.phenotips.data.similarity.PatientGenotype;
import org.phenotips.data.similarity.PatientGenotypeManager;
import org.phenotips.data.similarity.PatientSimilarityView;
import org.phenotips.data.similarity.internal.DefaultAccessType;
import org.phenotips.groups.Group;
import org.phenotips.groups.GroupManager;
import org.phenotips.matchingnotification.match.PatientInMatch;
import org.phenotips.matchingnotification.match.PatientMatch;
import org.phenotips.vocabulary.Vocabulary;
import org.phenotips.vocabulary.VocabularyManager;
import org.phenotips.vocabulary.VocabularyTerm;
import org.phenotips.vocabulary.internal.solr.SolrVocabularyTerm;

import org.xwiki.component.manager.ComponentManager;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.users.User;
import org.xwiki.users.UserManager;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @version $Id$
 */
@SuppressWarnings({ "checkstyle:ClassFanOutComplexity", "checkstyle:ExecutableStatementCount" })
public class DefaultPatientInMatch implements PatientInMatch
{
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultPatientInMatch.class);

    private static final PatientGenotypeManager PATIENT_GENOTYPE_MANAGER;

    private static final PatientRepository PATIENT_REPOSITORY;

    private static final VocabularyManager VOCABULARY_MANAGER;

    private static final EntityPermissionsManager PERMISSIONS_MANAGER;

    private static final EntityAccessManager ACCESS_HELPER;

    private static final UserManager USER_MANAGER;

    private static final GroupManager GROUP_MANAGER;

    private static final AccessLevel VIEW;

    private static final AccessLevel MATCH;

    private static final Visibility MATCHABLE_VISIBILITY;

    private static final String GENES = "genes";

    private static final String MATCHED_EXOME_GENES = "matchedExomeGenes";

    private static final String PHENOTYPES = "phenotypes";

    private static final String AGE_ON_ONSET = "age_of_onset";

    private static final String MODE_OF_INHERITANCE = "mode_of_inheritance";

    private static final String CONTACT_INFO = "contact_info";

    private static final String DISORDERS = "disorders";

    private String patientId;

    private String serverId;

    private String ageOfOnset;

    private Set<String> modeOfInheritance;

    private String href;

    private Set<String> genes;

    private Set<String> matchedExomeGenes;

    private Set<? extends Feature> phenotypes;

    private Patient patient;

    private PatientGenotype genotype;

    private ContactInfo contactInfo;

    private Set<? extends Disorder> disorders;

    /** The access level the user has to this patient. */
    private AccessLevel access;

    static {
        PatientGenotypeManager pgm = null;
        PatientRepository patientRepository = null;
        VocabularyManager vm = null;
        EntityPermissionsManager pm = null;
        EntityAccessManager pa = null;
        UserManager um = null;
        GroupManager gm = null;
        AccessLevel v = null;
        AccessLevel m = null;
        Visibility vi = null;
        try {
            ComponentManager ccm = ComponentManagerRegistry.getContextComponentManager();
            pgm = ccm.getInstance(PatientGenotypeManager.class);
            patientRepository = ccm.getInstance(PatientRepository.class);
            vm = ccm.getInstance(VocabularyManager.class);
            pm = ccm.getInstance(EntityPermissionsManager.class, "secure");
            pa = ccm.getInstance(EntityAccessManager.class);
            um = ccm.getInstance(UserManager.class);
            gm = ccm.getInstance(GroupManager.class);
            v = ccm.getInstance(AccessLevel.class, "view");
            m = ccm.getInstance(AccessLevel.class, "match");
            vi = ccm.getInstance(Visibility.class, "matchable");
        } catch (Exception e) {
            LOGGER.error("Error loading static components: {}", e.getMessage(), e);
        }
        PATIENT_GENOTYPE_MANAGER = pgm;
        PATIENT_REPOSITORY = patientRepository;
        VOCABULARY_MANAGER = vm;
        PERMISSIONS_MANAGER = pm;
        ACCESS_HELPER = pa;
        USER_MANAGER = um;
        GROUP_MANAGER = gm;
        VIEW = v;
        MATCH = m;
        MATCHABLE_VISIBILITY = vi;
    }

    /**
     * Builds an object from PatientMatch and Patient objects. This is used when a PatientMatch object is built the
     * first time.
     *
     * @param patient the patient that this object represents
     * @param serverId id of server where patient is found
     * @param matchedGenes all matched genes between two patients
     */
    public DefaultPatientInMatch(Patient patient, String serverId, Set<String> matchedGenes)
    {
        this.patient = patient;
        this.patientId = patient.getId();
        this.serverId = serverId;
        this.genotype = PATIENT_GENOTYPE_MANAGER.getGenotype(this.patient);
        this.setAccess();
        this.readDetails(matchedGenes);
    }

    /**
     * Builds an object for a patient from a PatientMatch and the result of a call to getDetailsColumnJSON().
     * This is used when a PatientMatch is retrieved from the DB.
     *
     * @param match the match that contains the patient this object represents
     * @param patientId the id of the patient that this object represents
     * @param serverId id of server where patient is found
     * @param patientDetails the result of a previous call to getDetailsColumnJSON().
     */
    public DefaultPatientInMatch(PatientMatch match, String patientId, String serverId, String patientDetails)
    {
        this.patientId = patientId;
        this.serverId = serverId;
        this.patient = getLocalPatient();
        this.genotype = PATIENT_GENOTYPE_MANAGER.getGenotype(this.patient);
        this.setAccess();
        this.rebuildDetails(patientDetails, match.getHref());
    }

    @Override
    public JSONObject toJSON()
    {
        JSONObject json = new JSONObject();
        json.put("patientId", this.getPatientId());
        json.put("externalId", this.getExternalId());
        json.put("serverId", this.getServerId());
        json.put("emails", this.getEmails());

        SolvedData patientData = this.getSolvedData();
        if (patientData != null) {
            json.put("solved", "1".equals(patientData.getStatus()));
            json.put("pubmedIds", patientData.getPubmedIds());
        }

        if (this.access != null) {
            // FIXME: workaround for incorrect access-setting code in this.setAccess()
            // This JSON goes to the UI, which needs to know correct access level
            if (this.isLocal() && this.patient != null) {
                json.put("access", this.access.getName());
            }
        }

        JSONObject ownershipDetails = this.getOwnership();
        json.put("ownership", ownershipDetails);

        json.put("isOwner",
            ownershipDetails.getBoolean("userIsOwner") || ownershipDetails.getBoolean("userGroupIsOwner"));

        // Merge in all items from details column
        JSONObject detailsColumn = this.getDetailsColumnJSON();
        for (String key : detailsColumn.keySet()) {
            json.put(key, detailsColumn.get(key));
        }

        json.put("hasExomeData", this.hasExomeData());
        if (this.genotype != null) {
            json.put("genesStatus", this.genotype.getGenesStatus());
        }
        return json;
    }

    /*
     * All data exported here should be imported in {@link rebuildDetails()}.
     */
    @Override
    public JSONObject getDetailsColumnJSON()
    {
        JSONObject json = new JSONObject();
        json.put(GENES, this.genes);
        json.put(MATCHED_EXOME_GENES, this.matchedExomeGenes);
        json.put(PHENOTYPES, this.phenotypes);
        json.put(MODE_OF_INHERITANCE, new JSONArray(this.modeOfInheritance));
        json.put(AGE_ON_ONSET, this.ageOfOnset);
        json.put(CONTACT_INFO, (this.contactInfo != null) ? this.contactInfo.toJSON() : null);
        json.put(DISORDERS, this.disorders);
        return json;
    }

    @Override
    public boolean isLocal()
    {
        return StringUtils.isEmpty(this.getServerId());
    }

    @Override
    public Collection<String> getEmails()
    {
        if (this.contactInfo != null) {
            return this.contactInfo.getEmails();
        }
        return Collections.emptyList();
    }

    @Override
    public Set<String> getCandidateGenes()
    {
        return this.genes;
    }

    @Override
    public Set<String> getMatchedExomeGenes()
    {
        return this.matchedExomeGenes;
    }

    @Override
    public Set<? extends Feature> getPhenotypes()
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
        if (isLocal() && this.access.compareTo(VIEW) >= 0) {
            return this.patient.getExternalId();
        }
        return "";
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

    @Override
    public AccessType getAccessType()
    {
        EntityAccess entityAccess = PERMISSIONS_MANAGER.getEntityAccess(this.patient);
        AccessLevel useAccess = entityAccess.getAccessLevel();
        Visibility visibility = entityAccess.getVisibility();
        if (useAccess.compareTo(MATCH) < 0 && visibility.compareTo(MATCHABLE_VISIBILITY) >= 0) {
            // matches which are not matchable are filtered out elsewhere. But for MME requests which are
            // done as a guest user (who has no access to all patients), need to make sure the match can
            // be processed and returned, for which there should be at least "match" access level
            useAccess = MATCH;
        }

        return new DefaultAccessType(this.access, VIEW, MATCH);
    }

    @Override
    public String getGenesStatus()
    {
        // if the patient is remote, we return false for now
        if (this.patient == null) {
            return null;
        }
        if (this.genotype == null) {
            return null;
        }
        return this.genotype.getGenesStatus();
    }

    @Override
    public ContactInfo getContactInfo()
    {
        return this.contactInfo;
    }

    @Override
    public Set<? extends Disorder> getDisorders()
    {
        return this.disorders;
    }

    /*
     * Data read from {@code patientDetails} was exported in {@link getDetailsColumnJSON}. However, it is possible that
     * some data is missing in case more details added in newer versions. So, it is ok for some values to be missing
     * (but not genes or phenotypes).
     */
    private void rebuildDetails(String patientDetails, String oldMMEContactData)
    {
        try {
            JSONObject json = new JSONObject(patientDetails);

            this.genes = getGeneSymbols(jsonArrayToSet(json.getJSONArray(GENES)));
            this.matchedExomeGenes = getGeneSymbols(jsonArrayToSet(json.optJSONArray(MATCHED_EXOME_GENES)));
            this.phenotypes = readFeatures(json.optJSONArray(PHENOTYPES));
            this.ageOfOnset = json.getString(AGE_ON_ONSET);
            this.modeOfInheritance = jsonArrayToSet(json.getJSONArray(MODE_OF_INHERITANCE));
            this.contactInfo = rebuildContactInfo(json.optJSONObject(CONTACT_INFO), oldMMEContactData);
            this.disorders = readDisorders(json.optJSONArray(DISORDERS));
        } catch (Exception ex) {
            LOGGER.error("Error parsing patientDetails JSON: {}", ex.getMessage(), ex);
        }
    }

    // Returns an unmodifiable set of Strings
    private static Set<String> jsonArrayToSet(JSONArray jsonArray)
    {
        if (jsonArray == null) {
            return Collections.emptySet();
        }
        Set<String> set = new HashSet<>();
        Iterator<Object> iterator = jsonArray.iterator();
        while (iterator.hasNext()) {
            set.add((String) iterator.next());
        }
        return Collections.unmodifiableSet(set);
    }

    private void readDetails(Set<String> matchedGenes)
    {
        this.genes = this.getGenes(this.patient);
        this.matchedExomeGenes = this.getMatchedExomeGenes(matchedGenes);
        this.phenotypes = this.patient.getFeatures();

        PatientData<List<SolrVocabularyTerm>> globalControllers = this.patient.getData("global-qualifiers");
        this.ageOfOnset = this.getAgeOfOnset(globalControllers);
        this.modeOfInheritance = this.getModeOfInheritance(globalControllers);
        this.contactInfo = this.populateContactInfo();
        this.disorders = this.getClinicalDisorders(this.patient);
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

    private Set<String> getMatchedExomeGenes(Set<String> matchedGenes)
    {
        if (this.hasExomeData()) {
            // we assume that matched exome genes are all that are not candidate/solved
            Set<String> exomeGenes = matchedGenes.stream()
                .filter(gene -> !this.genes.contains(gene))
                .collect(Collectors.toSet());
            Set<String> set = getGeneSymbols(exomeGenes);
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

    private SolvedData getSolvedData()
    {
        // if the patient is remote
        if (this.patient == null) {
            return null;
        }

        PatientData<SolvedData> data = this.patient.getData("solved");
        if (data == null) {
            return null;
        }

        SolvedData patientData = data.getValue();
        return patientData;
    }

    private Patient getLocalPatient()
    {
        if (this.isLocal()) {
            return PATIENT_REPOSITORY.get(this.patientId);
        } else {
            return null;
        }
    }

    private ContactInfo populateContactInfo()
    {
        if (this.patient != null && this.patient.getData("contact").size() > 0) {
            PatientData<ContactInfo> data = this.patient.getData("contact");
            if (data != null && data.size() > 0) {
                return data.get(0);
            }
        }
        return null;
    }

    private Set<? extends Disorder> getClinicalDisorders(Patient patient)
    {
        if (!this.isLocal()) {
            return patient.getDisorders();
        }

        Set<Disorder> clinicalDisorders = new TreeSet<>();

        PatientData<Disorder> data = patient.getData("clinical-diagnosis");
        if (data != null) {
            Iterator<Disorder> iterator = data.iterator();
            while (iterator.hasNext()) {
                Disorder disorder = iterator.next();
                clinicalDisorders.add(disorder);
            }
        }

        for (Disorder disease : patient.getDisorders()) {
            if (!StringUtils.isBlank(disease.getId())) {
                clinicalDisorders.add(disease);
            }
        }
        return Collections.unmodifiableSet(clinicalDisorders);
    }

    private ContactInfo rebuildContactInfo(JSONObject contact, String oldMMEContactData)
    {
        ContactInfo result = this.populateContactInfo();
        if (result != null) {
            return result;
        } else if (contact != null) {
            ContactInfo.Builder contactBuilder = new ContactInfo.Builder();
            contactBuilder.withUserId(contact.optString("id"));
            contactBuilder.withName(contact.optString("name"));
            contactBuilder.withInstitution(contact.optString("institution"));
            contactBuilder.withUrl(contact.optString("url"));
            contactBuilder.withEmail(contact.optString("email"));
            return contactBuilder.build();
        } else if (StringUtils.isNotEmpty(oldMMEContactData)) {
            // TODO: discuss what better algorithm/package can be use to split emails
            // MME emails may be of the form "mailto:email1,email2", need to parse that
            List<String> emails = new LinkedList<>();
            if (oldMMEContactData.startsWith("mailto:")) {
                String emailList = oldMMEContactData.replace("mailto:", "");
                emails.addAll(Arrays.asList(emailList.split(",|;")));
            } else {
                emails.add(oldMMEContactData);
            }
            ContactInfo.Builder contactBuilder = new ContactInfo.Builder();
            contactBuilder.withEmails(emails);
            return contactBuilder.build();
        }
        return null;
    }

    private void setAccess()
    {
        try {
            if (!this.isLocal() || this.patient == null) {
                //
                // FIXME: this is wrong, while server code has "view" access to the in-memory
                // copy of the remote patient, from the UI's point of view current user
                // does NOT have access to the patient. So need to investigate why this
                // fix was needed and do a proper fix. Maybe we no longer need this after
                // match obfuscation was removed.

                // Remote patient, assume we have access
                this.access = PERMISSIONS_MANAGER.resolveAccessLevel("view");
            } else if (this.patient instanceof PatientSimilarityView) {
                this.access = ((PatientSimilarityView) this.patient).getAccess();
            } else {
                this.access = PERMISSIONS_MANAGER.getEntityAccess(this.patient).getAccessLevel();
            }
        } catch (Exception e) {
            this.access = PERMISSIONS_MANAGER.resolveAccessLevel("none");
        }
    }

    /**
     * Checks if current user or one of his groups is owner of one patient.
     */
    private JSONObject getOwnership()
    {
        boolean userIsOwner = false;
        boolean userGroupIsOwner = false;
        boolean isPublic = false;

        if (this.patient != null) {
            User currentUser = USER_MANAGER.getCurrentUser();
            DocumentReference userRef = currentUser.getProfileDocument();
            EntityReference ownerRef = ACCESS_HELPER.getOwner(this.patient).getUser();
            if (userRef.equals(ownerRef)) {
                userIsOwner = true;
            } else {
                Set<Group> userGroups = GROUP_MANAGER.getGroupsForUser(currentUser);
                for (Group group : userGroups) {
                    DocumentReference groupRef = group.getReference();
                    if (groupRef.equals(ownerRef)) {
                        userGroupIsOwner = true;
                        break;
                    }
                }
            }

            // check open/public status
            String visibility = PERMISSIONS_MANAGER.getEntityAccess(this.patient).getVisibility().getName();
            if ("open".equals(visibility) || "public".equals(visibility)) {
                isPublic = true;
            }
        }

        JSONObject ownershipJSON = new JSONObject();
        ownershipJSON.put("userIsOwner", userIsOwner);
        ownershipJSON.put("userGroupIsOwner", userGroupIsOwner);
        ownershipJSON.put("publicRecord", isPublic);
        return ownershipJSON;
    }

    // read patient features from match "details" JSONArray saved in db
    private Set<Feature> readFeatures(JSONArray phenotypesJson)
    {
        Set<Feature> features = new HashSet<>();
        if (phenotypesJson == null) {
            return features;
        }

        for (Object object : phenotypesJson) {
            JSONObject item = (JSONObject) object;
            Feature phenotipsFeature = new PhenoTipsFeature(item);
            features.add(phenotipsFeature);
        }
        return features;
    }

    private Set<Disorder> readDisorders(JSONArray disordersJson)
    {
        Set<Disorder> disorderSet = new HashSet<>();
        if (disordersJson == null) {
            return disorderSet;
        }

        for (Object object : disordersJson) {
            JSONObject jsonDisorder = (JSONObject) object;
            Disorder disorder = new PhenoTipsDisorder(jsonDisorder);
            disorderSet.add(disorder);
        }
        return disorderSet;
    }
}
