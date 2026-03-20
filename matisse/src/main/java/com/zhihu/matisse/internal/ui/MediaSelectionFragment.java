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
package com.zhihu.matisse.internal.ui;

import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.OnLifecycleEvent;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.zhihu.matisse.R;
import com.zhihu.matisse.filter.PreFilter;
import com.zhihu.matisse.internal.entity.Album;
import com.zhihu.matisse.internal.entity.Item;
import com.zhihu.matisse.internal.entity.SelectionSpec;
import com.zhihu.matisse.internal.model.AlbumMediaCollection;
import com.zhihu.matisse.internal.model.SelectedItemCollection;
import com.zhihu.matisse.internal.ui.adapter.AlbumMediaAdapter;
import com.zhihu.matisse.internal.ui.widget.MediaGridInset;
import com.zhihu.matisse.internal.utils.UIUtils;

import java.util.ArrayList;
import java.util.List;

public class MediaSelectionFragment extends Fragment implements
        AlbumMediaCollection.AlbumMediaCallbacks, AlbumMediaAdapter.CheckStateListener,
        AlbumMediaAdapter.OnMediaClickListener {

    public static final String EXTRA_ALBUM = "extra_album";

    private static final String[] MEDIA_COLUMNS = {
            MediaStore.Files.FileColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.SIZE,
            "duration"
    };

    private final AlbumMediaCollection mAlbumMediaCollection = new AlbumMediaCollection();
    private RecyclerView mRecyclerView;
    private ProgressBar mProgressBar;
    private AlbumMediaAdapter mAdapter;
    private SelectionProvider mSelectionProvider;
    private AlbumMediaAdapter.CheckStateListener mCheckStateListener;
    private AlbumMediaAdapter.OnMediaClickListener mOnMediaClickListener;
    private volatile int mPreFilterVersion = 0;
    public static MediaSelectionFragment newInstance(Album album) {
        MediaSelectionFragment fragment = new MediaSelectionFragment();
        Bundle args = new Bundle();
        args.putParcelable(EXTRA_ALBUM, album);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof SelectionProvider) {
            mSelectionProvider = (SelectionProvider) context;
        } else {
            throw new IllegalStateException("Context must implement SelectionProvider.");
        }
        if (context instanceof AlbumMediaAdapter.CheckStateListener) {
            mCheckStateListener = (AlbumMediaAdapter.CheckStateListener) context;
        }
        if (context instanceof AlbumMediaAdapter.OnMediaClickListener) {
            mOnMediaClickListener = (AlbumMediaAdapter.OnMediaClickListener) context;
        }
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_media_selection, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mRecyclerView = (RecyclerView) view.findViewById(R.id.recyclerview);
        mProgressBar = (ProgressBar) view.findViewById(R.id.pre_filter_progress);
        getActivity().getLifecycle().addObserver(new LifecycleObserver() {
            @OnLifecycleEvent(Lifecycle.Event.ON_CREATE)
            public void onCreated(){
                Album album = getArguments().getParcelable(EXTRA_ALBUM);
                mAdapter = new AlbumMediaAdapter(getContext(),
                    mSelectionProvider.provideSelectedItemCollection(), mRecyclerView);
                mAdapter.registerCheckStateListener(MediaSelectionFragment.this);
                mAdapter.registerOnMediaClickListener(MediaSelectionFragment.this);
                mRecyclerView.setHasFixedSize(true);

                int spanCount;
                SelectionSpec selectionSpec = SelectionSpec.getInstance();
                if (selectionSpec.gridExpectedSize > 0) {
                    spanCount = UIUtils.spanCount(getContext(), selectionSpec.gridExpectedSize);
                } else {
                    spanCount = selectionSpec.spanCount;
                }
                mRecyclerView.setLayoutManager(new GridLayoutManager(getContext(), spanCount));

                int spacing = getResources().getDimensionPixelSize(R.dimen.media_grid_spacing);
                mRecyclerView.addItemDecoration(new MediaGridInset(spanCount, spacing, false));
                mRecyclerView.setAdapter(mAdapter);
                mAlbumMediaCollection.onCreate(getActivity(), MediaSelectionFragment.this);
                mAlbumMediaCollection.load(album, selectionSpec.capture, hashCode());
            }

            @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            public void onDestroy(){
            }
        });
    }

    @Override public void onDestroy() {
        super.onDestroy();
        mAlbumMediaCollection.onDestroy();
    }

    public void refreshMediaGrid() {
        mAdapter.notifyDataSetChanged();
    }

    public void refreshSelection() {
        mAdapter.refreshSelection();
    }

    @Override
    public void onAlbumMediaLoad(Cursor cursor) {
        SelectionSpec spec = SelectionSpec.getInstance();
        if (spec.preFilters != null && !spec.preFilters.isEmpty()) {
            applyPreFilters(cursor);
        } else {
            mProgressBar.setVisibility(View.GONE);
            mRecyclerView.setVisibility(View.VISIBLE);
            mAdapter.swapCursor(cursor);
        }
    }

    @Override
    public void onAlbumMediaReset() {
        mPreFilterVersion++;
        mAdapter.swapCursor(null);
    }

    private void applyPreFilters(Cursor cursor) {
        mProgressBar.setVisibility(View.VISIBLE);
        mRecyclerView.setVisibility(View.GONE);

        final int version = ++mPreFilterVersion;
        final Context context = getContext().getApplicationContext();
        final List<PreFilter> preFilters = new ArrayList<>(SelectionSpec.getInstance().preFilters);

        // Snapshot cursor data on main thread to avoid threading issues
        final List<Object[]> rows = new ArrayList<>();
        if (cursor != null && cursor.moveToFirst()) {
            int idCol = cursor.getColumnIndex(MediaStore.Files.FileColumns._ID);
            int nameCol = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME);
            int mimeCol = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE);
            int sizeCol = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE);
            int durationCol = cursor.getColumnIndex("duration");
            do {
                rows.add(new Object[]{
                        cursor.getLong(idCol),
                        cursor.getString(nameCol),
                        cursor.getString(mimeCol),
                        cursor.getLong(sizeCol),
                        cursor.getLong(durationCol)
                });
            } while (cursor.moveToNext());
        }

        new Thread(() -> {
            MatrixCursor filtered = new MatrixCursor(MEDIA_COLUMNS);

            for (Object[] row : rows) {
                if (version != mPreFilterVersion) return;

                long id = (long) row[0];
                // Always keep capture placeholder
                if (id == Item.ITEM_ID_CAPTURE) {
                    filtered.addRow(row);
                    continue;
                }

                // Build Item from row via single-row cursor
                MatrixCursor singleRow = new MatrixCursor(MEDIA_COLUMNS);
                singleRow.addRow(row);
                singleRow.moveToFirst();
                Item item = Item.valueOf(singleRow);
                singleRow.close();

                boolean accepted = true;
                for (PreFilter pf : preFilters) {
                    if (!pf.accept(context, item)) {
                        accepted = false;
                        break;
                    }
                }
                if (accepted) {
                    filtered.addRow(row);
                }
            }

            if (version != mPreFilterVersion) return;

            new Handler(Looper.getMainLooper()).post(() -> {
                if (version != mPreFilterVersion) return;
                if (getActivity() != null && !isDetached()) {
                    mAdapter.swapCursor(filtered);
                    mProgressBar.setVisibility(View.GONE);
                    mRecyclerView.setVisibility(View.VISIBLE);
                }
            });
        }).start();
    }

    @Override
    public void onUpdate() {
        // notify outer Activity that check state changed
        if (mCheckStateListener != null) {
            mCheckStateListener.onUpdate();
        }
    }

    @Override
    public void onMediaClick(Album album, Item item, int adapterPosition) {
        if (mOnMediaClickListener != null) {
            mOnMediaClickListener.onMediaClick((Album) getArguments().getParcelable(EXTRA_ALBUM),
                    item, adapterPosition);
        }
    }

    public interface SelectionProvider {
        SelectedItemCollection provideSelectedItemCollection();
    }
}
