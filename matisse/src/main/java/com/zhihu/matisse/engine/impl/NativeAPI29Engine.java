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
package com.zhihu.matisse.engine.impl;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.CancellationSignal;
import android.util.Log;
import android.util.Size;
import android.widget.ImageView;
import androidx.annotation.RequiresApi;
import com.zhihu.matisse.engine.ImageEngine;

import java.io.IOException;

/**
 * {@link ImageEngine} implementation using Glide.
 */

@RequiresApi(api = Build.VERSION_CODES.Q)
public class NativeAPI29Engine implements ImageEngine {


    @Override
    public void loadThumbnail(Context context, int resize, Drawable placeholder, ImageView imageView, Uri uri) {
        imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        try {
            Bitmap bmp = context.getContentResolver().loadThumbnail(
                    uri,
                    new Size(resize, resize),
                    new CancellationSignal()
            );
            imageView.setImageBitmap(bmp);
        } catch (IOException e) {
            e.printStackTrace();
            imageView.setImageDrawable(placeholder);
        }
    }

    @Override
    public void loadGifThumbnail(Context context, int resize, Drawable placeholder, ImageView imageView,
                                 Uri uri) {
        imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        try {
            Bitmap bmp = context.getContentResolver().loadThumbnail(
                    uri,
                    new Size(resize, resize),
                    new CancellationSignal()
            );
            imageView.setImageBitmap(bmp);
        } catch (IOException e) {
            e.printStackTrace();
            imageView.setImageDrawable(placeholder);
        }
    }

    @Override
    public void loadImage(Context context, int resizeX, int resizeY, ImageView imageView, Uri uri) {
        Log.d("Pong", "onAlbumMediaLoad loadImage " + uri.toString());
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        try {
            Bitmap bmp = context.getContentResolver().loadThumbnail(
                    uri,
                    new Size(resizeX, resizeY),
                    new CancellationSignal()
            );
            imageView.setImageBitmap(bmp);
            Log.d("Pong", "onAlbumMediaLoad loadImage finished " + uri.toString());
        } catch (IOException e) {
            e.printStackTrace();
            imageView.setImageDrawable(null);
            Log.d("Pong", "onAlbumMediaLoad loadImage failed " + uri.toString());
        }

    }

    @Override
    public void loadGifImage(Context context, int resizeX, int resizeY, ImageView imageView, Uri uri) {
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        try {
            Bitmap bmp = context.getContentResolver().loadThumbnail(
                    uri,
                    new Size(resizeX, resizeY),
                    new CancellationSignal()
            );
            imageView.setImageBitmap(bmp);
        } catch (IOException e) {
            e.printStackTrace();
            imageView.setImageDrawable(null);
        }
    }

    @Override
    public boolean supportAnimatedGif() {
        return true;
    }

}
