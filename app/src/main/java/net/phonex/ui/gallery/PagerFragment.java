package net.phonex.ui.gallery;

import android.app.Fragment;
import android.os.Bundle;
import android.support.v4.view.ViewPager;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import net.phonex.R;
import net.phonex.ft.storage.FileStorageUri;
import net.phonex.util.Log;

import java.util.ArrayList;

/**
 * Created by Matus on 7/15/2015.
 */
public class PagerFragment extends Fragment {
    private static final String TAG = "PagerFragment";

    private ArrayList<FileStorageUri> uris;
    private int position;
    private Toolbar toolbar;

    public static PagerFragment newInstance(ArrayList<FileStorageUri> uris, int startingPosition) {
        if (uris == null) {
            throw new NullPointerException("uris");
        }
        PagerFragment fragment = new PagerFragment();
        fragment.uris = uris;
        fragment.position = startingPosition;
        return fragment;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelableArrayList(GalleryActivity.EXTRA_URIS, uris);
        outState.putInt(GalleryActivity.EXTRA_START_POSITION, position);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        if (uris == null) {
            if (savedInstanceState == null) {
                Log.e(TAG, "Resuming PagerFragment without saved instance state");
                getFragmentManager().popBackStack();
            } else {
                uris = savedInstanceState.getParcelableArrayList(GalleryActivity.EXTRA_URIS);
                position = savedInstanceState.getInt(GalleryActivity.EXTRA_START_POSITION);
            }
        }

        View rootView = inflater.inflate(R.layout.gallery_pager_fragment, container, false);

        final ViewPager pager = (ViewPager) rootView.findViewById(R.id.pager);
        // keep one page to the left and one page to the right
        pager.setOffscreenPageLimit(1);
        final ImageAdapter adapter = new ImageAdapter(uris, getFragmentManager());
        pager.setAdapter(adapter);
        pager.setCurrentItem(position);

        initToolbar(rootView);

        pager.setOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

            }

            @Override
            public void onPageSelected(int position) {
                PagerFragment.this.position = position;
                toolbar.setTitle(String.format("%d/%d %s", position + 1, uris.size(), uris.get(position).getFilename()));
            }

            @Override
            public void onPageScrollStateChanged(int state) {

            }
        });

        return rootView;
    }

    private void initToolbar(View view) {
        toolbar = (Toolbar) view.findViewById(R.id.my_toolbar);
        toolbar.setTitle(String.format("%d/%d %s", position + 1, uris.size(), uris.get(position).getFilename()));

        toolbar.setNavigationIcon(R.drawable.abc_ic_ab_back_mtrl_am_alpha);
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (getActivity() != null) getActivity().finish();
            }
        });
    }
}
