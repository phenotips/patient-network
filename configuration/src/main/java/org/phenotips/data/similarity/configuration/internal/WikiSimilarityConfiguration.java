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
 * @since 1.0M1
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
