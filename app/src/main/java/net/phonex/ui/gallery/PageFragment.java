package net.phonex.ui.gallery;

import android.app.Fragment;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.drawable.GlideDrawable;
import com.bumptech.glide.request.animation.GlideAnimation;
import com.bumptech.glide.request.target.GlideDrawableImageViewTarget;
import com.bumptech.glide.request.target.Target;

import net.phonex.R;
import net.phonex.ft.storage.FileStorageUri;
import net.phonex.util.Log;

import it.sephiroth.android.library.imagezoom.ImageViewTouch;
import it.sephiroth.android.library.imagezoom.ImageViewTouchBase;

/**
 * Created by Matus on 23-Jul-15.
 */
public class PageFragment extends Fragment {

    private static final String TAG = "PageFragment";

    private FileStorageUri uri;

    private RetryTarget previewTarget;
    private RetryTarget fullTarget;
    private ImageViewTouch previewImageView;
    private ImageViewTouch fullImageView;

    private Handler hidePhotoPreviewHandler;
    private ProgressBar loadingProgressBar;

    public static PageFragment newInstance(FileStorageUri uri) {
        PageFragment fragment = new PageFragment();
        fragment.uri = uri;
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            uri = savedInstanceState.getParcelable("uri");
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable("uri", uri);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View imageLayout = inflater.inflate(R.layout.gallery_pager_image, container, false);

        previewImageView = (ImageViewTouch) imageLayout.findViewById(R.id.preview_image);
        previewImageView.setDisplayType(ImageViewTouchBase.DisplayType.FIT_TO_SCREEN);

        fullImageView = (ImageViewTouch) imageLayout.findViewById(R.id.full_image);
        fullImageView.setDisplayType(ImageViewTouchBase.DisplayType.FIT_TO_SCREEN);

        loadingProgressBar = (ProgressBar) imageLayout.findViewById(R.id.loading_progress);

        return imageLayout;
    }

    @Override
    public void onResume() {
        Log.df(TAG, "onResume %s", uri.toString());
        if (previewImageView == null && getView() != null) {
            previewImageView = (ImageViewTouch) getView().findViewById(R.id.preview_image);
            previewImageView.setDisplayType(ImageViewTouchBase.DisplayType.FIT_TO_SCREEN);
        }
        if (fullImageView == null && getView() != null) {
            fullImageView = (ImageViewTouch) getView().findViewById(R.id.full_image);
            fullImageView.setDisplayType(ImageViewTouchBase.DisplayType.FIT_TO_SCREEN);
        }
        if (loadingProgressBar == null && getView() != null) {
            loadingProgressBar = (ProgressBar) getView().findViewById(R.id.loading_progress);
        }

        super.onResume();

        loadPhoto(false);
    }

    @Override
    public void onPause() {
        Log.df(TAG, "onPause %s", uri.toString());

        // try to clear images if possible
        // this might not have any effect on memory used by glide though
        // it has bitmap pool and does not allow anyone else to swim in it

        previewImageView.clear();
        Glide.clear(previewTarget);
        previewImageView = null;
        previewTarget = null;

        fullImageView.clear();
        Glide.clear(fullImageView);
        fullImageView = null;
        fullTarget = null;

        super.onPause();
    }

    private class RetryTarget extends GlideDrawableImageViewTarget {

        private boolean highRes;

        public RetryTarget(ImageView view) {
            super(view);
        }

        public void setHighRes(boolean highRes) {
            this.highRes = highRes;
        }

        @Override
        public void onResourceReady(GlideDrawable resource, GlideAnimation<? super GlideDrawable> animation) {
            super.onResourceReady(resource, animation);

            if (highRes) {
                // there is already the full image
                Log.d(TAG, "Loaded high resolution preview");
                // hide the preview image, it is ugly, if we zoom out the full image
                previewImageView.setVisibility(View.INVISIBLE);
            } else {
                // if this was just the preview image, start loading the full image
                Log.d(TAG, "Loaded low resolution preview");

                // show loading progress bar
                loadingProgressBar.setVisibility(View.VISIBLE);

                // if the load succeeds, the progress will be hidden
                // if it fails, we do not know. if it does not load in 5 sec, it won't, just hide it
                if (hidePhotoPreviewHandler == null) {
                    hidePhotoPreviewHandler = new Handler(msg -> {
                        loadingProgressBar.setVisibility(View.INVISIBLE);
                        return true;
                    });
                }
                hidePhotoPreviewHandler.sendEmptyMessageDelayed(0, 5000);
                loadPhoto(true);
            }
        }
    }

    /**
     * Load image that has just been taken.
     *
     * First low resolution image is loaded, because we might have memory constraints.
     * If load is successful, try to load high res image.
     *
     * Low res image is not zoomable (covered by high res).
     * There is a progressbar inbetween.
     *
     * @param highRes false if preview is loaded and then high res image is loaded,
     *                true if only high res is loaded
     */
    private void loadPhoto(boolean highRes) {
        if (!isAdded()) {
            return;
        }

        RetryTarget target;

        if (highRes) {
            if (fullTarget == null) {
                fullTarget = new RetryTarget(fullImageView);
            }
            target = fullTarget;
        } else {
            if (previewTarget == null) {
                previewTarget = new RetryTarget(previewImageView);
            }
            target = previewTarget;
        }

        target.setHighRes(highRes);

        int width;
        int height;

        if (highRes) {
            width = fullImageView.getWidth();
            height = fullImageView.getHeight();
            if (width <= 0 || height <= 0) {
                return;
            }
        } else {
            width = 300;
            height = 300;
        }

        Log.df(TAG, "Loading image in resolution %d x %d", width, height);

        Glide.with(getActivity())
                .load(uri)
                .override(width, height)
                        //.placeholder(R.drawable.ic_photo_black_48px) // no placeholder, it will cover image!
                .error(R.drawable.ic_broken_image_black_48px) // if preview fails, full will not start
                // we could cache SOURCE, because we load the same image twice
                // however that might break security
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .crossFade() // animate
                .into(target);
    }
}
