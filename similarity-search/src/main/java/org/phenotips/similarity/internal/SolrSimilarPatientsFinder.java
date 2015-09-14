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

import org.phenotips.data.Feature;
import org.phenotips.data.Patient;
import org.phenotips.data.PatientRepository;
import org.phenotips.data.permissions.AccessLevel;
import org.phenotips.data.similarity.PatientSimilarityView;
import org.phenotips.data.similarity.PatientSimilarityViewFactory;
import org.phenotips.similarity.SimilarPatientsFinder;
import org.phenotips.vocabulary.SolrCoreContainerHandler;
import org.phenotips.vocabulary.Vocabulary;
import org.phenotips.vocabulary.VocabularyTerm;

import org.xwiki.component.annotation.Component;
import org.xwiki.component.phase.Initializable;
import org.xwiki.component.phase.InitializationException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Named;

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

import groovy.lang.Singleton;

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
    @Named("hpo")
    private Vocabulary ontologyService;

    @Inject
    private SolrCoreContainerHandler cores;

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
        return find(referencePatient, false);
    }

    @Override
    public List<PatientSimilarityView> findSimilarPrototypes(Patient referencePatient)
    {
        return find(referencePatient, true);
    }

    @Override
    public long countSimilarPatients(Patient referencePatient)
    {
        SolrQuery query = generateQuery(referencePatient, false);
        return count(query);
    }

    private List<PatientSimilarityView> find(Patient referencePatient, boolean prototypes)
    {
        //logger.error("Searching for patients using access level: {}", this.accessLevelThreshold.getName());
        SolrQuery query = generateQuery(referencePatient, prototypes);
        SolrDocumentList docs = search(query);
        List<PatientSimilarityView> results = new ArrayList<PatientSimilarityView>(docs.size());
        for (SolrDocument doc : docs) {
            String name = (String) doc.getFieldValue("document");
            Patient matchPatient = this.patients.getPatientById(name);
            if (matchPatient == null) {
                // Leftover patient in the index, should be removed
                continue;
            }
            PatientSimilarityView result = this.factory.makeSimilarPatient(matchPatient, referencePatient);
            //logger.error("Found match: found {}, score: {}, accessLevel: {}, accessCompare: {}",
            //             name, result.getScore(), result.getAccess().getName(),
            //             this.accessLevelThreshold.compareTo(result.getAccess()));
            if (this.accessLevelThreshold.compareTo(result.getAccess()) <= 0 && result.getScore() > 0) {
                //logger.error("added match");
                results.add(result);
            }
        }

        Collections.sort(results, new Comparator<PatientSimilarityView>()
        {
            @Override
            public int compare(PatientSimilarityView o1, PatientSimilarityView o2)
            {
                return (int) Math.signum(o2.getScore() - o1.getScore());
            }
        });
        return results;
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
        // FIXME This is a very basic implementation, to be revisited
        for (Feature phenotype : referencePatient.getFeatures()) {
            if (StringUtils.isNotBlank(phenotype.getId())) {
                //construct a query based on ancestors
                VocabularyTerm term = this.ontologyService.getTerm(phenotype.getId());
                if (term != null) {
                    Set<VocabularyTerm> parents = term.getAncestorsAndSelf();
                    Iterator<VocabularyTerm> iterator = parents.iterator();
                    q.append("(phenotype_ancestors" + ":" + ClientUtils.escapeQueryChars(iterator.next().getId()));
                    while (iterator.hasNext()) {
                        q.append(" OR phenotype_ancestors:"
                            + ClientUtils.escapeQueryChars(iterator.next().getId()));
                    }
                    q.append(")");
                }
            }
        }
        // Ignore the reference patient itself (unless reference patient is a temporary in-memory only
        // patient, e.g. a RemoteMatchingPatient created from remote patient data obtained via remote-matching API)
        if (referencePatient.getDocument() != null) {
            q.append("-document:" + ClientUtils.escapeQueryChars(referencePatient.getDocument().toString()));
        }
        q.append(prototypes ? " +" : " -").append("document:xwiki\\:data.MIM*");
        query.add(CommonParams.Q, q.toString());
        //logger.error("SOLRQUERY generated: {}", query.toString());
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
        try {
            return this.server.query(query).getResults();
        } catch (IOException | SolrServerException ex) {
            this.logger.warn("Failed to query the patients index: {}",
                    ex.getMessage());
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
}
