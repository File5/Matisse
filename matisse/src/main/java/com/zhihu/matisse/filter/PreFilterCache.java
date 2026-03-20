/*
 * Copyright 2017 Zhihu Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.zhihu.matisse.filter;

import androidx.annotation.Nullable;

/**
 * Cache interface for pre-filter results. Implementations must be thread-safe
 * as they are accessed from background threads during media scanning.
 * <p>
 * The default implementation is {@link InMemoryPreFilterCache}. Consumers can
 * provide their own (e.g., Room/SQLite-backed, LRU-bounded, disk-persistent)
 * via {@link com.zhihu.matisse.SelectionCreator#preFilterCacheImpl(PreFilterCache)}.
 * <p>
 * Pass {@code null} to disable caching entirely.
 */
public interface PreFilterCache {

    /**
     * Look up a cached result.
     *
     * @param itemId Media item ID.
     * @return cached acceptance state, or null if not yet evaluated.
     */
    @Nullable
    Boolean get(long itemId);

    /**
     * Store a filter result.
     *
     * @param itemId   Media item ID.
     * @param accepted Whether the item passed all pre-filters.
     */
    void put(long itemId, boolean accepted);

    /**
     * Clear all cached results.
     */
    void clear();
}
