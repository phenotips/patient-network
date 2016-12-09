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
package org.phenotips.matchingnotification.finder.internal;

import org.phenotips.matchingnotification.finder.MatchFinder;

import org.xwiki.component.annotation.Component;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.component.manager.ComponentManager;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

/**
 * @version $Id$
 */
@Component
@Singleton
public class MatchFinderListProvider implements Provider<List<MatchFinder>>
{
    @Inject
    @Named("wiki")
    private ComponentManager componentManager;

    @Override
    public List<MatchFinder> get() {

        List<MatchFinder> services = null;
        try {
            services = new LinkedList<>(this.componentManager.<MatchFinder>getInstanceList(MatchFinder.class));
        } catch (ComponentLookupException ex) {
            throw new RuntimeException("Failed to look up instance of MatchFinder", ex);
        }

        Collections.sort(services, new Comparator<MatchFinder>() {
            @Override
            public int compare(MatchFinder o1, MatchFinder o2) {
                return o2.getPriority() - o1.getPriority();
            }
        });
        return services;

    }

}
