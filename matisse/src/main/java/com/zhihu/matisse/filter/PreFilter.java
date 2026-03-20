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

import android.content.Context;

import com.zhihu.matisse.internal.entity.Item;

/**
 * Pre-filter for hiding media items from the grid before the user can select them.
 * Unlike {@link Filter} which blocks selection but still shows items, PreFilter
 * removes items from the grid entirely.
 * <p>
 * Pre-filters are evaluated on a background thread during media loading.
 * Implementations should be thread-safe.
 */
public abstract class PreFilter {

    /**
     * Evaluate whether a media item should be shown in the grid.
     *
     * @param context Application context.
     * @param item    The media item to evaluate.
     * @return true if the item should be shown, false to hide it from the grid.
     */
    public abstract boolean accept(Context context, Item item);
}
