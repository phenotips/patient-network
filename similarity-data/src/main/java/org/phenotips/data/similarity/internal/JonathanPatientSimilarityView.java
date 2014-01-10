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

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import net.sf.json.JSONObject;

import org.phenotips.data.Disorder;
import org.phenotips.data.Feature;
import org.phenotips.data.Patient;
import org.phenotips.data.permissions.AccessLevel;
import org.phenotips.data.similarity.PatientSimilarityView;
import org.phenotips.ontology.OntologyManager;
import org.phenotips.ontology.OntologyService;
import org.phenotips.ontology.OntologyTerm;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.phase.Initializable;
import org.xwiki.component.phase.InitializationException;
import org.xwiki.model.reference.DocumentReference;


/*
 * New components need to be declared in: src/main/resources/META-INF/components.txt
 * PatientSimilarityView injected in similarity-search/.../internal/SolrSimilarPatientsFinder.java, instantiated by RestPatSimViewFactory
 */
public class JonathanPatientSimilarityView implements PatientSimilarityView {
	/*
	 * 
	 * (non-Javadoc)
	 * @see org.phenotips.data.similarity.FeatureSimilarityScorer#getScore(org.phenotips.data.Feature, org.phenotips.data.Feature)
	 * 
	 * -x improves bound on cond prob
	 * code_to_term: mapping from HP id (str) to node.Term()
	 * Term() has .parents (Terms) and .children (Terms)
	 *   .dir_freq
	 *   .raw_freq - total number of annotations for a term, weighted by probs (+0.001 smoothing)
	 *   .freq
	 *   .get_ancestors (set of all ancestor nodes)
	 *   .patients - set of Patient objects
	 *   .inf (set in parse_diseases.compute_probs)
	 *     
	 * create phenotype.Patient() with list of HP terms (translated using code_to_term)
	 *   Patient() inherits from Phenotype():
	 *     contains .traits (list of HP terms), .disease (defaults to None)
	 *     .all_traits is the set of all traits the patient has (and their ancestors)
	 *     Patient object added to a.patients for all ancestors a
	 *     
	 * call sim.sim on (Patient(), Patient(), x)
	 *   get intersection of the sets of all ancestors of all terms for each patient
	 *   t.p1count, .p2count, number of terms in each patient that descend from t, with .mincount as min
	 *   get cost of each patient's traits
	 *     cost of list of Traits is sum_t(t.inf)
	 *   shared cost is sum of scaled (* mincount) t.cond_inf for each shared ancestor Term
	 *   return 2 * (sum of shared costs) / (sum of separate costs)
	 *   
	 * parse_diseases uses phenotype_annotation.tab (OMIM) to set Term() properties
	 *   OMIM line maps disease to phenotype with rough frequency (different for different dbs)
	 *   Disease().freqs(num)/traits(Term)/codes(str) appended
	 *   merge any redundant disease traits: append average (else 0.25 if no data) to Disease.freqs
	 *     add frequency to trait.raw_freq (aggr across diseases)
	 *   t.freq is normalized t.raw_freq, then bounded by [1e-9, 1-1e-9]
	 *   t.prob is sum of t.freq for t and all descendants, then bounded by [1e-9, 1-1e-9]
	 *   t.inf is -log(t.prob)
	 *   t.cond_inf is: t.inf - -logprob(sum of .freq for all common descendants of all strict ancestors of t)
	 *     worse approx: t.inf - max(p.inf for parent p of t)
	 *   
	 * phenotips/resources/solr-configuration/src/main/resources/omim/conf/schema.xml
	 */
	
    /** The matched patient to represent. */
    private final Patient match;

    /** The reference patient against which to compare. */
    private final Patient reference;

    /** Pre-computed bound on -logP(t|parents(t)), for each node t (i.e. t.cond_inf)  */
    private static Map<OntologyTerm, Double> probCondParents;
    
    /** Pre-computed term information content (-logp), for each node t (i.e. t.inf)  */
    private static Map<OntologyTerm, Double> termIC;
    
    private OntologyManager ontologyManager;
	
    public JonathanPatientSimilarityView(Patient match, Patient reference, OntologyManager ontologyManager)
			throws IllegalArgumentException {
    	System.err.println("JonathanPatientSimilarityView instance created...");
		this.ontologyManager = ontologyManager;
		this.match = match;
		this.reference = reference;
		
    	// Load the OMIM/HPO mappings to pre-compute term statistics
    	OntologyService diseaseTraits = ontologyManager.getOntology("MIM");
    	
    	// Compute prior frequencies of phenotypes (based on disease frequencies and phenotype prevalences)
        Map<OntologyTerm, Double> termFreq = new HashMap<OntologyTerm, Double>();  // t.freq
        
        Map<String, String> getAllHPIDs = new HashMap<String, String>();
        getAllHPIDs.put("id", "HP:*");

        for (OntologyTerm term : diseaseTraits.search(getAllHPIDs)) {
        	termFreq.put(term, 0.0);
        	System.err.println(term.getId());
        }
        /*
         * SolrOntologyTerm.getParents()
         * add search API to OntologyService
         * 
         * create mapping from parent nodes to list of children
         * root = "HP:0000118"
         * eps = 1e-9
         * freqDenom = 0
         * for term in HPO:
         *   termFreq[term] = 0
         * 
         * for disease in diseaseTraits:
         *   for term in disease.symptoms:
         *     occurrence = disease.getFrequency(term)
         *     termFreq[term] += occurrence
         * 	   freqDenom += occurrence
         * put("id", "HP:*")
         * put("id", "HP:*")
         * for term in HPO:
         *   termFreq[term] = min(max(termFreq[term] / freqDenom, eps), 1-eps)
         * 
         * def setInformation(node):
         *   probMass = 0
         *   for child in children(node):
         *     probMass += setInformation(child)
         *     
         *   probMass = min(max(probMass, eps), 1-eps)
         *   termIC[node] = -log(probMass)
         *   return probMass
         * 
         * setInformation(root)
         * 
         * for term in HPO:
         *   parents = set(term.getParents())
         *   sibling_prob_mass = 0
         *   for parent in parents:
         *     for poss_sibling in children(parent):
         *       if poss_sibling.getParents().issupersetof(parents):
         *         sibling_prob_mass += exp(-termIC(poss_sibling))
         *         
         *   sibling_prob_mass = min(max(sibling_prob_mass, eps), 1-eps)
         *   probCondParents[term] = termIC(term) + log(sibling_prob_mass)
         * 
         */
        
        //Set<OntologyTerm> terms = diseaseTraits.search(fieldValues);
       
	}
    

    @Override
    public double getScore()
    {
    /* call sim.sim on (Patient(), Patient(), x)
   	 *   get intersection of the sets of all ancestors of all terms for each patient
   	 *   t.p1count, .p2count, number of terms in each patient that descend from t, with .mincount as min
   	 *   get cost of each patient's traits
   	 *     cost of list of Traits is sum_t(t.inf)
   	 *   shared cost is sum of scaled (* mincount) t.cond_inf for each shared ancestor Term
   	 *   return 2 * (sum of shared costs) / (sum of separate costs)
   	 */
        if (match == null || reference == null) {
            return Double.NaN;
        }
        
        for (Feature feature : this.match.getFeatures()) {
            OntologyTerm term = this.ontologyManager.resolveTerm(feature.getId());
            if (term == null) {
                return Double.NaN;
            }
        }
        return 0;
    }

	@Override
	public DocumentReference getDocument() {
		// TODO Auto-generated method stub
		return null;
	}


	@Override
	public DocumentReference getReporter() {
		// TODO Auto-generated method stub
		return null;
	}


	@Override
	public Set<? extends Feature> getFeatures() {
		// TODO Auto-generated method stub
		return null;
	}


	@Override
	public Set<? extends Disorder> getDisorders() {
		// TODO Auto-generated method stub
		return null;
	}


	@Override
	public JSONObject toJSON() {
		// TODO Auto-generated method stub
		return null;
	}


	@Override
	public Patient getReference() {
		// TODO Auto-generated method stub
		return null;
	}


	@Override
	public AccessLevel getAccess() {
		// TODO Auto-generated method stub
		return null;
	}


	@Override
	public String getContactToken() {
		// TODO Auto-generated method stub
		return null;
	}
}
