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
package org.phenotips.data.similarity.configuration.internal;

import org.phenotips.data.similarity.configuration.SimilarityConfiguration;

import org.xwiki.bridge.DocumentAccessBridge;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.test.mockito.MockitoComponentMockingRule;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.when;

/**
 * Tests for the default {@link SimilarityConfiguration} implementation, {@link WikiSimilarityConfiguration}.
 * 
 * @version $Id$
 */
public class WikiSimilarityConfigurationTest
{
    @Rule
    public final MockitoComponentMockingRule<SimilarityConfiguration> mocker =
        new MockitoComponentMockingRule<SimilarityConfiguration>(WikiSimilarityConfiguration.class);

    /** {@link SimilarityConfiguration#getScorerType()} returns the configured value. */
    @Test
    public void getScorerTypeWithValidConfiguration() throws ComponentLookupException
    {
        DocumentAccessBridge b = this.mocker.getInstance(DocumentAccessBridge.class);
        when(b.getProperty(any(DocumentReference.class), any(DocumentReference.class), same("scorer")))
            .thenReturn("ic");
        Assert.assertEquals("ic", this.mocker.getComponentUnderTest().getScorerType());
    }

    /** {@link SimilarityConfiguration#getScorerType()} returns the default value when there's no configuration. */
    @Test
    public void getScorerTypeWithNoConfiguration() throws ComponentLookupException
    {
        Assert.assertEquals("default", this.mocker.getComponentUnderTest().getScorerType());
    }

    /** {@link SimilarityConfiguration#getScorerType()} returns the default value when there configuration is empty. */
    @Test
    public void getScorerTypeWithEmptyConfiguration() throws ComponentLookupException
    {
        DocumentAccessBridge b = this.mocker.getInstance(DocumentAccessBridge.class);
        when(b.getProperty(any(DocumentReference.class), any(DocumentReference.class), same("scorer"))).thenReturn("");
        Assert.assertEquals("default", this.mocker.getComponentUnderTest().getScorerType());
    }
}
