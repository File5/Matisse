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
package com.zhihu.matisse.sample;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.zhihu.matisse.Matisse;
import com.zhihu.matisse.MatissePermissions;
import com.zhihu.matisse.MimeType;
import com.zhihu.matisse.engine.impl.GlideEngine;
import com.zhihu.matisse.engine.impl.PicassoEngine;
import com.zhihu.matisse.filter.Filter;
import com.zhihu.matisse.internal.entity.CaptureStrategy;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SampleActivity extends AppCompatActivity implements View.OnClickListener {

    private ActivityResultLauncher<String[]> permissionsLauncher;

    private UriAdapter mAdapter;
    private Matisse matisse;
    private final ActivityResultLauncher<Intent> captureLauncher = registerForActivityResult(
        new ActivityResultContracts.StartActivityForResult(),
        new ActivityResultCallback<ActivityResult>() {
            @Override public void onActivityResult(ActivityResult result) {
                Intent data = result.getData();
                mAdapter.setData(Matisse.obtainResult(data), Matisse.obtainPathResult(data));
                Log.e("On capture  ", String.valueOf(Matisse.obtainOriginalState(data)));
            }
        });

    private final ActivityResultLauncher<Intent> pickerLauncher = registerForActivityResult(
        new ActivityResultContracts.StartActivityForResult(),
        new ActivityResultCallback<ActivityResult>() {
            @Override public void onActivityResult(ActivityResult result) {
                Intent data = result.getData();
                if (data == null) return;
                mAdapter.setData(Matisse.obtainResult(data), Matisse.obtainPathResult(data));
                Log.e("OnActivityResult ", String.valueOf(Matisse.obtainOriginalState(data)));
            }
        });
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        findViewById(R.id.zhihu).setOnClickListener(this);
        findViewById(R.id.dracula).setOnClickListener(this);
        findViewById(R.id.only_gif).setOnClickListener(this);
        findViewById(R.id.capture).setOnClickListener(this);

        RecyclerView recyclerView = (RecyclerView) findViewById(R.id.recyclerview);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(mAdapter = new UriAdapter());

        permissionsLauncher = MatissePermissions.register(this, (state, deniedPermissions) -> {
            switch (state) {
                case GRANTED:
                    Log.d("Permissions", "All permissions granted");
                    break;
                case PARTIAL:
                    Log.d("Permissions", "Partial photo access granted");
                    Toast.makeText(this,
                            "Limited photo access granted",
                            Toast.LENGTH_SHORT).show();
                    break;
                case DENIED:
                    Toast.makeText(this,
                            "Some permissions denied: " + deniedPermissions,
                            Toast.LENGTH_LONG).show();
                    break;
                case PERMANENTLY_DENIED:
                    Toast.makeText(this,
                            "Permissions permanently denied. Please enable in Settings.",
                            Toast.LENGTH_LONG).show();
                    MatissePermissions.openAppSettings(this);
                    break;
            }
        });

        matisse = Matisse.from(SampleActivity.this);

        if (!MatissePermissions.isGranted(this)) {
            MatissePermissions.request(permissionsLauncher);
        }
    }

    // <editor-fold defaultstate="collapsed" desc="onClick">
    @SuppressLint("CheckResult")
    @Override
    public void onClick(final View v) {
        startAction(v);
    }

    private void startAction(View v) {
        int id = v.getId();

        if (id == R.id.capture) {
            matisse.performCapture(new CaptureStrategy(true, "com.zhihu.matisse.sample.fileprovider", "test"), captureLauncher);
        } else if (id == R.id.zhihu) {
            Matisse.from(SampleActivity.this)
                    .choose(MimeType.ofImage(), false)
                    .showSingleMediaType(true)
                    .countable(true)
                    .capture(true)
                    .captureStrategy(new CaptureStrategy(true, "com.zhihu.matisse.sample.fileprovider", "test"))
                    .maxSelectable(9)
                    .addFilter(new GifSizeFilter(320, 320, 5 * Filter.K * Filter.K))
                    .gridExpectedSize(getResources().getDimensionPixelSize(R.dimen.grid_expected_size))
                    .restrictOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
                    .thumbnailScale(0.85f)
                    .imageEngine(new GlideEngine())
                    .setOnSelectedListener((uriList, pathList) -> {
                        Log.e("onSelected", "onSelected: pathList=" + pathList);
                    })
                    .showSingleMediaType(true)
                    .originalEnable(true)
                    .maxOriginalSize(10)
                    .autoHideToolbarOnSingleTap(true)
                    .setOnCheckedListener(isChecked -> {
                        Log.e("isChecked", "onCheck: isChecked=" + isChecked);
                    })
                    .forResult(pickerLauncher);
        } else if (id == R.id.dracula) {
            Matisse.from(SampleActivity.this)
                    .choose(MimeType.ofImage())
                    .theme(com.zhihu.matisse.R.style.Matisse_Dracula)
                    .countable(false)
                    .showPreview(false)
                    .addPreFilter(new GpsPreFilter(true))
                    //.addFilter(new GifSizeFilter(320, 320, 5 * Filter.K * Filter.K))
                    .maxSelectable(9)
                    //.originalEnable(true)
                    //.maxOriginalSize(10)
                    .imageEngine(new PicassoEngine())
                    .forResult(pickerLauncher);
        } else if (id == R.id.only_gif) {
            Matisse.from(SampleActivity.this)
                    .choose(MimeType.of(MimeType.GIF), false)
                    .countable(true)
                    .maxSelectable(9)
                    .addFilter(new GifSizeFilter(320, 320, 5 * Filter.K * Filter.K))
                    .gridExpectedSize(getResources().getDimensionPixelSize(R.dimen.grid_expected_size))
                    .restrictOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
                    .thumbnailScale(0.85f)
                    .imageEngine(new GlideEngine())
                    .showSingleMediaType(true)
                    .originalEnable(true)
                    .maxOriginalSize(10)
                    .autoHideToolbarOnSingleTap(true)
                    .forResult(pickerLauncher);
        } else {
            // Default case
        }

        mAdapter.setData(null, null);
    }

    private static class UriAdapter extends RecyclerView.Adapter<UriAdapter.UriViewHolder> {

        private List<Uri> mUris;
        private List<String> mPaths;

        void setData(List<Uri> uris, List<String> paths) {
            mUris = uris;
            mPaths = paths;
            notifyDataSetChanged();
        }

        @Override
        public UriViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new UriViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.uri_item, parent, false));
        }

        @Override
        public void onBindViewHolder(UriViewHolder holder, int position) {
            holder.mUri.setText(mUris.get(position).toString());
            holder.mPath.setText(mPaths.get(position));

            holder.mUri.setAlpha(position % 2 == 0 ? 1.0f : 0.54f);
            holder.mPath.setAlpha(position % 2 == 0 ? 1.0f : 0.54f);

            float[] latLong = Matisse.obtainGpsLocation(
                    holder.itemView.getContext(), mUris.get(position));
            if (latLong != null) {
                holder.mLocation.setVisibility(View.VISIBLE);
                holder.mLocation.setText(String.format(Locale.US,
                        "GPS: %.6f, %.6f", latLong[0], latLong[1]));
            } else {
                holder.mLocation.setVisibility(View.GONE);
            }
        }

        @Override
        public int getItemCount() {
            return mUris == null ? 0 : mUris.size();
        }

        static class UriViewHolder extends RecyclerView.ViewHolder {

            private TextView mUri;
            private TextView mPath;
            private TextView mLocation;

            UriViewHolder(View contentView) {
                super(contentView);
                mUri = (TextView) contentView.findViewById(R.id.uri);
                mPath = (TextView) contentView.findViewById(R.id.path);
                mLocation = (TextView) contentView.findViewById(R.id.location);
            }
        }
    }
}
