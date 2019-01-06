package net.phonex.util.glide;

import android.content.Context;

import com.bumptech.glide.Glide;
import com.bumptech.glide.GlideBuilder;
import com.bumptech.glide.module.GlideModule;

import java.io.InputStream;

/**
 * Glide module that just passes InputStream.
 * It should use some identifier, otherwise caching does not work!
 * Use uris instead.
 *
 * Created by Matus on 10.6.2015.
 */
public class PassingGlideModule implements GlideModule {

    @Override
    public void applyOptions(Context context, GlideBuilder builder) {
    }

    @Override
    public void registerComponents(Context context, Glide glide) {
        glide.register(InputStream.class, InputStream.class, new PassingStreamModelLoader.Factory());
    }
}
