package net.phonex.util.glide;

import android.content.Context;

import com.bumptech.glide.Glide;
import com.bumptech.glide.GlideBuilder;
import com.bumptech.glide.module.GlideModule;

import net.phonex.ft.storage.FileStorageUri;
import net.phonex.util.Log;

import java.io.InputStream;

/**
 * Created by Matus on 10.6.2015.
 */
public class FileStorageUriStreamGlideModule implements GlideModule {
    @Override
    public void applyOptions(Context context, GlideBuilder builder) {

    }

    @Override
    public void registerComponents(Context context, Glide glide) {
        Log.d("FileStorageUriStreamGlideModule", "registerComponents");
        glide.register(FileStorageUri.class, InputStream.class, new FileStorageUriStreamModelLoader.Factory());
    }
}
