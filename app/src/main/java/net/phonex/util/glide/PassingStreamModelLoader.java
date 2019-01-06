package net.phonex.util.glide;

import android.content.Context;

import com.bumptech.glide.load.data.DataFetcher;
import com.bumptech.glide.load.model.GenericLoaderFactory;
import com.bumptech.glide.load.model.ModelLoaderFactory;
import com.bumptech.glide.load.model.stream.StreamModelLoader;

import java.io.InputStream;

/**
 * Created by Matus on 10.6.2015.
 */

public class PassingStreamModelLoader implements StreamModelLoader<InputStream> {
    @Override
    public DataFetcher<InputStream> getResourceFetcher(InputStream model, int width, int height) {
        return new PassingDataFetcher(model);
    }

    public static class Factory implements ModelLoaderFactory<InputStream, InputStream> {

        @Override
        public StreamModelLoader<InputStream> build(Context context, GenericLoaderFactory factories) {
            return new PassingStreamModelLoader();
        }

        @Override
        public void teardown() {
            // Do nothing.
        }
    }
}
