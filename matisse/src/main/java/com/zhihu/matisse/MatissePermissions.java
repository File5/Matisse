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
 *   <tr><td>33</td><td>READ_MEDIA_IMAGES, READ_MEDIA_VIDEO, ACCESS_MEDIA_LOCATION</td></tr>
 *   <tr><td>34+</td><td>above + READ_MEDIA_VISUAL_USER_SELECTED (partial access)</td></tr>
 * </table>
 *
 * <h3>Usage</h3>
 * <pre>
 * // 1. Register in onCreate (before onStart)
 * launcher = MatissePermissions.register(this, (state, denied) -&gt; {
 *     switch (state) {
 *         case GRANTED:
 *             // Proceed with full access
 *             break;
 *         case PARTIAL:
 *             // API 34+: user shared a subset of photos. The picker will
 *             // only see those items. Optionally show a "Manage selection"
 *             // button that calls MatissePermissions.requestReselection(launcher).
 *             break;
 *         case DENIED:
 *             // Show rationale, re-request
 *             break;
 *         case PERMANENTLY_DENIED:
 *             // Direct user to app Settings
 *             MatissePermissions.openAppSettings(this);
 *             break;
 *     }
 * });
 *
 * // 2. Check &amp; request
 * if (!MatissePermissions.isGranted(this)) {
 *     MatissePermissions.request(launcher);
 * }
 *
 * // 3. (API 34+ only) Let the user add or remove photos after partial grant
 * if (MatissePermissions.hasPartialAccess(this)) {
 *     // Show a "Select more photos" UI element. When tapped:
 *     MatissePermissions.requestReselection(launcher);
 * }
 * </pre>
 *
 * <h3>Notes on {@code ACCESS_MEDIA_LOCATION}</h3>
 * Whether unredacted EXIF GPS metadata is available depends on the user's
 * grant of {@code ACCESS_MEDIA_LOCATION}. Its outcome is reported via the
 * {@code deniedPermissions} list and does not gate {@link State#GRANTED}: if
 * the user grants media access but denies location, the state is still
 * {@code GRANTED} and callers may show a degraded UX for items without GPS.
 */
public final class MatissePermissions {

    /**
     * Result state after a permission request.
     */
    public enum State {
        /** All required media permissions are granted. */
        GRANTED,
        /**
         * API 34+ only: user granted access to a user-selected subset of photos
         * via {@code READ_MEDIA_VISUAL_USER_SELECTED}. The picker can run, but
         * will only see the items the user explicitly shared. To let the user
         * add/remove items later, call {@link #requestReselection}.
         */
        PARTIAL,
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
         * @param deniedPermissions List of permissions that were not granted.
         *                          May be non-empty for {@link State#GRANTED}
         *                          (e.g., {@code ACCESS_MEDIA_LOCATION} denied
         *                          while media perms granted).
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

        if (Build.VERSION.SDK_INT >= 34) {
            // Android 14+: granular media + partial-access permission
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES);
            permissions.add(Manifest.permission.READ_MEDIA_VIDEO);
            permissions.add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED);
        } else if (Build.VERSION.SDK_INT >= 33) {
            // Android 13: granular media permissions
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
     * Whether the picker can run with the current permission grants. Returns
     * {@code true} for both full and partial access. Always {@code true} on
     * API &lt; 23.
     *
     * <p>Use {@link #hasFullAccess} to check strictly for full media access,
     * or {@link #hasPartialAccess} to detect the API 34+ partial-access state.
     */
    public static boolean isGranted(@NonNull Context context) {
        if (Build.VERSION.SDK_INT < 23) {
            return true;
        }
        if (hasFullAccess(context)) {
            return true;
        }
        return Build.VERSION.SDK_INT >= 34 && hasPartialAccess(context);
    }

    /**
     * Whether the app has full (non-partial) media access. On API 34+ this is
     * {@code true} only when both {@code READ_MEDIA_IMAGES} and
     * {@code READ_MEDIA_VIDEO} are granted.
     */
    public static boolean hasFullAccess(@NonNull Context context) {
        if (Build.VERSION.SDK_INT < 23) {
            return true;
        }
        if (Build.VERSION.SDK_INT >= 33) {
            return isPermissionGranted(context, Manifest.permission.READ_MEDIA_IMAGES)
                    && isPermissionGranted(context, Manifest.permission.READ_MEDIA_VIDEO);
        }
        return isPermissionGranted(context, Manifest.permission.READ_EXTERNAL_STORAGE);
    }

    /**
     * Whether the app has partial media access (API 34+ only). Returns
     * {@code true} when {@code READ_MEDIA_VISUAL_USER_SELECTED} is granted but
     * the full media permissions are not. Use this to decide whether to show a
     * "Manage photo selection" UI element in the host app, per the
     * <a href="https://developer.android.com/about/versions/14/changes/partial-photo-video-access">
     * Android 14 partial access guidelines</a>.
     */
    public static boolean hasPartialAccess(@NonNull Context context) {
        if (Build.VERSION.SDK_INT < 34) {
            return false;
        }
        boolean userSelected = isPermissionGranted(
                context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED);
        return userSelected && !hasFullAccess(context);
    }

    /**
     * Returns {@code true} if a rationale should be shown for at least one
     * required permission. Useful for deciding whether to show an explanation
     * dialog before calling {@link #request}.
     *
     * <p>On API 34+ with partial access already granted, the granular media
     * permissions are excluded from the check: the system returns {@code false}
     * for them in that state, which would otherwise mislead callers.
     */
    public static boolean shouldShowRationale(@NonNull Activity activity) {
        if (Build.VERSION.SDK_INT < 23) {
            return false;
        }
        boolean partial = hasPartialAccess(activity);
        for (String permission : getRequired()) {
            if (partial && isMediaAccessPermission(permission)) {
                continue;
            }
            if (!isPermissionGranted(activity, permission)
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
     * Re-launch the permission request to let the user manage their photo
     * selection. Equivalent to {@link #request} but named for the API 34+
     * partial-access reselection flow: when the app already has partial access,
     * relaunching {@code RequestMultiplePermissions} with the full perm set
     * (including {@code READ_MEDIA_VISUAL_USER_SELECTED}) shows the photo
     * reselection bottom-sheet rather than the upgrade dialog.
     *
     * <p>Per Android guidance, the host app should expose a clear UI element
     * that triggers this flow on user action — the system does not re-prompt
     * automatically.
     */
    public static void requestReselection(@NonNull ActivityResultLauncher<String[]> launcher) {
        request(launcher);
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

    private static boolean isPermissionGranted(@NonNull Context context, @NonNull String permission) {
        return ContextCompat.checkSelfPermission(context, permission)
                == PackageManager.PERMISSION_GRANTED;
    }

    private static boolean isMediaAccessPermission(@NonNull String permission) {
        return Manifest.permission.READ_MEDIA_IMAGES.equals(permission)
                || Manifest.permission.READ_MEDIA_VIDEO.equals(permission)
                || Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED.equals(permission);
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

        // API 34+: partial access — VISUAL_USER_SELECTED granted, full media perms not.
        if (Build.VERSION.SDK_INT >= 34
                && Boolean.TRUE.equals(result.get(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED))) {
            boolean fullImages = Boolean.TRUE.equals(result.get(Manifest.permission.READ_MEDIA_IMAGES));
            boolean fullVideo = Boolean.TRUE.equals(result.get(Manifest.permission.READ_MEDIA_VIDEO));
            if (!(fullImages && fullVideo)) {
                // IMAGES/VIDEO being false reflects the user's partial choice, not a refusal.
                // Only surface other denials (e.g. ACCESS_MEDIA_LOCATION) to the caller.
                List<String> partialDenied = new ArrayList<>();
                for (String permission : denied) {
                    if (!Manifest.permission.READ_MEDIA_IMAGES.equals(permission)
                            && !Manifest.permission.READ_MEDIA_VIDEO.equals(permission)) {
                        partialDenied.add(permission);
                    }
                }
                callback.onResult(State.PARTIAL, partialDenied);
                return;
            }
        }

        // ACCESS_MEDIA_LOCATION is best-effort: if media access is otherwise full,
        // treat AML denial as GRANTED with AML reported in `denied`.
        if (hasFullAccess(activity) && deniedOnlyContainsAccessMediaLocation(denied)) {
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

    private static boolean deniedOnlyContainsAccessMediaLocation(@NonNull List<String> denied) {
        if (denied.isEmpty()) {
            return false;
        }
        for (String permission : denied) {
            if (!Manifest.permission.ACCESS_MEDIA_LOCATION.equals(permission)) {
                return false;
            }
        }
        return true;
    }
}
