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
package org.phenotips.data.similarity.permissions.internal;

import org.phenotips.data.permissions.AccessLevel;
import org.phenotips.data.permissions.internal.AbstractVisibility;

import org.xwiki.component.annotation.Component;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

/**
 * Allows returning the patient as the result of a similarity search, but only features similar between the two
 * patients, reference and match, will be accessible. On the permissiveness level, this sits between right above
 * {@link org.phenotips.data.permissions.internal.visibility.PrivateVisibility no access}.
 *
 * @version $Id$
 */
@Component
@Named("matchable")
@Singleton
public class MatchableVisibility extends AbstractVisibility
{
    /** @see #getDefaultAccessLevel() */
    @Inject
    @Named("match")
    private AccessLevel access;

    /** Default constructor. */
    public MatchableVisibility()
    {
        super(5);
    }

    @Override
    public String getName()
    {
        return "matchable";
    }

    @Override
    public AccessLevel getDefaultAccessLevel()
    {
        return this.access;
    }
}
