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
import org.phenotips.vocabulary.VocabularyTerm;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Implementation of {@link org.phenotips.data.similarity.FeatureClusterView} that reveals the full patient information
 * if the user has full access to the patient, and only matching reference information for similar features if the
 * patient is matchable.
 *
 * @version $Id$
 * @since 1.0M1
 */
public class RestrictedFeatureClusterView extends DefaultFeatureClusterView
{
    /**
     * Constructor passing the {@link #match matched feature} and the {@link #reference reference feature}.
     *
     * @param match the features in the matched patient, can be empty
     * @param reference the features in the reference patient, can be empty
     * @param access the access level of the match
     * @param root the root/shared ancestor for the cluster, can be {@code null} to represent unmatched features
     * @throws IllegalArgumentException if match or reference are null
     */
    public RestrictedFeatureClusterView(Collection<Feature> match, Collection<Feature> reference, AccessType access,
        VocabularyTerm root) throws IllegalArgumentException
    {
        super(match, reference, access, root);
    }

    @Override
    public Collection<Feature> getMatch()
    {
        // null access is passed when we go through email notification process, user is admin
        if (this.access == null || this.access.isOpenAccess()) {
            return super.getMatch();
        } else if (this.access.isPrivateAccess()) {
            return Collections.emptySet();
        } else {
            List<Feature> hiddenFeatures = new ArrayList<Feature>(this.match.size());
            for (int i = 0; i < this.match.size(); i++) {
                hiddenFeatures.add(null);
            }
            return Collections.unmodifiableCollection(hiddenFeatures);
        }
    }
}
