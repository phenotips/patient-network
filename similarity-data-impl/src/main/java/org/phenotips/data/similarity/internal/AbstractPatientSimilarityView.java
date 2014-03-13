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

import org.phenotips.components.ComponentManagerRegistry;
import org.phenotips.data.Disorder;
import org.phenotips.data.Feature;
import org.phenotips.data.Patient;
import org.phenotips.data.PatientData;
import org.phenotips.data.permissions.AccessLevel;
import org.phenotips.data.similarity.AccessType;
import org.phenotips.data.similarity.DisorderSimilarityView;
import org.phenotips.data.similarity.ExternalToolJobManager;
import org.phenotips.data.similarity.FeatureSimilarityScorer;
import org.phenotips.data.similarity.FeatureSimilarityView;
import org.phenotips.data.similarity.Genotype;
import org.phenotips.data.similarity.GenotypeSimilarityView;
import org.phenotips.data.similarity.PatientSimilarityView;
import org.phenotips.messaging.ConnectionManager;

import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.model.reference.DocumentReference;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import net.sf.json.JSONArray;

/**
 * Base class for implementing {@link PatientSimilarityView}.
 * 
 * @version $Id$
 * @since 1.0M10
 */
public abstract class AbstractPatientSimilarityView implements PatientSimilarityView
{
    /** The matched patient to represent. */
    protected final Patient match;

    /** The reference patient against which to compare. */
    protected final Patient reference;

    /** The access level the user has to this patient. */
    protected final AccessType access;

    /** The exomizer manager object to handle genetic comparisons. */
    protected final ExternalToolJobManager<Genotype> exomizerManager;

    protected final String contactToken;

    /** Links feature values from this patient to the reference. */
    protected Set<FeatureSimilarityView> matchedFeatures;

    /** Links gene variants from this patient to the reference. */
    protected GenotypeSimilarityView matchedGenes;

    /** Links disorder values from this patient to the reference. */
    protected Set<DisorderSimilarityView> matchedDisorders;

    /**
     * Simple constructor passing both {@link #match the patient} and the {@link #reference reference patient}.
     * 
     * @param match the matched patient to represent, must not be {@code null}
     * @param reference the reference patient against which to compare, must not be {@code null}
     * @param access the access level the current user has on the matched patient
     * @throws IllegalArgumentException if one of the patients is {@code null}
     */
    public AbstractPatientSimilarityView(Patient match, Patient reference, AccessType access)
        throws IllegalArgumentException
    {
        if (match == null || reference == null) {
            throw new IllegalArgumentException("Similar patients require both a match and a reference");
        }
        this.match = match;
        this.reference = reference;
        this.access = access;
        String token = "";
        ExternalToolJobManager<Genotype> em = null;
        try {
            ConnectionManager cm =
                ComponentManagerRegistry.getContextComponentManager().getInstance(ConnectionManager.class);
            token = String.valueOf(cm.getConnection(this).getId());
            em =
                ComponentManagerRegistry.getContextComponentManager().getInstance(ExternalToolJobManager.class,
                    "exomizer");
        } catch (ComponentLookupException e) {
            // This should not happen
        }

        this.exomizerManager = em;
        this.contactToken = token;

        matchFeatures();
        matchDisorders();
        matchGenes();
    }

    @Override
    public String getId()
    {
        return this.match.getId();
    }

    @Override
    public String getExternalId()
    {
        return this.match.getExternalId();
    }

    @Override
    public DocumentReference getDocument()
    {
        return this.match.getDocument();
    }

    @Override
    public DocumentReference getReporter()
    {
        return this.match.getReporter();
    }

    @Override
    public <T> PatientData<T> getData(String name)
    {
        return this.match.getData(name);
    }

    @Override
    public String getContactToken()
    {
        return this.contactToken;
    }

    @Override
    public AccessLevel getAccess()
    {
        return this.access.getAccessLevel();
    }

    @Override
    public Patient getReference()
    {
        return this.reference;
    }

    /**
     * Get JSON for all features in the patient according to the access level. See {@link #getFeatures()} for the
     * features displayed.
     * 
     * @return the JSON for visible features
     */
    protected JSONArray getFeaturesJSON()
    {
        Set<? extends Feature> features = getFeatures();
        JSONArray featuresJSON = new JSONArray();
        if (!features.isEmpty()) {
            for (Feature feature : features) {
                featuresJSON.add(feature.toJSON());
            }
        }
        return featuresJSON;
    }

    /**
     * Get JSON for all disorders in the patient according to the access level. See {@link #getDisorders()} for the
     * disorders displayed.
     * 
     * @return the JSON for visible disorders
     */
    protected JSONArray getDisordersJSON()
    {
        JSONArray disordersJSON = new JSONArray();
        Set<? extends Disorder> disorders = getDisorders();
        if (!disorders.isEmpty()) {
            for (Disorder disorder : disorders) {
                disordersJSON.add(disorder.toJSON());
            }
        }
        return disordersJSON;
    }

    /**
     * Get JSON for genotypic similarity between the patients according to the access level. See
     * {@link #getGenotypeSimilarity()} for details.
     * 
     * @return the JSON for genotypic similarity
     */
    protected JSONArray getGenotypeJSON()
    {
        return getGenotype().toJSON();
    }

    /**
     * Searches for a similar feature in the reference patient, matching one of the matched patient's features, or
     * vice-versa.
     * 
     * @param toMatch the feature to match
     * @param lookIn the list of features to look in, either the reference patient or the matched patient features
     * @return one of the features from the list, if it matches the target feature, or {@code null} otherwise
     */
    protected Feature findMatchingFeature(Feature toMatch, Set<? extends Feature> lookIn)
    {
        FeatureSimilarityScorer scorer = RestrictedFeatureSimilarityView.getScorer();
        if (scorer != null) {
            double bestScore = 0;
            Feature bestMatch = null;
            for (Feature candidate : lookIn) {
                double score = scorer.getScore(candidate, toMatch);
                if (score > bestScore) {
                    bestScore = score;
                    bestMatch = candidate;
                }
            }
            return bestMatch;
        } else {
            for (Feature candidate : lookIn) {
                if (StringUtils.equals(candidate.getId(), toMatch.getId())) {
                    return candidate;
                }
            }
        }
        return null;
    }

    @Override
    public Set<? extends Feature> getFeatures()
    {
        Set<Feature> result = new HashSet<Feature>();
        for (FeatureSimilarityView feature : this.matchedFeatures) {
            if (feature.isMatchingPair() || feature.getId() != null) {
                result.add(feature);
            }
        }

        return result;
    }

    /**
     * Return the Genotype object containing the genes and variants between the two patients.
     * 
     * @return A Genotype object, containing the relevant genes, variants, and scores for the patient matching.
     */
    public Genotype getGenotype()
    {
        return this.matchedGenes;
    }

    @Override
    public Set<? extends Disorder> getDisorders()
    {
        Set<Disorder> result = new HashSet<Disorder>();
        for (DisorderSimilarityView disorder : this.matchedDisorders) {
            if (disorder.getId() != null) {
                result.add(disorder);
            }
        }

        return result;
    }

    /**
     * Searches for a similar disorder in the reference patient, matching one of the matched patient's disorders, or
     * vice-versa.
     * 
     * @param toMatch the disorder to match
     * @param lookIn the list of disorders to look in, either the reference patient or the matched patient diseases
     * @return one of the disorders from the list, if it matches the target disorder, or {@code null} otherwise
     */
    protected Disorder findMatchingDisorder(Disorder toMatch, Set<? extends Disorder> lookIn)
    {
        for (Disorder candidate : lookIn) {
            if (StringUtils.equals(candidate.getId(), toMatch.getId())) {
                return candidate;
            }
        }
        return null;
    }

    /** See {@link #RestrictedFeatureSimilarityView(Feature, Feature, AccessType)}. */
    protected FeatureSimilarityView createFeatureSimilarityView(Feature match, Feature reference, AccessType access)
    {
        return new DefaultFeatureSimilarityView(match, reference);
    }

    /** See {@link #RestrictedDisorderSimilarityView(Patient, Patient, AccessType)}. */
    protected DisorderSimilarityView createDisorderSimilarityView(Disorder match, Disorder reference, AccessType access)
    {
        return new DefaultDisorderSimilarityView(match, reference);
    }

    /**
     * Create pairs of matching features, one from the current patient and one from the reference patient. Unmatched
     * values from either side are paired with a {@code null} value.
     */
    protected void matchFeatures()
    {
        Set<FeatureSimilarityView> result = new HashSet<FeatureSimilarityView>();
        for (Feature feature : this.match.getFeatures()) {
            Feature matching = findMatchingFeature(feature, this.reference.getFeatures());
            result.add(createFeatureSimilarityView(feature, matching, this.access));
        }
        for (Feature feature : this.reference.getFeatures()) {
            Feature matching = findMatchingFeature(feature, this.match.getFeatures());
            if (matching == null) {
                result.add(createFeatureSimilarityView(null, feature, this.access));
            }
        }
        this.matchedFeatures = Collections.unmodifiableSet(result);
    }

    /**
     * Create pairs of matching disorders, one from the current patient and one from the reference patient. Unmatched
     * values from either side are paired with a {@code null} value.
     */
    protected void matchDisorders()
    {
        Set<DisorderSimilarityView> result = new HashSet<DisorderSimilarityView>();
        for (Disorder disorder : this.match.getDisorders()) {
            result.add(createDisorderSimilarityView(disorder,
                findMatchingDisorder(disorder, this.reference.getDisorders()), this.access));
        }
        for (Disorder disorder : this.reference.getDisorders()) {
            if (this.match == null || findMatchingDisorder(disorder, this.match.getDisorders()) == null) {
                result.add(createDisorderSimilarityView(null, disorder, this.access));
            }
        }
        this.matchedDisorders = Collections.unmodifiableSet(result);
    }

    /**
     * Create a GenotypeSimilarityView for the pair of patients, if genotype information exists for both.
     */
    private void matchGenes()
    {
        this.matchedGenes = new RestrictedGenotypeSimilarityView(this.match, this.reference, this.access);
    }

    @Override
    public double getScore()
    {
        double featuresScore = getFeaturesScore();
        return adjustScoreWithDisordersScore(featuresScore);
    }

    /**
     * Compute the patient's score as given by the phenotypic similarity with the reference patient.
     * 
     * @return a similarity score, between {@code -1} for opposite patient descriptions and {@code 1} for an exact
     *         match, with {@code 0} for patients with no similarities
     * @see #getScore()
     */
    protected double getFeaturesScore()
    {
        if (this.matchedFeatures.isEmpty()) {
            return 0;
        }
        double featureScore;
        // Lower bias means that positive values are far more important ("heavy") than negative ones
        // Higher bias means that the score is closer to an arithmetic mean
        double bias = 3.0;
        double squareSum = 0;
        double sum = 0;
        int matchingFeaturePairs = 0;
        int unmatchedFeaturePairs = 0;

        for (FeatureSimilarityView feature : this.matchedFeatures) {
            double elementScore = feature.getScore();
            if (Double.isNaN(elementScore)) {
                ++unmatchedFeaturePairs;
                continue;
            }
            squareSum += (bias + elementScore) * (bias + elementScore);
            sum += bias + elementScore;
            ++matchingFeaturePairs;
        }
        if (matchingFeaturePairs == 0) {
            return 0;
        }
        featureScore = squareSum / sum - bias;

        if (unmatchedFeaturePairs > 0 && featureScore > 0) {
            // When there are many unmatched features, lower the score towards 0 (irrelevant patient pair score)
            featureScore *=
                Math.pow(0.9, Math.max(0, unmatchedFeaturePairs - Math.ceil(Math.log(matchingFeaturePairs))));
        }
        return featureScore;
    }

    /**
     * Adjust the similarity score by taking into account common disorders. Matching disorders will boost the base score
     * given by the phenotypic similarity, while unmatched disorders don't affect the score at all. If the base score is
     * negative, no boost is awarded.
     * 
     * @param baseScore the score given by features alone, a number between {@code -1} and {@code 1}
     * @return the adjusted similarity score, boosted closer to {@code 1} if there are common disorders between this
     *         patient and the reference patient, or the unmodified base score otherwise; the score is never lowered,
     *         and never goes above {@code 1}
     * @see #getScore()
     */
    protected double adjustScoreWithDisordersScore(double baseScore)
    {
        if (this.matchedDisorders.isEmpty() || baseScore <= 0) {
            return baseScore;
        }
        double score = baseScore;
        double bias = 3;
        for (DisorderSimilarityView disorder : this.matchedDisorders) {
            if (disorder.isMatchingPair()) {
                // For each disorder match, reduce the distance between the current score to 1 by 1/3
                score = score + (1 - score) / bias;
            }
        }
        return score;
    }

}
