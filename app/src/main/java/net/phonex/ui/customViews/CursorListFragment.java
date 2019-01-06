package net.phonex.ui.customViews;

import android.app.ListFragment;
import android.app.LoaderManager;
import android.content.Loader;
import android.database.Cursor;
import android.os.Bundle;
import android.view.View;
import android.view.animation.AnimationUtils;

import net.phonex.R;
import net.phonex.ui.chat.compounds.MyCursor;
import net.phonex.util.Log;

/**
 * Helper class for list fragments.<br/>
 * This takes in charge of cursor callbacks by forwarding to {@link #changeCursor(Loader, Cursor)}.<br/>
 * It also takes in charge to retrieve and update progress indicator. Custom views for this must contain:
 */
public abstract class CursorListFragment extends ListFragment implements LoaderManager.LoaderCallbacks<Cursor> {
    private View mListContainer = null;
    private View mProgressContainer = null;
    private boolean mListShown = false;

    @Override
    public void setListShown(boolean shown) {
        setListShown(shown, true);
    }
    
    @Override
    public void setListShownNoAnimation(boolean shown) {
        setListShown(shown, false);
    }
    
    private void setListShown(boolean shown, boolean animate) {
        loadCustomView();
        if(mListShown == shown) {
            return;
        }

        mListShown = shown;
        if (mListContainer == null || mProgressContainer == null) {
            return;
        }

        if(shown) {
            if(animate) {
                mListContainer.startAnimation(AnimationUtils.loadAnimation(getActivity(), android.R.anim.fade_in));
            }

            mListContainer.setVisibility(View.VISIBLE);
            mProgressContainer.setVisibility(View.GONE);
        } else {
            if(animate) {
                mListContainer.startAnimation(AnimationUtils.loadAnimation(getActivity(), android.R.anim.fade_out));
            }

            mListContainer.setVisibility(View.GONE);
            mProgressContainer.setVisibility(View.VISIBLE);
        }
    }
    
    /**
     * Make sure our private reference to views are correct. 
     */
    private void loadCustomView() {
        if(mListContainer != null) {
            return;
        }
        mListContainer = getView().findViewById(R.id.listContainer);
        mProgressContainer = getView().findViewById(R.id.progressContainer);
    }

    public abstract Loader<Cursor> onCreateLoader(int loader, Bundle args);

    /**
     * {@inheritDoc}
     */
    @Override
    public void onLoadFinished(final Loader<Cursor> loader, final Cursor data) {

        if (data == null){
            changeCursor(loader, data);
        } else {
            changeCursor(loader, new MyCursor(data));
        }

        if(isResumed()) {
            setListShown(true);
        }else {
            setListShownNoAnimation(true);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        changeCursor(loader, null);
    }
    
    /**
     * Request a cursor change to the adapter. <br/>
     * To be implemented by extenders.
     * @param c the new cursor to replace the old one
     */
    public abstract void changeCursor(Loader<Cursor> loader, Cursor c);

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        // When we will recycle this view, the stored shown and list containers becomes invalid
        mListShown = false;
        mListContainer = null;
        super.onActivityCreated(savedInstanceState);
    }
}
