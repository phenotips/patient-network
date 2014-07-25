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
package org.phenotips.data.similarity.internal;

import org.phenotips.components.ComponentManagerRegistry;

import org.xwiki.cache.Cache;
import org.xwiki.cache.CacheException;
import org.xwiki.cache.CacheManager;
import org.xwiki.cache.config.CacheConfiguration;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.component.manager.ComponentManager;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/**
 * Wrapper over an xwiki cache to better handle keys corresponding to pairs of entities.
 * 
 * @version $Id$
 * @param <T>
 * @since
 */
public class PairCache<T>
{
    /** The cache being wrapped. */
    private Cache<T> cache;

    /** Keys of all cache entries involving one of the IDs. */
    private Map<String, Collection<String>> idEntries;

    public PairCache() throws CacheException
    {
        this.idEntries = new HashMap<String, Collection<String>>();

        try {
            ComponentManager componentManager = ComponentManagerRegistry.getContextComponentManager();
            CacheManager cacheManager = componentManager.getInstance(CacheManager.class);
            this.cache = cacheManager.createNewLocalCache(new CacheConfiguration());
        } catch (ComponentLookupException e) {
            throw new CacheException("Error getting local cache factory");
        }
    }

    /**
     * Add the cacheKey to the collection of cache keys associated with the given ID.
     * 
     * @param id the unique ID to associate with the cache key.
     * @param cacheKey the cache key to associate with.
     */
    private void associateWithCacheKey(String id, String cacheKey)
    {
        Collection<String> associatedKeys = this.idEntries.get(id);
        if (associatedKeys == null) {
            associatedKeys = new HashSet<String>();
            this.idEntries.put(id, associatedKeys);
        }
        associatedKeys.add(cacheKey);
    }

    /**
     * Store the cacheKey and corresponding value in the cache, and associate this entry with the pair of id strings.
     * 
     * @param id1 an id to associate with the entry.
     * @param id2 another id to associate with the entry.
     * @param cacheKey the key for the cache entry.
     * @param value the value to insert into the cache.
     */
    public void set(String id1, String id2, String cacheKey, T value)
    {
        this.cache.set(cacheKey, value);

        // Associate each key with the cacheKey
        associateWithCacheKey(id1, cacheKey);
        associateWithCacheKey(id2, cacheKey);
    }

    /**
     * Get the value associated with the given cacheKey.
     * 
     * @param cacheKey the key to query from the cache.
     * @return the value associated with the key, or null if there is no value.
     */
    public T get(String cacheKey)
    {
        return this.cache.get(cacheKey);
    }

    /**
     * Clear all cache entries involving a specific ID.
     * 
     * @param id the id specified as one of a pair associated with cache entries.
     */
    public void removeAssociated(String id)
    {
        // Pop any existing mappings and remove them from the cache
        Collection<String> cacheKeys = this.idEntries.remove(id);
        if (cacheKeys != null) {
            for (String cacheKey : cacheKeys) {
                this.cache.remove(cacheKey);
            }
        }
    }

    /**
     * Remove all entries from the cache.
     */
    public void removeAll()
    {
        this.cache.removeAll();
        this.idEntries.clear();
    }
}
