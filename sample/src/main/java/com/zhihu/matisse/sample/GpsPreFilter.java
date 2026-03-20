package com.zhihu.matisse.sample;

import android.content.Context;

import com.zhihu.matisse.filter.PreFilter;
import com.zhihu.matisse.internal.entity.Item;
import com.zhihu.matisse.internal.utils.PhotoMetadataUtils;

/**
 * Pre-filter that shows or hides images based on GPS EXIF data presence.
 * <p>
 * When {@code requireGps} is true, only geotagged images are shown.
 * When false, only images without GPS data are shown.
 * Videos are always shown when requireGps is false, hidden when true.
 */
public class GpsPreFilter extends PreFilter {

    private final boolean mRequireGps;

    /**
     * @param requireGps true to show only geotagged images, false to show only non-geotagged.
     */
    public GpsPreFilter(boolean requireGps) {
        mRequireGps = requireGps;
    }

    @Override
    public boolean accept(Context context, Item item) {
        if (!item.isImage()) {
            return !mRequireGps;
        }
        float[] latLong = PhotoMetadataUtils.getGpsLatLong(context, item.getContentUri());
        boolean hasGps = latLong != null;
        return mRequireGps == hasGps;
    }
}
