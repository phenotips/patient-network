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

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

import net.sf.json.JSONObject;

import org.phenotips.components.ComponentManagerRegistry;
import org.phenotips.data.Patient;
import org.phenotips.data.similarity.AccessType;
import org.phenotips.data.similarity.ExternalToolJobManager;
import org.phenotips.data.similarity.Genotype;
import org.phenotips.data.similarity.GenotypeSimilarityView;
import org.xwiki.component.manager.ComponentLookupException;

/**
 * Implementation of {@link GenotypeSimilarityView}  that reveals the full patient information if the user has full access
 * to the patient, and only matching reference information if the patient is matchable.
 * 
 * @version $Id$
 * @since
 */
public class RestrictedGenotypeSimilarityView implements GenotypeSimilarityView
{
    /** Variant score threshold between non-frameshift and splicing. */
    private static final double KO_THRESHOLD = 0.87;
    
    /** The matched genotype to represent. */
    private Genotype matchGenotype;
    
    /** The reference genotype against which to compare. */
    private Genotype refGenotype;

    /** The access type the user has to the match patient. */
    private AccessType access;
    
    /**
     * Simple constructor passing the {@link #match matched patient}, the {@link #reference reference patient}, and the
     * {@link #access patient access type}.
     * 
     * @param match the matched patient to represent
     * @param reference the reference patient against which to compare
     * @param access the access type the user has to the match patient
     */
    public RestrictedGenotypeSimilarityView(Patient match, Patient reference, AccessType access)
    {
        ExternalToolJobManager<Genotype> em = null;
        try {
            em = ComponentManagerRegistry.getContextComponentManager().getInstance(ExternalToolJobManager.class);
        } catch (ComponentLookupException e) {
            assert false : "ExternalToolJobManager could not be retrieved";
        }

        String matchId = match.getDocument().getName();
        String refId = reference.getDocument().getName();
        this.matchGenotype = em.getResult(matchId);
        this.refGenotype = em.getResult(matchId);
        this.access = access;
        
        if (this.matchGenotype == null && this.refGenotype == null) {
            
        }
        
        // Load genotypes for all other patients
        Set<String> otherGenotypedIds = em.getAllCompleted();
        Set<Genotype> otherGenotypes = new TreeSet<Genotype>();
        otherGenotypedIds.remove(matchId);
        otherGenotypedIds.remove(refId);
        for (String patientId : otherGenotypedIds) {
            Genotype gt = em.getResult(patientId);
            if (gt != null) {
                otherGenotypes.add(gt);
            }
        }
        
    }

    @Override
    public Set<String> getGenes()
    {
        if (this.refGenotype == null || this.matchGenotype == null) {
            return Collections.emptySet();
        }
        
        // Get union of genes mutated in both patients
        Set<String> shared = this.refGenotype.getGenes();
        shared.retainAll(this.matchGenotype.getGenes());
        return Collections.unmodifiableSet(shared);
    }

    @Override
    public double getGeneScore(String gene)
    {
        // TODO Auto-generated method stub
        return 0;
    }
    
    @Override
    public JSONObject toJSON()
    {
        if (this.match == null || this.access.isPrivateAccess()) {
            return new JSONObject(true);
        }

        JSONObject result = new JSONObject();
        result.element("score", getScore());
        
        return result;
    }
}
