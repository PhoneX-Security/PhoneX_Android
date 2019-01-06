package net.phonex.util.glide;

import android.content.Context;

import com.bumptech.glide.load.data.DataFetcher;
import com.bumptech.glide.load.model.GenericLoaderFactory;
import com.bumptech.glide.load.model.ModelLoader;
import com.bumptech.glide.load.model.ModelLoaderFactory;

import net.phonex.ft.storage.FileStorageUri;

import java.io.InputStream;

/**
 * Created by Matus on 10.6.2015.
 */
public class FileStorageUriStreamModelLoader implements ModelLoader<FileStorageUri, InputStream> {

    Context context;

    public FileStorageUriStreamModelLoader(Context context) {
        this.context = context;
    }

    @Override
    public DataFetcher<InputStream> getResourceFetcher(FileStorageUri model, int width, int height) {
        return new FileStorageUriStreamDataFetcher(model, context);
    }

    public static class Factory implements ModelLoaderFactory<FileStorageUri, InputStream> {

        @Override
        public ModelLoader<FileStorageUri, InputStream> build(Context context, GenericLoaderFactory factories) {
            return new FileStorageUriStreamModelLoader(context);
        }

        @Override
        public void teardown() {
            // Do nothing.
        }
    }
}
