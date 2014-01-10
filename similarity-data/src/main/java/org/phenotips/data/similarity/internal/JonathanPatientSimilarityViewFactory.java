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
package org.phenotips.data.similarity.internal;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import org.apache.solr.common.params.CommonParams;
import org.phenotips.data.Patient;
import org.phenotips.data.permissions.AccessLevel;
import org.phenotips.data.permissions.PermissionsManager;
import org.phenotips.data.similarity.AccessType;
import org.phenotips.data.similarity.PatientSimilarityView;
import org.phenotips.data.similarity.PatientSimilarityViewFactory;
import org.phenotips.ontology.OntologyManager;
import org.phenotips.ontology.OntologyService;
import org.phenotips.ontology.OntologyTerm;
import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.phase.Initializable;
import org.xwiki.component.phase.InitializationException;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

/**
 * Implementation of {@link PatientSimilarityViewFactory} which uses Jonathan's code
 * 
 * @version $Id$
 * @since 
 */
@Component
@Named("jonathan")
@Singleton
public class JonathanPatientSimilarityViewFactory implements PatientSimilarityViewFactory, Initializable
{
	// Solr search guildelines: http://www.solrtutorial.com/solr-query-syntax.html
	
	/** Logging helper object. */
	@Inject
	private Logger logger;
	
    /** Provides access to the term ontology. */
    @Inject
    private OntologyManager ontologyManager;

    private Map<OntologyTerm, Set<OntologyTerm>> termChildren;
    
    /** Pre-computed bound on -logP(t|parents(t)), for each node t (i.e. t.cond_inf)  */
    private static Map<OntologyTerm, Double> probCondParents;
    
    /** Pre-computed term information content (-logp), for each node t (i.e. t.inf)  */
    private static Map<OntologyTerm, Double> termIC;
    
    @Override
    public PatientSimilarityView makeSimilarPatient(Patient match, Patient reference) throws IllegalArgumentException
    {
        if (match == null || reference == null) {
            throw new IllegalArgumentException("Similar patients require both a match and a reference");
        }
        return new JonathanPatientSimilarityView(match, reference, ontologyManager);
    }

    private Map<String, String> getChildrenSearchQuery(OntologyTerm parent) 
    {
    	Map<String, String> query = new HashMap<String, String>();
        query.put("is_a", parent.getId());
        return query;
    }
    
    private double limitProb(double prob) 
    {
    	return Math.min(Math.max(prob, Double.MIN_VALUE), 1-Double.MIN_VALUE);
    }
    
    private double setTermICHelper(OntologyTerm term) 
    {
        /* def setInformation(node):
        *   probMass = 0
        *   for child in children(node):
        *     probMass += setInformation(child)
        *     
        *   probMass = min(max(probMass, eps), 1-eps)
        *   termIC[node] = -log(probMass)
        *   return probMass
        */
    	double probMass = 0;
    	if (termIC.containsKey(term)) {
    		probMass = Math.pow(2, -termIC.get(term));
    	} else {
	    	Collection<OntologyTerm> children = termChildren.get(term);
	    	if (children != null) {
	    		for (OntologyTerm child : children) {
	    			probMass += setTermICHelper(child);
	    		}
	    	}
	    	probMass = limitProb(probMass);
	    	// Compute -log_2(prob)
	    	termIC.put(term, -Math.log(probMass) / Math.log(2));
    	}
    	return probMass;
    }
    
	@Override
	public void initialize() throws InitializationException 
	{
    	logger.error("JonathanPatientSimilarityViewFactory instance initializing...");

    	// Reusable query for all items in ontology
        Map<String, String> queryAll = new HashMap<String, String>();
        queryAll.put("id", "*");
        queryAll.put(CommonParams.ROWS, "10000000");
        Collection<OntologyTerm> termResults;
        
    	// Load the OMIM/HPO mappings to pre-compute term statistics
    	OntologyService diseaseTraits = ontologyManager.getOntology("MIM");
    	OntologyService phenotypes = ontologyManager.getOntology("HPO");
    	OntologyTerm hpRoot = phenotypes.getTerm("HP:0000118");
    	
    	// Compute prior frequencies of phenotypes (based on disease frequencies and phenotype prevalences)
        Map<OntologyTerm, Double> termFreq = new HashMap<OntologyTerm, Double>();  // t.freq
        termIC = new HashMap<OntologyTerm, Double>();
        double freqDenom = 0;
        termChildren = new HashMap<OntologyTerm, Set<OntologyTerm>>();

        // Implementation takes only a few seconds
        logger.error("Iterating over HPO ontology...");
        termResults = phenotypes.search(queryAll);
        for (OntologyTerm term : termResults) {
        	termFreq.put(term, 0.0);
        	for (OntologyTerm parent : term.getParents()) {
        		Set<OntologyTerm> parentChildren = termChildren.get(parent.getId());
        		if (parentChildren == null) {
        			parentChildren = new HashSet<OntologyTerm>();
        			termChildren.put(parent, parentChildren);
        		}
        		parentChildren.add(term);
        	}
        }
        logger.error("Processed " + termFreq.size() + " HPO terms");
        
        // Starting with root of phenotype abnormality, descend tree to build up
        // termChildren mapping and initialize term frequencies
    	// Implementation takes 2m30
    	/*
    	logger.error("Traversing HPO ontology starting at the root...");
    	OntologyTerm root = phenotypes.getTerm("HP:0000118");
        Queue<OntologyTerm> termQ = new LinkedList<OntologyTerm>();
        termQ.add(root);
        while (!termQ.isEmpty()) {
        	OntologyTerm term = termQ.remove();
        	assert !termFreq.containsKey(term);
        	termFreq.put(term, 0.0);
        	Set<OntologyTerm> children = new HashSet<OntologyTerm>();
        	for (OntologyTerm child : phenotypes.search(getChildrenSearchQuery(term))) {
        		termQ.add(child);
        		children.add(child);
        	}
        	if (!children.isEmpty()) {
        		termChildren.put(term, children);
        	}
        }
        */
        
    	logger.error("Iterating over MIM ontology...");
        long numDiseases = diseaseTraits.size();
        
        termResults = diseaseTraits.search(queryAll);
        for (OntologyTerm disease : termResults) {
        	logger.error("Found disease:" + disease.getId());
        	Object symptoms = disease.get("actual_symptom");
        	if (!(symptoms instanceof Collection<?>)) {
        		throw new RuntimeException("Solr returned non-collection symptoms");
        	}
    		for (String symptom : ((Collection<String>) symptoms)) {
            	logger.error("  Found symptom:" + symptom);
            	double freq = 0.25;
            	freqDenom += freq;
            	OntologyTerm symptomTerm = phenotypes.getTerm(symptom);
            	if (termFreq.containsKey(symptomTerm)) {
            		freq += termFreq.get(symptomTerm);
            	} else {
            		logger.error("  Missing existing termFreq for " + symptomTerm.getId());
            	}
            	termFreq.put(symptomTerm, freq);
    		}
        	break;
        }
        
        for (Map.Entry<OntologyTerm, Double> entry : termFreq.entrySet()) {
        	entry.setValue(limitProb(entry.getValue() / freqDenom));
        }
        
        // Fill the termIC map by traversing the HP ontology recursively from the root
        setTermICHelper(hpRoot);
        
        for (OntologyTerm term : termFreq.keySet()) {
        	double siblingProbMass = 0;
        	for (OntologyTerm parent : term.getParents()) {
        		for (OntologyTerm possibleSibling : termChildren.get(parent)) {
        			// Check if sibling has a superset of the term's parents
        			if (possibleSibling.getParents().containsAll(term.getParents())) {
        				siblingProbMass += Math.pow(2, -termIC.get(possibleSibling));
        			}
        		}
        	}
        	probCondParents.put(term, termIC.get(term) + (Math.log(limitProb(siblingProbMass)) / Math.log(2)));
        }
	}
}
