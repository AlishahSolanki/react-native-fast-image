package com.dylanvann.fastimage;

import android.app.Activity;

import android.support.annotation.Nullable;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.views.imagehelper.ImageSource;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;

class FastImageViewModule extends ReactContextBaseJavaModule {

    private static final String REACT_CLASS = "FastImageView";
    private static final String ERROR_LOAD_FAILED = "ERROR_LOAD_FAILED";

    FastImageViewModule(ReactApplicationContext reactContext) {
        super(reactContext);
    }

    @Override
    public String getName() {
        return REACT_CLASS;
    }

    @ReactMethod
    public void preload(final ReadableArray sources) {
        final Activity activity = getCurrentActivity();
        if (activity == null) return;
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < sources.size(); i++) {
                    final ReadableMap source = sources.getMap(i);

                    final FastImageSource imageSource = FastImageViewConverter.getImageSource(activity, source);

                    Glide
                            .with(activity.getApplicationContext())
                            // This will make this work for remote and local images. e.g.
                            //    - file:///
                            //    - content://
                            //    - res:/
                            //    - android.resource://
                            //    - data:image/png;base64
                            .load(
                                    imageSource.isBase64Resource() ? imageSource.getSource() :
                                            imageSource.isResource() ? imageSource.getUri() : imageSource.getGlideUrl()
                            )

                            .apply(FastImageViewConverter.getOptions(source))
                            .preload();
                }
            }
        });
    }

    @ReactMethod
    public void loadImage(final ReadableMap source, final Promise promise) {
        final Activity activity = getCurrentActivity();
        if (activity == null) return;
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                final FastImageSource imageSource = FastImageViewConverter.getImageSource(activity, source);

                final GlideUrl glideUrl = imageSource.getGlideUrl();

                Glide
                        .with(activity.getApplicationContext())
                        .asFile()
                        .load(glideUrl)
                        .apply(FastImageViewConverter.getOptions(source))
                        .listener(new RequestListener<File>() {
                            @Override
                            public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<File> target, boolean isFirstResource) {
                                promise.reject(ERROR_LOAD_FAILED, e);
                                return false;
                            }

                            @Override
                            public boolean onResourceReady(File resource, Object model, Target<File> target, DataSource dataSource, boolean isFirstResource) {
                                copyFileOrDirectory(resource,resource.getPath(), promise);
                                //promise.resolve(resource.getAbsolutePath());
                                return false;
                            }
                        })
                        .submit();
            }
        });
    }
    public static void copyFileOrDirectory(File srcDir, String dstDir,Promise promise ) {

        try {
            String[] dir = dstDir.split("/");


            File src = srcDir;
            File dst = new File(dstDir.replace(dir[dir.length - 2] + "/" + dir[dir.length - 1], "share.png"));

            if (src.isDirectory()) {

                String files[] = src.list();
                int filesLength = files.length;
                for (int i = 0; i < filesLength; i++) {
                    File src1 = (new File(src, files[i]));
                    String dst1 = dst.getPath();
                    copyFileOrDirectory(src1, dst1, promise);

                }
            } else {
                copyFile(src, dst, promise);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void copyFile(File sourceFile, File destFile, Promise promise) throws IOException {
        if (!destFile.getParentFile().exists())
            destFile.getParentFile().mkdirs();

        if (!destFile.exists()) {
            destFile.createNewFile();
        }

        FileChannel source = null;
        FileChannel destination = null;

        try {
            source = new FileInputStream(sourceFile).getChannel();
            destination = new FileOutputStream(destFile).getChannel();
            destination.transferFrom(source, 0, source.size());
        } finally {
            if (source != null) {
                source.close();
            }
            if (destination != null) {
                destination.close();
                promise.resolve(destFile.getAbsolutePath());
            }
        }
    }
}
