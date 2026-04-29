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
package com.zhihu.matisse;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Helper for requesting all permissions needed by Matisse, handling differences
 * across Android API levels 21 through 35+.
 *
 * <h3>Permission matrix by API level</h3>
 * <table>
 *   <tr><th>API</th><th>Permissions</th></tr>
 *   <tr><td>21-22</td><td>None (install-time grants)</td></tr>
 *   <tr><td>23-28</td><td>READ_EXTERNAL_STORAGE, WRITE_EXTERNAL_STORAGE</td></tr>
 *   <tr><td>29-32</td><td>READ_EXTERNAL_STORAGE, ACCESS_MEDIA_LOCATION</td></tr>
 *   <tr><td>33+</td><td>READ_MEDIA_IMAGES, READ_MEDIA_VIDEO, ACCESS_MEDIA_LOCATION</td></tr>
 * </table>
 *
 * <h3>Usage</h3>
 * <pre>
 * // 1. Register in onCreate (before onStart)
 * launcher = MatissePermissions.register(this, (state, denied) -&gt; {
 *     switch (state) {
 *         case GRANTED:
 *             // proceed
 *             break;
 *         case DENIED:
 *             // show rationale, re-request
 *             break;
 *         case PERMANENTLY_DENIED:
 *             // show dialog pointing to Settings
 *             MatissePermissions.openAppSettings(this);
 *             break;
 *     }
 * });
 *
 * // 2. Check &amp; request
 * if (!MatissePermissions.isGranted(this)) {
 *     MatissePermissions.request(launcher);
 * }
 * </pre>
 */
public final class MatissePermissions {

    /**
     * Result state after a permission request.
     */
    public enum State {
        /** All required permissions are granted. */
        GRANTED,
        /** One or more permissions were denied. The user can be asked again. */
        DENIED,
        /**
         * One or more permissions were denied with "Don't ask again".
         * The user must grant them manually via app Settings.
         */
        PERMANENTLY_DENIED
    }

    /**
     * Callback for permission request results.
     */
    public interface Callback {
        /**
         * @param state             The overall result state.
         * @param deniedPermissions List of permissions that were not granted (empty if GRANTED).
         */
        void onResult(@NonNull State state, @NonNull List<String> deniedPermissions);
    }

    private MatissePermissions() {
    }

    /**
     * Returns the permissions Matisse needs on the current device's API level.
     * Only includes permissions that require runtime requests (API 23+).
     */
    @NonNull
    public static String[] getRequired() {
        List<String> permissions = new ArrayList<>();

        if (Build.VERSION.SDK_INT >= 33) {
            // Android 13+: granular media permissions replace READ_EXTERNAL_STORAGE
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES);
            permissions.add(Manifest.permission.READ_MEDIA_VIDEO);
        } else if (Build.VERSION.SDK_INT >= 23) {
            // Android 6.0 - 12L: broad read access
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        }
        // else API < 23: all permissions are install-time, nothing to request

        if (Build.VERSION.SDK_INT >= 23 && Build.VERSION.SDK_INT <= 28) {
            // Android 6.0 - 9: write access needed for captured photos
            // Android 10+ scoped storage makes this unnecessary
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }

        if (Build.VERSION.SDK_INT >= 29) {
            // Android 10+: required to read GPS EXIF from MediaStore URIs
            permissions.add(Manifest.permission.ACCESS_MEDIA_LOCATION);
        }

        return permissions.toArray(new String[0]);
    }

    /**
     * Checks whether all required permissions are currently granted.
     * Always returns {@code true} on API &lt; 23.
     */
    public static boolean isGranted(@NonNull Context context) {
        if (Build.VERSION.SDK_INT < 23) {
            return true;
        }
        for (String permission : getRequired()) {
            if (ContextCompat.checkSelfPermission(context, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns {@code true} if a rationale should be shown for at least one
     * required permission. Useful for deciding whether to show an explanation
     * dialog before calling {@link #request}.
     */
    public static boolean shouldShowRationale(@NonNull Activity activity) {
        if (Build.VERSION.SDK_INT < 23) {
            return false;
        }
        for (String permission : getRequired()) {
            if (ContextCompat.checkSelfPermission(activity, permission)
                    != PackageManager.PERMISSION_GRANTED
                    && activity.shouldShowRequestPermissionRationale(permission)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Register a permission-request launcher from an Activity.
     * Must be called during initialization (before {@code onStart}).
     *
     * @param activity The host Activity.
     * @param callback Called when the user responds to the permission dialog.
     * @return A launcher to pass to {@link #request}.
     */
    @NonNull
    public static ActivityResultLauncher<String[]> register(
            @NonNull ComponentActivity activity,
            @NonNull Callback callback) {
        return activity.registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> handleResult(activity, result, callback));
    }

    /**
     * Register a permission-request launcher from a Fragment.
     * Must be called during initialization (before {@code onStart}).
     *
     * @param fragment The host Fragment.
     * @param callback Called when the user responds to the permission dialog.
     * @return A launcher to pass to {@link #request}.
     */
    @NonNull
    public static ActivityResultLauncher<String[]> register(
            @NonNull Fragment fragment,
            @NonNull Callback callback) {
        return fragment.registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    Activity activity = fragment.getActivity();
                    if (activity != null) {
                        handleResult(activity, result, callback);
                    }
                });
    }

    /**
     * Launch the system permission dialog for all required permissions.
     * No-op on API &lt; 23 (permissions are install-time).
     *
     * @param launcher The launcher obtained from {@link #register}.
     */
    public static void request(@NonNull ActivityResultLauncher<String[]> launcher) {
        String[] required = getRequired();
        if (required.length > 0) {
            launcher.launch(required);
        }
    }

    /**
     * Open this app's system Settings page so the user can manually enable
     * permanently denied permissions.
     */
    public static void openAppSettings(@NonNull Context context) {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.fromParts("package", context.getPackageName(), null));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    private static void handleResult(
            @NonNull Activity activity,
            @NonNull Map<String, Boolean> result,
            @NonNull Callback callback) {
        List<String> denied = new ArrayList<>();
        for (Map.Entry<String, Boolean> entry : result.entrySet()) {
            if (!Boolean.TRUE.equals(entry.getValue())) {
                denied.add(entry.getKey());
            }
        }

        if (denied.isEmpty()) {
            callback.onResult(State.GRANTED, denied);
            return;
        }

        // Distinguish "denied" from "permanently denied" (Don't ask again).
        // After a request, shouldShowRequestPermissionRationale returns:
        //   true  -> denied, but can ask again
        //   false -> denied with "Don't ask again" checked
        boolean permanentlyDenied = false;
        if (Build.VERSION.SDK_INT >= 23) {
            for (String permission : denied) {
                if (!activity.shouldShowRequestPermissionRationale(permission)) {
                    permanentlyDenied = true;
                    break;
                }
            }
        }

        callback.onResult(
                permanentlyDenied ? State.PERMANENTLY_DENIED : State.DENIED,
                denied);
    }
}
