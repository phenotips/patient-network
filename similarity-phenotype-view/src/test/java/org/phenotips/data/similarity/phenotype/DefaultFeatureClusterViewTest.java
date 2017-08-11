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
package org.phenotips.data.similarity.phenotype;

import org.phenotips.data.Feature;
import org.phenotips.data.similarity.AccessType;
import org.phenotips.data.similarity.FeatureClusterView;
import org.phenotips.data.similarity.phenotype.mocks.MockFeature;
import org.phenotips.vocabulary.VocabularyTerm;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for the default {@link FeatureClusterView} implementation, {@link DefaultFeatureClusterView}.
 *
 * @version $Id$
 */
public class DefaultFeatureClusterViewTest
{
    private static final String PHENOTYPE = "phenotype";

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
        new DefaultFeatureClusterView(new ArrayList<Feature>(), null, open, null);
    }

    /** Missing match throws exception. */
    @Test(expected = IllegalArgumentException.class)
    public void testNullMatch()
    {
        new DefaultFeatureClusterView(null, new ArrayList<Feature>(), open, null);
    }

    /** Basic test for ancestor information retrieval. */
    @Test
    public void testGetAncestorDetails()
    {
        Collection<Feature> match = new ArrayList<>();
        Collection<Feature> reference = new ArrayList<>();

        VocabularyTerm ancestor = mock(VocabularyTerm.class);
        when(ancestor.getName()).thenReturn("Cataract");
        when(ancestor.getId()).thenReturn("HP:0000518");

        FeatureClusterView o = new DefaultFeatureClusterView(match, reference, open, ancestor);
        // Test that ancestor information is retrieved as Feature methods of ClusterView
        Assert.assertEquals(ancestor, o.getRoot());

        Assert.assertEquals(ancestor.getName(), o.getName());
        Assert.assertEquals(ancestor.getId(), o.getId());
    }

    /** When the ancestor is missing, ancestor is for "unmatched" terms. */
    @Test
    public void testGetAncestorDetailsWithNoAncestor()
    {
        Collection<Feature> match = new ArrayList<>();
        Collection<Feature> reference = new ArrayList<>();

        FeatureClusterView o = new DefaultFeatureClusterView(match, reference, open, null);
        // Test that ancestor information is retrieved as Feature methods of ClusterView
        Assert.assertEquals(null, o.getRoot());
        Assert.assertEquals("Unmatched", o.getName());
        Assert.assertEquals("", o.getId());
    }

    /** When there are no terms, the getMatch returns an empty collection. */
    @Test
    public void testGetEmptyMatch()
    {
        Collection<Feature> match = new ArrayList<>();
        Collection<Feature> reference = new ArrayList<>();

        FeatureClusterView o = new DefaultFeatureClusterView(match, reference, open, null);
        Assert.assertTrue(o.getMatch().isEmpty());
    }

    /** Basic test of getMatch. */
    @Test
    public void testGetMatch()
    {
        Collection<Feature> match = new ArrayList<>();
        Collection<Feature> reference = new ArrayList<>();

        FeatureClusterView o = new DefaultFeatureClusterView(match, reference, open, null);
        Assert.assertTrue(o.getMatch().isEmpty());

        match.add(new MockFeature("HP:0001382", "Joint hypermobility", PHENOTYPE, true));
        match.add(new MockFeature("HP:0012165", "Oligodactyly", PHENOTYPE, true));
        match.add(new MockFeature("HP:0000518", "Cataract", PHENOTYPE, true));
        match.add(new MockFeature("HP:0001249", "Intellectual disability", PHENOTYPE, false));
        match.add(new MockFeature("HP:0001256", "Mild intellectual disability", PHENOTYPE, true));

        o = new DefaultFeatureClusterView(match, reference, open, null);
        Assert.assertEquals(match.size(), o.getMatch().size());
        for (Feature f : o.getMatch()) {
            Assert.assertTrue(match.contains(f));
        }
    }

    /** Ensure that the match results are immutable. */
    @Test(expected = UnsupportedOperationException.class)
    public void testMatchIsImmutable()
    {
        Collection<Feature> match = new ArrayList<>();
        Collection<Feature> reference = new ArrayList<>();

        match.add(new MockFeature("HP:0001382", "Joint hypermobility", PHENOTYPE, true));

        FeatureClusterView o = new DefaultFeatureClusterView(match, reference, open, null);
        Assert.assertEquals(match.size(), o.getMatch().size());
        o.getMatch().clear();
    }

    /** Ensure that the reference results are immutable. */
    @Test(expected = UnsupportedOperationException.class)
    public void testReferenceIsImmutable()
    {
        Collection<Feature> match = new ArrayList<>();
        Collection<Feature> reference = new ArrayList<>();

        reference.add(new MockFeature("HP:0001382", "Joint hypermobility", PHENOTYPE, true));

        FeatureClusterView o = new DefaultFeatureClusterView(match, reference, open, null);
        Assert.assertEquals(reference.size(), o.getReference().size());
        o.getReference().clear();
    }

    /** Basic JSON tests. */
    @Test
    public void testToJSON()
    {
        List<Feature> match = new ArrayList<>();
        List<Feature> reference = new ArrayList<>();

        VocabularyTerm ancestor = mock(VocabularyTerm.class);
        when(ancestor.getName()).thenReturn("Cataract");
        when(ancestor.getId()).thenReturn("HP:0000518");

        reference.add(new MockFeature("HP:0012165", "Oligodactyly", PHENOTYPE, true));
        reference.add(new MockFeature("HP:0000518", "Cataract", PHENOTYPE, true));
        reference.add(new MockFeature("HP:0001249", "Intellectual disability", PHENOTYPE, true));

        match.add(new MockFeature("HP:0001382", "Joint hypermobility", PHENOTYPE, true));
        match.add(new MockFeature("HP:0012165", "Oligodactyly", PHENOTYPE, true));
        match.add(new MockFeature("HP:0000518", "Cataract", PHENOTYPE, true));
        match.add(new MockFeature("HP:0001256", "Mild intellectual disability", PHENOTYPE, true));

        FeatureClusterView o = new DefaultFeatureClusterView(match, reference, open, ancestor);

        JSONObject result = o.toJSON();
        Assert.assertEquals(3, result.length());

        // Category information (for unmatched terms)
        JSONObject category = result.getJSONObject("category");
        Assert.assertEquals(2, category.length());
        Assert.assertEquals(ancestor.getId(), category.getString("id"));
        Assert.assertEquals(ancestor.getName(), category.getString("name"));

        // Reference term ids
        JSONArray ref = result.getJSONArray("reference");
        Assert.assertEquals(reference.size(), ref.length());
        for (int i = 0; i < reference.size(); i++) {
            Assert.assertEquals(reference.get(i).getId(), ref.getString(i));
        }

        // Match term ids
        JSONArray mat = result.getJSONArray("match");
        Assert.assertEquals(match.size(), mat.length());
        for (int i = 0; i < match.size(); i++) {
            Assert.assertEquals(match.get(i).getId(), mat.getString(i));
        }
    }

    /** Basic JSON tests with no ancestor. */
    @Test
    public void testNoAncestorToJSON()
    {
        List<Feature> match = new ArrayList<>();
        List<Feature> reference = new ArrayList<>();

        reference.add(new MockFeature("HP:0012165", "Oligodactyly", PHENOTYPE, true));
        reference.add(new MockFeature("HP:0000518", "Cataract", PHENOTYPE, true));
        reference.add(new MockFeature("HP:0001249", "Intellectual disability", PHENOTYPE, true));

        match.add(new MockFeature("HP:0001382", "Joint hypermobility", PHENOTYPE, true));
        match.add(new MockFeature("HP:0012165", "Oligodactyly", PHENOTYPE, true));
        match.add(new MockFeature("HP:0000518", "Cataract", PHENOTYPE, true));
        match.add(new MockFeature("HP:0001256", "Mild intellectual disability", PHENOTYPE, true));

        FeatureClusterView o = new DefaultFeatureClusterView(match, reference, open, null);

        JSONObject result = o.toJSON();
        Assert.assertEquals(3, result.length());

        // Category information (for unmatched terms)
        JSONObject category = result.getJSONObject("category");
        Assert.assertEquals(2, category.length());
        Assert.assertEquals("", category.getString("id"));
        Assert.assertEquals("Unmatched", category.getString("name"));

        // Reference term ids
        JSONArray ref = result.getJSONArray("reference");
        Assert.assertEquals(reference.size(), ref.length());
        for (int i = 0; i < reference.size(); i++) {
            Assert.assertEquals(reference.get(i).getId(), ref.getString(i));
        }

        // Match term ids
        JSONArray mat = result.getJSONArray("match");
        Assert.assertEquals(match.size(), mat.length());
        for (int i = 0; i < match.size(); i++) {
            Assert.assertEquals(match.get(i).getId(), mat.getString(i));
        }
    }

    /** Basic JSON tests with no reference. */
    @Test
    public void testNoReferenceToJSON()
    {
        List<Feature> match = new ArrayList<>();
        List<Feature> reference = new ArrayList<>();

        match.add(new MockFeature("HP:0001382", "Joint hypermobility", PHENOTYPE, true));
        match.add(new MockFeature("HP:0012165", "Oligodactyly", PHENOTYPE, true));
        match.add(new MockFeature("HP:0000518", "Cataract", PHENOTYPE, true));
        match.add(new MockFeature("HP:0001256", "Mild intellectual disability", PHENOTYPE, true));

        FeatureClusterView o = new DefaultFeatureClusterView(match, reference, open, null);

        JSONObject result = o.toJSON();
        Assert.assertEquals(3, result.length());

        // Category information (for unmatched terms)
        JSONObject category = result.getJSONObject("category");
        Assert.assertEquals(2, category.length());
        Assert.assertEquals("", category.getString("id"));
        Assert.assertEquals("Unmatched", category.getString("name"));

        // No reference key at all
        Assert.assertEquals(0, result.getJSONArray("reference").length());

        // Match term ids
        JSONArray mat = result.getJSONArray("match");
        Assert.assertEquals(match.size(), mat.length());
        for (int i = 0; i < match.size(); i++) {
            Assert.assertEquals(match.get(i).getId(), mat.getString(i));
        }
    }

    /** Basic JSON tests with no match. */
    @Test
    public void testNoMatchToJSON()
    {
        List<Feature> match = new ArrayList<>();
        List<Feature> reference = new ArrayList<>();

        reference.add(new MockFeature("HP:0012165", "Oligodactyly", PHENOTYPE, true));
        reference.add(new MockFeature("HP:0000518", "Cataract", PHENOTYPE, true));
        reference.add(new MockFeature("HP:0001249", "Intellectual disability", PHENOTYPE, true));

        FeatureClusterView o = new DefaultFeatureClusterView(match, reference, open, null);

        JSONObject result = o.toJSON();
        Assert.assertEquals(3, result.length());

        // Reference term ids
        JSONArray ref = result.getJSONArray("reference");
        Assert.assertEquals(reference.size(), ref.length());
        for (int i = 0; i < reference.size(); i++) {
            Assert.assertEquals(reference.get(i).getId(), ref.getString(i));
        }

        // Match term ids
        Assert.assertEquals(0, result.getJSONArray("match").length());
    }
}
