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
package org.phenotips.data.similarity.internal;

import org.phenotips.data.Patient;
import org.phenotips.data.similarity.AccessType;
import org.phenotips.data.similarity.PatientGenotype;
import org.phenotips.data.similarity.PatientGenotypeSimilarityView;
import org.phenotips.data.similarity.Variant;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Base class for implementing {@link PatientGenotypeSimilarityView}.
 *
 * @version $Id$
 * @since 1.0M6
 */
public abstract class AbstractPatientGenotypeSimilarityView extends AbstractExome
    implements PatientGenotypeSimilarityView
{
    /** The matched patient to represent. */
    protected final Patient match;

    /** The reference patient against which to compare. */
    protected final Patient reference;

    /** The access type the user has to the match patient. */
    protected final AccessType access;

    /** The matched genotype to represent. */
    protected PatientGenotype matchGenotype;

    /** The reference genotype against which to compare. */
    protected PatientGenotype refGenotype;

    /**
     * Simple constructor passing the {@link #match matched patient}, the {@link #reference reference patient}, and the
     * {@link #access patient access type}.
     *
     * @param match the matched patient, which is represented by this match
     * @param reference the reference patient, which the match is against
     * @param access the access type the user has to the match patient
     * @throws IllegalArgumentException if one of the patients is {@code null}
     */
    public AbstractPatientGenotypeSimilarityView(Patient match, Patient reference, AccessType access)
        throws IllegalArgumentException
    {
        if (match == null || reference == null) {
            throw new IllegalArgumentException("Both a match and a reference patient required");
        }
        this.match = match;
        this.reference = reference;
        this.access = access;
    }

    @Override
    public boolean hasGenotypeData()
    {
        return this.matchGenotype != null && this.matchGenotype.hasGenotypeData();
    }

    @Override
    public Set<String> getCandidateGenes()
    {
        if (this.matchGenotype == null) {
            return Collections.emptySet();
        } else {
            return Collections.unmodifiableSet(this.matchGenotype.getCandidateGenes());
        }
    }

    @Override
    public Set<String> getGenes()
    {
        if (this.matchGenotype == null) {
            return Collections.emptySet();
        } else {
            return Collections.unmodifiableSet(this.matchGenotype.getGenes());
        }
    }

    @Override
    public Double getGeneScore(String gene)
    {
        return this.matchGenotype == null ? null : this.matchGenotype.getGeneScore(gene);
    }

    @Override
    public List<Variant> getTopVariants(String gene, int k)
    {
        if (this.matchGenotype == null) {
            return Collections.emptyList();
        } else {
            return this.matchGenotype.getTopVariants(gene, k);
        }
    }
}
