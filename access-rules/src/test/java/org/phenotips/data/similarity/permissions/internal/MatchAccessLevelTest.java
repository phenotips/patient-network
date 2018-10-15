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
package org.phenotips.data.similarity.permissions.internal;

import org.phenotips.data.permissions.AccessLevel;
import org.phenotips.translation.TranslationManager;

import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.security.authorization.Right;
import org.xwiki.test.mockito.MockitoComponentMockingRule;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Matchers;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for the {@link MatchAccessLevel matchable access level}.
 *
 * @version $Id$
 */
public class MatchAccessLevelTest
{
    @Rule
    public final MockitoComponentMockingRule<AccessLevel> mocker =
        new MockitoComponentMockingRule<AccessLevel>(MatchAccessLevel.class);

    @Before
    public void setup() throws ComponentLookupException
    {
        TranslationManager tm = this.mocker.getInstance(TranslationManager.class);
        when(tm.translate(Matchers.anyString())).thenReturn("");
    }

    /** Basic test for {@link AccessLevel#getName()}. */
    @Test
    public void getName() throws ComponentLookupException
    {
        Assert.assertEquals("match", this.mocker.getComponentUnderTest().getName());
    }

    /** Basic test for {@link AccessLevel#isAssignable()}. */
    @Test
    public void isAssignable() throws ComponentLookupException
    {
        Assert.assertFalse(this.mocker.getComponentUnderTest().isAssignable());
    }

    /** Basic test for {@link AccessLevel#getLabel()}. */
    @Test
    public void getLabel() throws ComponentLookupException
    {
        TranslationManager tm = this.mocker.getInstance(TranslationManager.class);
        when(tm.translate("phenotips.permissions.accessLevels.match.label")).thenReturn("Match");
        Assert.assertEquals("Match", this.mocker.getComponentUnderTest().getLabel());
    }

    /** {@link AccessLevel#getLabel()} returns the name when a translation isn't found. */
    @Test
    public void getLabelWithoutTranslation() throws ComponentLookupException
    {
        Assert.assertEquals("match", this.mocker.getComponentUnderTest().getLabel());
    }

    /** Basic test for {@link AccessLevel#getDescription()}. */
    @Test
    public void getDescription() throws ComponentLookupException
    {
        TranslationManager tm = this.mocker.getInstance(TranslationManager.class);
        when(tm.translate("phenotips.permissions.accessLevels.match.description"))
            .thenReturn("Only features similar to a reference patient are accessible.");
        Assert.assertEquals("Only features similar to a reference patient are accessible.",
            this.mocker.getComponentUnderTest().getDescription());
    }

    /** {@link AccessLevel#getDescription()} returns an empty string when a translation isn't found. */
    @Test
    public void getDescriptionWithoutTranslation() throws ComponentLookupException
    {
        Assert.assertEquals("", this.mocker.getComponentUnderTest().getDescription());
    }

    /** Basic test for {@link AccessLevel#toString()}. */
    @Test
    public void toStringTest() throws ComponentLookupException
    {
        Assert.assertEquals("match", this.mocker.getComponentUnderTest().toString());
    }

    /** Basic test for {@link AccessLevel#equals(Object)}. */
    @Test
    public void equalsTest() throws ComponentLookupException
    {
        // Equals itself
        Assert.assertTrue(this.mocker.getComponentUnderTest().equals(this.mocker.getComponentUnderTest()));
        // Never equals null
        Assert.assertFalse(this.mocker.getComponentUnderTest().equals(null));
        // Equals another level with the same name
        AccessLevel other = mock(AccessLevel.class);
        when(other.getName()).thenReturn("match", "view");
        Assert.assertTrue(this.mocker.getComponentUnderTest().equals(other));
        // Doesn't equal a level with a different name
        Assert.assertFalse(this.mocker.getComponentUnderTest().equals(other));
        // Doesn't equal other types of objects
        Assert.assertFalse(this.mocker.getComponentUnderTest().equals("match"));
    }

    /** Basic test for {@link AccessLevel#compareTo(AccessLevel)}. */
    @Test
    public void compareToTest() throws ComponentLookupException
    {
        // Equals itself
        Assert.assertEquals(0, this.mocker.getComponentUnderTest().compareTo(this.mocker.getComponentUnderTest()));
        // If not instanceof AbstractAccessLevel then compareTo() returns Integer.MAX_VALUE
        Assert.assertEquals(Integer.MAX_VALUE, this.mocker.getComponentUnderTest().compareTo(null));
        // Equals another level with the same permissiveness
        Assert.assertEquals(0, this.mocker.getComponentUnderTest().compareTo(new MockAccessLevel("partial", 5, true)));
        // Respects the permissiveness order
        Assert.assertTrue(this.mocker.getComponentUnderTest().compareTo(new MockAccessLevel("private", 0, true)) > 0);
        Assert.assertTrue(this.mocker.getComponentUnderTest().compareTo(new MockAccessLevel("manage", 30, true)) < 0);
        // Other types of levels are placed before
        Assert.assertEquals(Integer.MAX_VALUE, this.mocker.getComponentUnderTest().compareTo(mock(AccessLevel.class)));
    }

    /** Basic tests for {@link AccessLevel#hashCode()}. */
    @Test
    public void hashCodeTest() throws ComponentLookupException
    {
        AccessLevel edit = this.mocker.getComponentUnderTest();
        AccessLevel other = new MockAccessLevel("match", 120, false);
        // Same hashcode for a different access level with the same name and assignable flag, ignoring permissiveness
        Assert.assertEquals(edit.hashCode(), other.hashCode());
        // Different hashcodes for different coordinates
        other = new MockAccessLevel("view", 5, false);
        Assert.assertNotEquals(edit.hashCode(), other.hashCode());
        other = new MockAccessLevel("match", 5, true);
        Assert.assertNotEquals(edit.hashCode(), other.hashCode());
        other = new MockAccessLevel("none", 5, true);
        Assert.assertNotEquals(edit.hashCode(), other.hashCode());
    }

    @Test
    public void grantsNoRight() throws ComponentLookupException
    {
        Assert.assertEquals(Right.ILLEGAL, this.mocker.getComponentUnderTest().getGrantedRight());
    }
}
