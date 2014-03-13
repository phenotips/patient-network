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
package org.phenotips.similarity.internal;

import org.phenotips.data.Feature;
import org.phenotips.data.Patient;
import org.phenotips.data.PatientRepository;
import org.phenotips.data.permissions.AccessLevel;
import org.phenotips.data.similarity.PatientSimilarityView;
import org.phenotips.data.similarity.PatientSimilarityViewFactory;
import org.phenotips.similarity.SimilarPatientsFinder;

import org.xwiki.component.annotation.Component;
import org.xwiki.component.phase.Initializable;
import org.xwiki.component.phase.InitializationException;
import org.xwiki.configuration.ConfigurationSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.commons.lang3.StringUtils;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrServer;
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
 * @since 1.0M8
 */
@Component
@Singleton
public class SolrSimilarPatientsFinder implements SimilarPatientsFinder, Initializable
{
    /** Character used in URLs to delimit path segments. */
    private static final String URL_PATH_SEPARATOR = "/";

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
    @Named("mi")
    private PatientSimilarityViewFactory factory;

    @Inject
    @Named("xwikiproperties")
    private ConfigurationSource configuration;

    /** The Solr server instance used. */
    private SolrServer server;

    @Override
    public void initialize() throws InitializationException
    {
        try {
            this.server = new HttpSolrServer(this.getSolrLocation() + "patients/");
        } catch (RuntimeException ex) {
            throw new InitializationException("Invalid URL specified for the Solr server: {}");
        }
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
            if (this.accessLevelThreshold.compareTo(result.getAccess()) <= 0 && result.getScore() > 0) {
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
            q.append(phenotype.getType() + ":" + ClientUtils.escapeQueryChars(phenotype.getId()) + " ");
        }
        // Ignore the reference patient itself
        q.append("-document:" + ClientUtils.escapeQueryChars(referencePatient.getDocument().toString()));
        q.append(prototypes ? " +" : " -").append("document:xwiki\\:data.MIM*");
        query.add(CommonParams.Q, q.toString());
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
        } catch (SolrServerException ex) {
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

    /**
     * Get the URL where the Solr server can be reached, without any core name.
     * 
     * @return an URL as a String
     */
    protected String getSolrLocation()
    {
        String wikiSolrUrl = this.configuration.getProperty("solr.remote.url", String.class);
        if (StringUtils.isBlank(wikiSolrUrl)) {
            return "http://localhost:8080/solr/";
        }
        return StringUtils.substringBeforeLast(StringUtils.removeEnd(wikiSolrUrl, URL_PATH_SEPARATOR),
            URL_PATH_SEPARATOR) + URL_PATH_SEPARATOR;
    }
}
