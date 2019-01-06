package net.phonex.util.glide;

import com.bumptech.glide.Priority;
import com.bumptech.glide.load.data.DataFetcher;

import net.phonex.util.Log;

import java.io.IOException;
import java.io.InputStream;

/**
 * Created by Matus on 10.6.2015.
 */
public class PassingDataFetcher implements DataFetcher<InputStream> {

    private static final String TAG = "PassingDataFetcher";

    private InputStream model;

    public PassingDataFetcher(InputStream model) {
        this.model = model;
    }

    @Override
    public InputStream loadData(Priority priority) throws Exception {
        return model;
    }

    @Override
    public void cleanup() {
        try {
            model.close();
        } catch (IOException e) {
            Log.e(TAG, "Failed to close input stream in cleanup", e);
        }
    }

    @Override
    public String getId() {
        // TODO uri would be suitable
        // instead of InputStream pass some other structure that includes it
        // this way it will never be cached, because InputStream is not reused
        return "" + model.hashCode();
    }

    @Override
    public void cancel() {

    }
}
