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

import org.phenotips.Constants;
import org.phenotips.data.similarity.configuration.SimilarityConfiguration;

import org.xwiki.bridge.DocumentAccessBridge;
import org.xwiki.component.annotation.Component;
import org.xwiki.model.EntityType;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReference;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.commons.lang3.StringUtils;

/**
 * Default implementation of the {@link SimilarityConfiguration} component, reading values configured in the wiki using
 * a {@code PhenoTips.SimilarCases} object in the {@code SimilarCasesConfiguration} document.
 * 
 * @version $Id$
 */
@Component
@Singleton
public class WikiSimilarityConfiguration implements SimilarityConfiguration
{
    /** The XDocument where the configuration is stored. */
    private static final EntityReference CONFIGURATION_LOCATION = new EntityReference("SimilarCasesConfiguration",
        EntityType.DOCUMENT, new EntityReference("data", EntityType.SPACE));

    /** The XClass used for configuration. */
    private static final EntityReference CONFIGURATION_CLASS = new EntityReference("SimilarCases", EntityType.DOCUMENT,
        Constants.CODE_SPACE_REFERENCE);

    /** Provides access to the wiki data. */
    @Inject
    private DocumentAccessBridge bridge;

    /** Resolves partial references in the current wiki. */
    @Inject
    @Named("current")
    private DocumentReferenceResolver<EntityReference> entityResolver;

    @Override
    public String getScorerType()
    {
        Object configured = this.bridge.getProperty(this.entityResolver.resolve(CONFIGURATION_LOCATION),
            this.entityResolver.resolve(CONFIGURATION_CLASS), "scorer");
        if (configured == null || StringUtils.isBlank(String.valueOf(configured))) {
            return "default";
        }
        return String.valueOf(configured);
    }
}
