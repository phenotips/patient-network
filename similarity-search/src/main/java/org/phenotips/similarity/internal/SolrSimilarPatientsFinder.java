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

import org.phenotips.data.ConsentManager;
import org.phenotips.data.Feature;
import org.phenotips.data.Patient;
import org.phenotips.data.PatientData;
import org.phenotips.data.PatientRepository;
import org.phenotips.data.permissions.AccessLevel;
import org.phenotips.data.similarity.PatientSimilarityView;
import org.phenotips.data.similarity.PatientSimilarityViewFactory;
import org.phenotips.similarity.SimilarPatientsFinder;
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
import java.util.Map;
import java.util.PriorityQueue;

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
public class SolrSimilarPatientsFinder implements SimilarPatientsFinder, Initializable
{
    /** The number of records to fully score. */
    private static final int SEED_QUERY_SIZE = 50;

    /** Logging helper object. */
    @Inject
    private Logger logger;

    /** Provides access to patient data. */
    @Inject
    private PatientRepository patients;

    /** The minimal access level needed for including a patient in the result. */
    @Inject
    @Named("match")
    private AccessLevel accessLevelThreshold;

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

    @Override
    public long countSimilarPatients(Patient referencePatient)
    {
        SolrQuery query = generateQuery(referencePatient, false);
        return count(query);
    }

    private List<PatientSimilarityView> find(Patient referencePatient, String requiredConsentId, boolean prototypes)
    {
        this.logger.debug("Searching for patients similar to [{}] using access level {}",
            referencePatient.getDocument(), this.accessLevelThreshold.getName());
        SolrQuery query = generateQuery(referencePatient, prototypes);
        if (query == null) {
            return Collections.emptyList();
        }
        SolrDocumentList docs = search(query);
        List<PatientSimilarityView> results = new ArrayList<PatientSimilarityView>(docs.size());
        for (SolrDocument doc : docs) {
            String name = (String) doc.getFieldValue("document");
            Patient matchPatient = this.patients.get(name);
            if (matchPatient == null) {
                // Leftover patient in the index, should be removed
                continue;
            }
            // check consents, if required
            if (requiredConsentId != null && !consentManager.hasConsent(matchPatient, requiredConsentId)) {
                continue;
            }
            PatientSimilarityView result = this.factory.makeSimilarPatient(matchPatient, referencePatient);
            this.logger.debug("Found match: [{}] with score: {}, accessLevel: {}, accessCompare: {}",
                name, result.getScore(), result.getAccess().getName(),
                this.accessLevelThreshold.compareTo(result.getAccess()));
            if (this.accessLevelThreshold.compareTo(result.getAccess()) <= 0 && result.getScore() > 0) {
                results.add(result);
            }
        }

        return getTopResults(results);
    }

    private List<PatientSimilarityView> getTopResults(List<PatientSimilarityView> allResults)
    {
        if (allResults.isEmpty()) {
            return Collections.emptyList();
        }

        // Select top k via PriorityQueue
        PriorityQueue<PatientSimilarityView> pq =
            new PriorityQueue<PatientSimilarityView>(allResults.size(), new Comparator<PatientSimilarityView>()
            {
                @Override
                public int compare(PatientSimilarityView o1, PatientSimilarityView o2)
                {
                    return (int) Math.signum(o2.getScore() - o1.getScore());
                }
            });
        pq.addAll(allResults);

        List<PatientSimilarityView> topResults = new ArrayList<PatientSimilarityView>();
        int docSize = pq.size();
        for (int i = 0; i < docSize; i++) {
            PatientSimilarityView item = pq.poll();
            if (item != null) {
                topResults.add(item);
            }
        }
        return topResults;
    }

    /**
     * Generates a Solr query that tries to match patients similar to the reference.
     *
     * @param referencePatient the reference patient
     * @return a query populated with terms from the patient phenotype
     */
    private SolrQuery generateQuery(Patient referencePatient, boolean prototypes)
    {
        SolrQuery query = new SolrQuery();
        StringBuilder q = new StringBuilder();
        // Whitespace-delimiter terms querying the extended phenotype field
        Collection<String> termIds = getPresentPhenotypeTerms(referencePatient);
        if (!termIds.isEmpty()) {
            q.append(" extended_phenotype:" + getQueryFromTerms(termIds));
        }

        PatientData<Map<String, String>> allGenes = referencePatient.getData("genes");
        appendGenesToQuery(allGenes, q);

        // Ignore the reference patient itself (unless reference patient is a temporary in-memory only
        // patient, e.g. a RemoteMatchingPatient created from remote patient data obtained via remote-matching API)
        if (referencePatient.getDocument() != null) {
            q.append(" -document:" + ClientUtils.escapeQueryChars(referencePatient.getDocument().toString()));
        }
        q.append(prototypes ? " +" : " -").append("document:xwiki\\:data.MIM*");
        query.add(CommonParams.Q, q.toString());
        this.logger.debug("SOLRQUERY generated for matching patient [{}]: {}", referencePatient.getDocument(),
            query.toString());
        return query;
    }

    /**
     * Performs a search in the Solr index, returning the matched documents.
     *
     * @param query the query prepared with {@link #generateQuery(Patient)}
     * @return the documents matched by the query, if any
     */
    private SolrDocumentList search(SolrQuery query)
    {
        query.setRows(SEED_QUERY_SIZE);
        try {
            return this.server.query(query).getResults();
        } catch (IOException | SolrServerException ex) {
            this.logger.warn("Failed to query the patients index: {}", ex.getMessage());
            return null;
        }
    }

    /**
     * Performs a search in the Solr index, returning only the total number of matches found.
     *
     * @param query the query prepared with {@link #generateQuery(Patient)}
     * @return the total number of document matched by the query, {@code 0} if none match
     */
    private long count(SolrQuery query)
    {
        query.setRows(0);
        SolrDocumentList response = search(query);
        if (response != null) {
            return response.getNumFound();
        }
        return 0;
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

    private void appendGenesToQuery(PatientData<Map<String, String>> allGenes, StringBuilder q)
    {
        Collection<String> genesToSearch = new ArrayList<String>();
        if (allGenes != null && allGenes.size() > 0 && allGenes.isIndexed()) {
            for (Map<String, String> gene : allGenes) {
                String geneName = gene.get("gene");
                if (StringUtils.isBlank(geneName)) {
                    continue;
                }

                geneName = geneName.trim();
                String status = gene.get("status");
                // Treat empty status as candidate
                if (StringUtils.isBlank(status) || "solved".equals(status) || "candidate".equals(status)) {
                    genesToSearch.add(geneName);
                }
            }
        }
        if (!genesToSearch.isEmpty()) {
            String geneQuery = getQueryFromTerms(genesToSearch);
            q.append(" solved_genes:" + geneQuery);
            q.append(" candidate_genes:" + geneQuery);
        }
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
}
