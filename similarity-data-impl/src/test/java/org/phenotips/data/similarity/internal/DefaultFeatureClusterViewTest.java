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
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.phenotips.data.similarity.internal;

import org.phenotips.data.Feature;
import org.phenotips.data.similarity.AccessType;
import org.phenotips.data.similarity.FeatureClusterView;
import org.phenotips.data.similarity.internal.mocks.MockFeature;
import org.phenotips.vocabulary.VocabularyTerm;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for the default {@link FeatureClusterView} implementation, {@link DefaultFeatureClusterView}.
 *
 * @version $Id$
 */
public class DefaultFeatureClusterViewTest
{
    private static AccessType open;

    @BeforeClass
    public static void setupAccessTypes()
    {
        open = mock(AccessType.class);
        when(open.isOpenAccess()).thenReturn(true);
        when(open.isLimitedAccess()).thenReturn(false);
        when(open.isPrivateAccess()).thenReturn(false);
        when(open.toString()).thenReturn("owner");
    }

    /** Missing reference throws exception. */
    @Test(expected = IllegalArgumentException.class)
    public void testNullReference()
    {
        new DefaultFeatureClusterView(new ArrayList<Feature>(), null, open, null, 0.0);
    }

    /** Missing match throws exception. */
    @Test(expected = IllegalArgumentException.class)
    public void testNullMatch()
    {
        new DefaultFeatureClusterView(null, new ArrayList<Feature>(), open, null, 0.0);
    }

    /** Basic test for ancestor information retrieval. */
    @Test
    public void testGetAncestorDetails()
    {
        Collection<Feature> match = new ArrayList<Feature>();
        Collection<Feature> reference = new ArrayList<Feature>();

        VocabularyTerm ancestor = mock(VocabularyTerm.class);
        when(ancestor.getName()).thenReturn("Cataract");
        when(ancestor.getId()).thenReturn("HP:0000518");
        double score = 0.1234;

        FeatureClusterView o = new DefaultFeatureClusterView(match, reference, open, ancestor, score);
        // Test that ancestor information is retrieved as Feature methods of ClusterView
        Assert.assertEquals(ancestor, o.getRoot());
        Assert.assertEquals(score, o.getScore(), 1E-5);
        Assert.assertEquals(ancestor.getName(), o.getName());
        Assert.assertEquals(ancestor.getId(), o.getId());

        // Test that type is "ancestor" and it is not present (by convention)
        Assert.assertEquals("ancestor", o.getType());
        Assert.assertFalse(o.isPresent());
        Assert.assertTrue(o.getMetadata().isEmpty());
    }

    /** When the ancestor is missing, ancestor is for "unmatched" terms. */
    @Test
    public void testGetAncestorDetailsWithNoAncestor()
    {
        Collection<Feature> match = new ArrayList<Feature>();
        Collection<Feature> reference = new ArrayList<Feature>();
        double score = 0.0;

        FeatureClusterView o = new DefaultFeatureClusterView(match, reference, open, null, score);
        // Test that ancestor information is retrieved as Feature methods of ClusterView
        Assert.assertEquals(null, o.getRoot());
        Assert.assertEquals(score, o.getScore(), 1E-5);
        Assert.assertEquals("Unmatched", o.getName());
        Assert.assertEquals("", o.getId());

        // Test that type is "ancestor" and it is not present (by convention)
        Assert.assertEquals("ancestor", o.getType());
        Assert.assertFalse(o.isPresent());
        Assert.assertTrue(o.getMetadata().isEmpty());
    }

    /** When there are no terms, the getMatch returns an empty collection. */
    @Test
    public void testGetEmptyMatch()
    {
        Collection<Feature> match = new ArrayList<Feature>();
        Collection<Feature> reference = new ArrayList<Feature>();
        double score = 0.0;

        FeatureClusterView o = new DefaultFeatureClusterView(match, reference, open, null, score);
        Assert.assertTrue(o.getMatch().isEmpty());
    }

    /** Basic test of getMatch. */
    @Test
    public void testGetMatch()
    {
        Collection<Feature> match = new ArrayList<Feature>();
        Collection<Feature> reference = new ArrayList<Feature>();
        double score = 0.0;

        FeatureClusterView o = new DefaultFeatureClusterView(match, reference, open, null, score);
        Assert.assertTrue(o.getMatch().isEmpty());

        match.add(new MockFeature("HP:0001382", "Joint hypermobility", "phenotype", true));
        match.add(new MockFeature("HP:0012165", "Oligodactyly", "phenotype", true));
        match.add(new MockFeature("HP:0000518", "Cataract", "phenotype", true));
        match.add(new MockFeature("HP:0001249", "Intellectual disability", "phenotype", false));
        match.add(new MockFeature("HP:0001256", "Mild intellectual disability", "phenotype", true));

        o = new DefaultFeatureClusterView(match, reference, open, null, score);
        Assert.assertEquals(match.size(), o.getMatch().size());
        for (Feature f : o.getMatch()) {
            Assert.assertTrue(match.contains(f));
        }
    }

    /** Ensure that the match results are immutable. */
    @Test(expected = UnsupportedOperationException.class)
    public void testMatchIsImmutable()
    {
        Collection<Feature> match = new ArrayList<Feature>();
        Collection<Feature> reference = new ArrayList<Feature>();
        double score = 0.0;

        match.add(new MockFeature("HP:0001382", "Joint hypermobility", "phenotype", true));

        FeatureClusterView o = new DefaultFeatureClusterView(match, reference, open, null, score);
        Assert.assertEquals(match.size(), o.getMatch().size());
        o.getMatch().clear();
    }

    /** Ensure that the reference results are immutable. */
    @Test(expected = UnsupportedOperationException.class)
    public void testReferenceIsImmutable()
    {
        Collection<Feature> match = new ArrayList<Feature>();
        Collection<Feature> reference = new ArrayList<Feature>();
        double score = 0.0;

        reference.add(new MockFeature("HP:0001382", "Joint hypermobility", "phenotype", true));

        FeatureClusterView o = new DefaultFeatureClusterView(match, reference, open, null, score);
        Assert.assertEquals(reference.size(), o.getReference().size());
        o.getReference().clear();
    }

    /** Basic JSON tests. */
    @Test
    public void testToJSON()
    {
        List<Feature> match = new ArrayList<Feature>();
        List<Feature> reference = new ArrayList<Feature>();

        VocabularyTerm ancestor = mock(VocabularyTerm.class);
        when(ancestor.getName()).thenReturn("Cataract");
        when(ancestor.getId()).thenReturn("HP:0000518");
        double score = 0.1234;

        reference.add(new MockFeature("HP:0012165", "Oligodactyly", "phenotype", true));
        reference.add(new MockFeature("HP:0000518", "Cataract", "phenotype", true));
        reference.add(new MockFeature("HP:0001249", "Intellectual disability", "phenotype", true));

        match.add(new MockFeature("HP:0001382", "Joint hypermobility", "phenotype", true));
        match.add(new MockFeature("HP:0012165", "Oligodactyly", "phenotype", true));
        match.add(new MockFeature("HP:0000518", "Cataract", "phenotype", true));
        match.add(new MockFeature("HP:0001256", "Mild intellectual disability", "phenotype", true));

        FeatureClusterView o = new DefaultFeatureClusterView(match, reference, open, ancestor, score);

        JSONObject result = o.toJSON();
        Assert.assertEquals(4, result.size());

        // Score at root level
        Assert.assertEquals(score, result.getDouble("score"), 1E-10);

        // Category information (for unmatched terms)
        JSONObject category = result.getJSONObject("category");
        Assert.assertEquals(2, category.size());
        Assert.assertEquals(ancestor.getId(), category.getString("id"));
        Assert.assertEquals(ancestor.getName(), category.getString("name"));

        // Reference term ids
        JSONArray ref = result.getJSONArray("reference");
        Assert.assertEquals(reference.size(), ref.size());
        for (int i = 0; i < reference.size(); i++) {
            Assert.assertEquals(reference.get(i).getId(), ref.getString(i));
        }

        // Match term ids
        JSONArray mat = result.getJSONArray("match");
        Assert.assertEquals(match.size(), mat.size());
        for (int i = 0; i < match.size(); i++) {
            Assert.assertEquals(match.get(i).getId(), mat.getString(i));
        }
    }

    /** Basic JSON tests with no ancestor. */
    @Test
    public void testNoAncestorToJSON()
    {
        List<Feature> match = new ArrayList<Feature>();
        List<Feature> reference = new ArrayList<Feature>();
        double score = 0.0;

        reference.add(new MockFeature("HP:0012165", "Oligodactyly", "phenotype", true));
        reference.add(new MockFeature("HP:0000518", "Cataract", "phenotype", true));
        reference.add(new MockFeature("HP:0001249", "Intellectual disability", "phenotype", true));

        match.add(new MockFeature("HP:0001382", "Joint hypermobility", "phenotype", true));
        match.add(new MockFeature("HP:0012165", "Oligodactyly", "phenotype", true));
        match.add(new MockFeature("HP:0000518", "Cataract", "phenotype", true));
        match.add(new MockFeature("HP:0001256", "Mild intellectual disability", "phenotype", true));

        FeatureClusterView o = new DefaultFeatureClusterView(match, reference, open, null, score);

        JSONObject result = o.toJSON();
        Assert.assertEquals(4, result.size());

        // Score at root level
        Assert.assertEquals(score, result.getDouble("score"), 1E-10);

        // Category information (for unmatched terms)
        JSONObject category = result.getJSONObject("category");
        Assert.assertEquals(2, category.size());
        Assert.assertEquals("", category.getString("id"));
        Assert.assertEquals("Unmatched", category.getString("name"));

        // Reference term ids
        JSONArray ref = result.getJSONArray("reference");
        Assert.assertEquals(reference.size(), ref.size());
        for (int i = 0; i < reference.size(); i++) {
            Assert.assertEquals(reference.get(i).getId(), ref.getString(i));
        }

        // Match term ids
        JSONArray mat = result.getJSONArray("match");
        Assert.assertEquals(match.size(), mat.size());
        for (int i = 0; i < match.size(); i++) {
            Assert.assertEquals(match.get(i).getId(), mat.getString(i));
        }
    }

    /** Basic JSON tests with no reference. */
    @Test
    public void testNoReferenceToJSON()
    {
        List<Feature> match = new ArrayList<Feature>();
        List<Feature> reference = new ArrayList<Feature>();
        double score = 0.0;

        match.add(new MockFeature("HP:0001382", "Joint hypermobility", "phenotype", true));
        match.add(new MockFeature("HP:0012165", "Oligodactyly", "phenotype", true));
        match.add(new MockFeature("HP:0000518", "Cataract", "phenotype", true));
        match.add(new MockFeature("HP:0001256", "Mild intellectual disability", "phenotype", true));

        FeatureClusterView o = new DefaultFeatureClusterView(match, reference, open, null, score);

        JSONObject result = o.toJSON();
        Assert.assertEquals(3, result.size());

        // Score at root level
        Assert.assertEquals(score, result.getDouble("score"), 1E-10);

        // Category information (for unmatched terms)
        JSONObject category = result.getJSONObject("category");
        Assert.assertEquals(2, category.size());
        Assert.assertEquals("", category.getString("id"));
        Assert.assertEquals("Unmatched", category.getString("name"));

        // No reference key at all
        Assert.assertFalse(result.containsKey("reference"));

        // Match term ids
        JSONArray mat = result.getJSONArray("match");
        Assert.assertEquals(match.size(), mat.size());
        for (int i = 0; i < match.size(); i++) {
            Assert.assertEquals(match.get(i).getId(), mat.getString(i));
        }
    }

    /** Basic JSON tests with no match. */
    @Test
    public void testNoMatchToJSON()
    {
        List<Feature> match = new ArrayList<Feature>();
        List<Feature> reference = new ArrayList<Feature>();
        double score = 1.0;

        reference.add(new MockFeature("HP:0012165", "Oligodactyly", "phenotype", true));
        reference.add(new MockFeature("HP:0000518", "Cataract", "phenotype", true));
        reference.add(new MockFeature("HP:0001249", "Intellectual disability", "phenotype", true));

        FeatureClusterView o = new DefaultFeatureClusterView(match, reference, open, null, score);

        JSONObject result = o.toJSON();
        Assert.assertEquals(3, result.size());

        // Score at root level
        Assert.assertEquals(score, result.getDouble("score"), 1E-10);

        // Reference term ids
        JSONArray ref = result.getJSONArray("reference");
        Assert.assertEquals(reference.size(), ref.size());
        for (int i = 0; i < reference.size(); i++) {
            Assert.assertEquals(reference.get(i).getId(), ref.getString(i));
        }

        // Match term ids
        Assert.assertFalse(result.containsKey("match"));
    }
}
