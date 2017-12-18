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
import org.phenotips.data.similarity.PatientPhenotypeSimilarityView;
import org.phenotips.data.similarity.PatientPhenotypeSimilarityViewFactory;

import org.xwiki.component.annotation.Component;

import java.util.Set;

import javax.inject.Singleton;

/**
 * Implementation of {@link PatientPhenotypeSimilarityViewFactory} that always reveals the full patient phenotype
 *  information; for use in trusted code.
 *
 * @version $Id$
 * @since 1.1
 */
@Component
@Singleton
public class DefaultPatientPhenotypeSimilarityViewFactory implements PatientPhenotypeSimilarityViewFactory
{
    @Override
    public PatientPhenotypeSimilarityView createPatientPhenotypeSimilarityView(Set<? extends Feature> matchFeatures,
        Set<? extends Feature> refFeatures, AccessType access)
    {
        return new DefaultPatientPhenotypeSimilarityView(matchFeatures, refFeatures, access);
    }
}
