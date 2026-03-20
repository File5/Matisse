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

import java.util.concurrent.ConcurrentHashMap;

/**
 * Default in-memory {@link PreFilterCache} backed by a {@link ConcurrentHashMap}.
 * A new instance is created for each Matisse session, so cached results
 * survive album switches but not session restarts.
 */
public class InMemoryPreFilterCache implements PreFilterCache {

    private final ConcurrentHashMap<Long, Boolean> mCache = new ConcurrentHashMap<>();

    @Override
    @Nullable
    public Boolean get(long itemId) {
        return mCache.get(itemId);
    }

    @Override
    public void put(long itemId, boolean accepted) {
        mCache.put(itemId, accepted);
    }

    @Override
    public void clear() {
        mCache.clear();
    }
}
