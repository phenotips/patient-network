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
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for the "restricted" {@link FeatureClusterView} implementation, {@link RestrictedFeatureClusterView}.
 *
 * @version $Id$
 */
public class RestrictedFeatureClusterViewTest
{
    private static AccessType open;

    private static AccessType limited;

    private static AccessType priv;

    @BeforeClass
    public static void setupAccessTypes()
    {
        open = mock(AccessType.class);
        when(open.isOpenAccess()).thenReturn(true);
        when(open.isLimitedAccess()).thenReturn(false);
        when(open.isPrivateAccess()).thenReturn(false);
        when(open.toString()).thenReturn("owner");

        limited = mock(AccessType.class);
        when(limited.isOpenAccess()).thenReturn(false);
        when(limited.isLimitedAccess()).thenReturn(true);
        when(limited.isPrivateAccess()).thenReturn(false);
        when(limited.toString()).thenReturn("match");

        priv = mock(AccessType.class);
        when(priv.isOpenAccess()).thenReturn(false);
        when(priv.isLimitedAccess()).thenReturn(false);
        when(priv.isPrivateAccess()).thenReturn(true);
        when(priv.toString()).thenReturn("none");
    }

    /** Ancestor data is available with public access. */
    @Test
    public void testGetAncestorDetailsWithPublicAccess()
    {
        Collection<Feature> match = new ArrayList<Feature>();
        Collection<Feature> reference = new ArrayList<Feature>();

        VocabularyTerm ancestor = mock(VocabularyTerm.class);
        when(ancestor.getName()).thenReturn("Cataract");
        when(ancestor.getId()).thenReturn("HP:0000518");
        double score = 0.1234;

        FeatureClusterView o = new RestrictedFeatureClusterView(match, reference, open, ancestor, score);
        // Test that ancestor information is not retrieved as Feature methods of ClusterView
        Assert.assertEquals(ancestor, o.getRoot());
        Assert.assertEquals(score, o.getScore(), 1E-5);
        Assert.assertEquals(ancestor.getName(), o.getName());
        Assert.assertEquals(ancestor.getId(), o.getId());

        // Test that type is "ancestor" and it is not present (by convention)
        Assert.assertEquals("ancestor", o.getType());
        Assert.assertFalse(o.isPresent());
        Assert.assertTrue(o.getMetadata().isEmpty());
    }

    /** Ancestor data is available with match access. */
    @Test
    public void testGetAncestorDetailsWithMatchAccess()
    {
        Collection<Feature> match = new ArrayList<Feature>();
        Collection<Feature> reference = new ArrayList<Feature>();

        VocabularyTerm ancestor = mock(VocabularyTerm.class);
        when(ancestor.getName()).thenReturn("Cataract");
        when(ancestor.getId()).thenReturn("HP:0000518");
        double score = 0.1234;

        FeatureClusterView o = new RestrictedFeatureClusterView(match, reference, limited, ancestor, score);
        // Test that ancestor information is not retrieved as Feature methods of ClusterView
        Assert.assertEquals(ancestor, o.getRoot());
        Assert.assertEquals(score, o.getScore(), 1E-5);
        Assert.assertEquals(ancestor.getName(), o.getName());
        Assert.assertEquals(ancestor.getId(), o.getId());

        // Test that type is "ancestor" and it is not present (by convention)
        Assert.assertEquals("ancestor", o.getType());
        Assert.assertFalse(o.isPresent());
        Assert.assertTrue(o.getMetadata().isEmpty());
    }

    /**
     * With matchable access, if the reference phenotype is the same as the cluster, the ancestor returned should be the
     * parent of the actual ancestor.
     */
    @Test
    public void testGetAncestorParentDetailsWithMatchAccess()
    {
        Collection<Feature> match = new ArrayList<Feature>();
        Collection<Feature> reference = new ArrayList<Feature>();

        VocabularyTerm ancestor = mock(VocabularyTerm.class);
        when(ancestor.getName()).thenReturn("Cataract");
        when(ancestor.getId()).thenReturn("HP:0000518");
        double score = 0.1234;

        // Parent ancestor whose details should be shown
        VocabularyTerm ancestorParent = mock(VocabularyTerm.class);
        when(ancestorParent.getName()).thenReturn("Abnormality of the lens");
        when(ancestorParent.getId()).thenReturn("HP:0000517");
        when(ancestor.getParents()).thenReturn(Collections.singleton(ancestorParent));

        reference.add(new MockFeature("HP:0000518", "Cataract", "phenotype", true));
        match.add(new MockFeature("HP:0010696", "Polar cataract", "phenotype", true));

        FeatureClusterView o = new RestrictedFeatureClusterView(match, reference, limited, ancestor, score);
        // Test that ancestor information is not retrieved as Feature methods of ClusterView
        Assert.assertEquals(ancestorParent, o.getRoot());
        Assert.assertEquals(score, o.getScore(), 1E-5);
        Assert.assertEquals(ancestorParent.getName(), o.getName());
        Assert.assertEquals(ancestorParent.getId(), o.getId());

        // Test that type is "ancestor" and it is not present (by convention)
        Assert.assertEquals("ancestor", o.getType());
        Assert.assertFalse(o.isPresent());
        Assert.assertTrue(o.getMetadata().isEmpty());
    }

    /** Ancestor data is unavailable with private access. */
    @Test
    public void testGetAncestorDetailsWithPrivateAccess()
    {
        Collection<Feature> match = new ArrayList<Feature>();
        Collection<Feature> reference = new ArrayList<Feature>();

        VocabularyTerm ancestor = mock(VocabularyTerm.class);
        when(ancestor.getName()).thenReturn("Cataract");
        when(ancestor.getId()).thenReturn("HP:0000518");
        double score = 0.1234;

        FeatureClusterView o = new RestrictedFeatureClusterView(match, reference, priv, ancestor, score);
        // Test that ancestor information is not retrieved as Feature methods of ClusterView
        Assert.assertEquals(null, o.getRoot());
        Assert.assertEquals(0.0, o.getScore(), 1E-5);
        Assert.assertEquals("Unmatched", o.getName());
        Assert.assertEquals("", o.getId());

        // Test that type is "ancestor" and it is not present (by convention)
        Assert.assertEquals("ancestor", o.getType());
        Assert.assertFalse(o.isPresent());
        Assert.assertTrue(o.getMetadata().isEmpty());
    }

    /** With public access, getMatch should return everything. */
    @Test
    public void testGetMatchWithPublicAccess()
    {
        Collection<Feature> match = new ArrayList<Feature>();
        Collection<Feature> reference = new ArrayList<Feature>();
        double score = 0.0;

        FeatureClusterView o = new RestrictedFeatureClusterView(match, reference, open, null, score);
        Assert.assertTrue(o.getMatch().isEmpty());

        match.add(new MockFeature("HP:0001382", "Joint hypermobility", "phenotype", true));
        match.add(new MockFeature("HP:0012165", "Oligodactyly", "phenotype", true));
        match.add(new MockFeature("HP:0000518", "Cataract", "phenotype", true));
        match.add(new MockFeature("HP:0001249", "Intellectual disability", "phenotype", false));
        match.add(new MockFeature("HP:0001256", "Mild intellectual disability", "phenotype", true));

        o = new RestrictedFeatureClusterView(match, reference, open, null, score);
        Assert.assertEquals(match.size(), o.getMatch().size());
        for (Feature f : o.getMatch()) {
            Assert.assertTrue(match.contains(f));
        }
    }

    /** With match access, getMatch should return information about the number of features but no details. */
    @Test
    public void testGetMatchWithMatchAccess()
    {
        Collection<Feature> match = new ArrayList<Feature>();
        Collection<Feature> reference = new ArrayList<Feature>();
        double score = 0.0;

        FeatureClusterView o = new RestrictedFeatureClusterView(match, reference, limited, null, score);
        Assert.assertTrue(o.getMatch().isEmpty());

        match.add(new MockFeature("HP:0001382", "Joint hypermobility", "phenotype", true));
        match.add(new MockFeature("HP:0012165", "Oligodactyly", "phenotype", true));
        match.add(new MockFeature("HP:0000518", "Cataract", "phenotype", true));
        match.add(new MockFeature("HP:0001249", "Intellectual disability", "phenotype", false));
        match.add(new MockFeature("HP:0001256", "Mild intellectual disability", "phenotype", true));

        // All features are null, but the count is correct
        o = new RestrictedFeatureClusterView(match, reference, limited, null, score);
        Assert.assertEquals(match.size(), o.getMatch().size());
        for (Feature f : o.getMatch()) {
            Assert.assertEquals(null, f);
        }
    }

    /** With private access, getMatch should return nothing. */
    @Test
    public void testGetMatchWithPrivateAccess()
    {
        Collection<Feature> match = new ArrayList<Feature>();
        Collection<Feature> reference = new ArrayList<Feature>();
        double score = 0.0;

        FeatureClusterView o = new RestrictedFeatureClusterView(match, reference, priv, null, score);
        Assert.assertTrue(o.getMatch().isEmpty());

        match.add(new MockFeature("HP:0001382", "Joint hypermobility", "phenotype", true));
        match.add(new MockFeature("HP:0012165", "Oligodactyly", "phenotype", true));
        match.add(new MockFeature("HP:0000518", "Cataract", "phenotype", true));
        match.add(new MockFeature("HP:0001249", "Intellectual disability", "phenotype", false));
        match.add(new MockFeature("HP:0001256", "Mild intellectual disability", "phenotype", true));

        // All features are null, but the count is correct
        o = new RestrictedFeatureClusterView(match, reference, priv, null, score);
        Assert.assertTrue(o.getMatch().isEmpty());
    }

    /** Basic JSON tests. */
    @Test
    public void testToJSONWithPublicAccess()
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

        FeatureClusterView o = new RestrictedFeatureClusterView(match, reference, open, ancestor, score);

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

    /**
     * Basic JSON tests with private access, should contain reference information but no match element. Categories of
     * identical matches should report the parent term.
     */
    @Test
    public void testToJSONWithMatchAccess()
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

        FeatureClusterView o = new RestrictedFeatureClusterView(match, reference, limited, ancestor, score);

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

        // Match term ids should all be empty strings
        JSONArray mat = result.getJSONArray("match");
        Assert.assertEquals(match.size(), mat.size());
        for (int i = 0; i < match.size(); i++) {
            Assert.assertEquals("", mat.getString(i));
        }
    }

    /** Basic JSON tests with private access, should contain reference information but no match element. */
    @Test
    public void testToJSONWithPrivateAccess()
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

        FeatureClusterView o = new RestrictedFeatureClusterView(match, reference, priv, ancestor, score);

        JSONObject result = o.toJSON();
        Assert.assertEquals(3, result.size());

        // Score at root level
        Assert.assertEquals(0.0, result.getDouble("score"), 1E-10);

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
        Assert.assertFalse(result.containsKey("match"));
    }

}
