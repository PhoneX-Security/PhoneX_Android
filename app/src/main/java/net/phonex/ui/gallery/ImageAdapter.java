package net.phonex.ui.gallery;

import android.app.Fragment;
import android.app.FragmentManager;
import android.content.Context;
import android.support.v13.app.FragmentStatePagerAdapter;
import android.support.v4.view.PagerAdapter;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

import net.phonex.R;
import net.phonex.ft.storage.FileStorageUri;
import net.phonex.ui.slidingtab.SmartFragmentPagerAdapter;
import net.phonex.util.Log;

import java.util.List;

import it.sephiroth.android.library.imagezoom.ImageViewTouch;
import it.sephiroth.android.library.imagezoom.ImageViewTouchBase;

/**
 * Created by Matus on 7/15/2015.
 */
public class ImageAdapter extends FragmentStatePagerAdapter {

    private static final String TAG = "ImageAdapter";

    private final List<FileStorageUri> uris;

    public ImageAdapter(final List<FileStorageUri> uris, FragmentManager fm) {
        super(fm);
        if (uris == null) {
            throw new NullPointerException("uris");
        }
        this.uris = uris;
    }

    @Override
    public int getCount() {
        return uris.size();
    }

    @Override
    public Fragment getItem(int position) {
        return PageFragment.newInstance(uris.get(position));
    }
}
