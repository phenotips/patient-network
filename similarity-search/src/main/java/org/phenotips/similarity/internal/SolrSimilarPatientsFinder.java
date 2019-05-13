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
package org.phenotips.similarity.internal;

import org.phenotips.consents.ConsentManager;
import org.phenotips.data.Feature;
import org.phenotips.data.Gene;
import org.phenotips.data.Patient;
import org.phenotips.data.PatientData;
import org.phenotips.data.PatientRepository;
import org.phenotips.data.permissions.EntityAccess;
import org.phenotips.data.permissions.EntityPermissionsManager;
import org.phenotips.data.permissions.Visibility;
import org.phenotips.data.similarity.PatientSimilarityView;
import org.phenotips.data.similarity.PatientSimilarityViewFactory;
import org.phenotips.similarity.SimilarPatientsFinder;
import org.phenotips.studies.family.Family;
import org.phenotips.studies.family.FamilyRepository;
import org.phenotips.vocabulary.SolrCoreContainerHandler;
import org.phenotips.vocabulary.VocabularyManager;
import org.phenotips.vocabulary.VocabularyTerm;

import org.xwiki.component.annotation.Component;
import org.xwiki.component.phase.Initializable;
import org.xwiki.component.phase.InitializationException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.embedded.EmbeddedSolrServer;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.params.CommonParams;
import org.slf4j.Logger;

/**
 * Implementation for {@link SimilarPatientsFinder} based on Solr indexing of existing patients.
 *
 * @version $Id$
 * @since 1.0M1
 */
@Component
@Singleton
@SuppressWarnings("checkstyle:ClassFanOutComplexity")
public class SolrSimilarPatientsFinder implements SimilarPatientsFinder, Initializable
{
    /** The number of records that have similar phenotypes that SOLR should find. */
    private static final int SOLR_SEED_QUERY_SIZE_FOR_PHENOTYPE_SIMILARITY = 50;

    private static final double MIN_SCORE_TO_CONSIDER_NON_ZERO = 0.001;

    /** Logging helper object. */
    @Inject
    private Logger logger;

    /** Provides access to patient data. */
    @Inject
    private PatientRepository patients;

    /** Provides access to patients families data. */
    @Inject
    private FamilyRepository familyRepository;

    /** The minimal visibility level needed for including a patient in the result. */
    @Inject
    @Named("matchable")
    private Visibility visibilityLevelThreshold;

    /** Allows to make a secure pair of patients. */
    @Inject
    @Named("restricted")
    private PatientSimilarityViewFactory factory;

    @Inject
    private VocabularyManager vocabularies;

    @Inject
    private SolrCoreContainerHandler cores;

    @Inject
    private ConsentManager consentManager;

    @Inject
    private EntityPermissionsManager permissionsManager;

    /** The Solr server instance used. */
    private SolrClient server;

    @Override
    public void initialize() throws InitializationException
    {
        this.server = new EmbeddedSolrServer(this.cores.getContainer(), "patients");
    }

    @Override
    public List<PatientSimilarityView> findSimilarPatients(Patient referencePatient)
    {
        return findSimilarPatients(referencePatient, null);
    }

    @Override
    public List<PatientSimilarityView> findSimilarPatients(Patient referencePatient, String requiredConsentId)
    {
        return find(referencePatient, requiredConsentId, false);
    }

    @Override
    public List<PatientSimilarityView> findSimilarPrototypes(Patient referencePatient)
    {
        return find(referencePatient, null, true);
    }

    private List<PatientSimilarityView> find(Patient referencePatient, String requiredConsentId, boolean prototypes)
    {
        this.logger.debug("Searching for patients similar to [{}] using visibility level {}",
            referencePatient.getId(), this.visibilityLevelThreshold.getName());

        Set<String> patientDocuments = this.findAllMatchingPatients(referencePatient, prototypes);
        this.logger.debug("Found {} potential matches", patientDocuments.size());

        List<PatientSimilarityView> results = new ArrayList<>(patientDocuments.size());
        if (patientDocuments.size() == 0) {
            return results;
        }

        // get reference patient's family once, to be used multiple times in the loop below
        Family family = (referencePatient.getDocumentReference() == null)
                        ? null
                        : this.familyRepository.getFamilyForPatient(referencePatient);

        for (String patientDocumentName : patientDocuments) {
            Patient matchPatient = this.patients.get(patientDocumentName);

            if (filterPatient(matchPatient, family, requiredConsentId)) {
                continue;
            }

            PatientSimilarityView result = this.factory.makeSimilarPatient(matchPatient, referencePatient);
            this.logger.debug("Found match: [{}] with score: {}", patientDocumentName, result.getScore());
            if (result.getScore() > MIN_SCORE_TO_CONSIDER_NON_ZERO) {
                results.add(result);
            }
        }

        // Sort by score
        Collections.sort(results, new Comparator<PatientSimilarityView>()
        {
            @Override
            public int compare(PatientSimilarityView o1, PatientSimilarityView o2)
            {
                double score1 = o1.getScore();
                double score2 = o2.getScore();
                return (int) Math.signum(score2 - score1);
            }
        });

        return results;
    }

    /**
     * Finds ALL patients with matching genes and at most SOLR_SEED_QUERY_SIZE_FOR_PHENOTYPE_SIMILARITY
     * patients with matching phenotypes (selected as most similar according to SOLR).
     *
     * No check is performed to see if a document is accessible, matchable, etc.
     *
     * @param referencePatient reference patient to find matches for
     * @param prototypes if true, OMIM disorder prototypes are included in results
     * @return a list of patient document names for patients which are similar to the reference patient
     *         (patient itself is not returned)
     */
    private Set<String> findAllMatchingPatients(Patient referencePatient, boolean prototypes)
    {
        Set<String> results = new HashSet<>();

        // 1. find at most SOLR_SEED_QUERY_SIZE_FOR_PHENOTYPE_SIMILARITY patients matching by
        //    phenotypes (disregarding all other data)
        SolrQuery queryP = generatePhenotypeQuery(referencePatient, prototypes);
        if (queryP != null) {
            queryP.setRows(SOLR_SEED_QUERY_SIZE_FOR_PHENOTYPE_SIMILARITY);
            SolrDocumentList docsMatchedOnPhenotypes = search(queryP);
            this.logger.error("Found {} potential matches using phenotype search", docsMatchedOnPhenotypes.size());

            for (SolrDocument doc : docsMatchedOnPhenotypes) {
                results.add((String) doc.getFieldValue("document"));
            }
        }

        // 2. find all patients with matching genes (disregarding all other data),
        //    and merge the resulting list of patients with the patients found in step 1.
        SolrQuery queryG = generateGenotypeQuery(referencePatient, prototypes);
        if (queryG != null) {

            // there should be no limit on the number of returned "genetic" matches,
            // but unfortunately SOLR has a built-in limit of 10, and there is no way to
            // completely disable the limit. So using DB size as the limit for the number of rows
            queryG.setRows(this.getSolrIndexSize().intValue());

            SolrDocumentList docsMatchedOnGenotype = search(queryG);
            this.logger.error("Found {} potential matches using genotype search", docsMatchedOnGenotype.size());

            for (SolrDocument doc : docsMatchedOnGenotype) {
                results.add((String) doc.getFieldValue("document"));
            }
        }

        return results;
    }

    private boolean filterPatient(Patient matchPatient, Family family, String requiredConsentId)
    {
        if (matchPatient == null) {
            // Leftover patient in the index, should be removed
            return true;
        }
        // filter out patients from the same family
        if (family != null && family.isMember(matchPatient)) {
            return true;
        }
        // check consents, if required
        if (requiredConsentId != null && !this.consentManager.hasConsent(matchPatient, requiredConsentId)) {
            return true;
        }

        EntityAccess access = this.permissionsManager.getEntityAccess(matchPatient);
        // filter out patients with visibility level less than defined visibility level threshold
        Visibility patientVisibility = access.getVisibility();
        if (this.visibilityLevelThreshold.compareTo(patientVisibility) > 0) {
            return true;
        }
        return false;
    }

    /**
     * Generates a Solr query that tries to match patients similar to the reference
     * based on reference patient phenotypes.
     *
     * @param referencePatient the reference patient
     * @return a query populated with terms from the patient phenotype
     */
    private SolrQuery generatePhenotypeQuery(Patient referencePatient, boolean prototypes)
    {
        // Whitespace-delimiter terms querying the extended phenotype field
        Collection<String> termIds = this.getPresentPhenotypeTerms(referencePatient);
        if (termIds.isEmpty()) {
            return null;
        }

        StringBuilder q = this.generateBaseQuery(referencePatient, prototypes);
        q.append(" extended_phenotype:" + getQueryFromTerms(termIds));

        SolrQuery query = new SolrQuery();
        query.add(CommonParams.Q, q.toString());
        this.logger.error("SOLRQUERY generated for matching patient based on phenotypes [{}]: {}",
                referencePatient.getId(), query.toString());
        return query;
    }

    /**
     * Generates a Solr query that tries to match patients similar to the reference
     * based on reference patient genes.
     *
     * @param referencePatient the reference patient
     * @return a query populated with terms from the patient genes
     */
    private SolrQuery generateGenotypeQuery(Patient referencePatient, boolean prototypes)
    {
        Collection<String> genesToSearch = this.getGenesToSearch(referencePatient);
        if (genesToSearch.isEmpty()) {
            return null;
        }

        StringBuilder q = this.generateBaseQuery(referencePatient, prototypes);
        String geneQuery = getQueryFromTerms(genesToSearch);
        q.append(" solved_genes:" + geneQuery);
        q.append(" candidate_genes:" + geneQuery);

        SolrQuery query = new SolrQuery();
        query.add(CommonParams.Q, q.toString());
        this.logger.error("SOLRQUERY generated for matching patient based on genes [{}]: {}",
                referencePatient.getId(), query.toString());
        return query;
    }

    private StringBuilder generateBaseQuery(Patient referencePatient, boolean prototypes)
    {
        StringBuilder q = new StringBuilder();

        // Ignore the reference patient itself (unless reference patient is a temporary in-memory only
        // patient, e.g. a RemoteMatchingPatient created from remote patient data obtained via remote-matching API)
        if (referencePatient.getDocumentReference() != null) {
            q.append(" -document:" + ClientUtils.escapeQueryChars(referencePatient.getDocumentReference().toString()));
        }

        // include or ignore OMIM prototypes based on prorotype parameter
        q.append(prototypes ? " +" : " -").append("document:xwiki\\:data.MIM*");

        return q;
    }

    /**
     * Performs a search in the Solr index, returning the matched documents.
     *
     * @param query the query prepared with {@link #generateQuery(Patient, boolean)}
     * @return the documents matched by the query, if any
     */
    private SolrDocumentList search(SolrQuery query)
    {
        try {
            return this.server.query(query).getResults();
        } catch (IOException | SolrServerException ex) {
            this.logger.error("Failed to query the patients index: [{}]", ex.getMessage());
            return null;
        }
    }

    private Collection<String> getPresentPhenotypeTerms(Patient patient)
    {
        Collection<String> termIds = new HashSet<>();
        for (Feature phenotype : patient.getFeatures()) {
            if (phenotype != null && phenotype.isPresent()) {
                String termId = phenotype.getId();
                if (StringUtils.isNotBlank(termId)) {
                    VocabularyTerm term = this.vocabularies.resolveTerm(termId);
                    if (term != null) {
                        for (VocabularyTerm ancestor : term.getAncestorsAndSelf()) {
                            termIds.add(ancestor.getId());
                        }
                    }
                }
            }
        }
        return termIds;
    }

    private Collection<String> getGenesToSearch(Patient patient)
    {
        List<String> genesToSearch = new ArrayList<>();

        PatientData<Gene> allGenes = patient.getData("genes");
        if (allGenes != null && allGenes.size() > 0 && allGenes.isIndexed()) {
            for (Gene gene : allGenes) {
                String geneId = gene.getId().trim();
                if (StringUtils.isBlank(geneId)) {
                    continue;
                }
                String status = gene.getStatus();
                // Treat empty status as candidate
                if (StringUtils.isBlank(status) || "solved".equals(status) || "candidate".equals(status)) {
                    genesToSearch.add(geneId);
                }
            }
        }

        return genesToSearch;
    }

    private String getQueryFromTerms(Collection<String> terms)
    {
        if (terms == null || terms.isEmpty()) {
            return "";
        }
        Collection<String> escaped = new HashSet<>(terms.size());
        for (String term : terms) {
            escaped.add(ClientUtils.escapeQueryChars(term));
        }
        return "(" + StringUtils.join(escaped, " ") + ")";
    }

    private Long getSolrIndexSize()
    {
        try {
            SolrQuery q = new SolrQuery("*:*");
            // don't actually request any data
            q.setRows(0);
            long indexSize = this.server.query(q).getResults().getNumFound();
            return indexSize;
        } catch (IOException | SolrServerException ex) {
            this.logger.error("Failed to get patients index size: [{}]", ex.getMessage());
            return null;
        }
    }
}
