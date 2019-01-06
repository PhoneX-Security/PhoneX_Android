package net.phonex.ui.chat;

import android.transition.Scene;
import android.view.View;
import android.widget.AbsListView;
import android.widget.ListView;

import net.phonex.util.LayoutUtils;
import net.phonex.util.Log;

/**
 * Scroll listener for MessageFragment.
 * Created by dusanklinec on 30.04.15.
 */
public class ChatScrollListener implements AbsListView.OnScrollListener {
    private static final String TAG = "ChatScrollListener";

    /**
     * Candidate scroll position. When scrolling stops, this value is passed to the listener.
     * During scrolling it is continuously updated.
     */
    private final ScrollPosition candPos = new ScrollPosition();

    /**
     * Last scroll state of the list. For change detection.
     */
    private int lastScrollState = 0;

    /**
     * Total scroll counter, computed from scroll deltas on event.
     */
    private long totalDelta = 0;
    private boolean totalDeltaValid = true;
    private boolean wasLastItemVisibleLastTime = false;

    /**
     * Main final listener for scroll events.
     */
    private final PixelScrollListener listener;

    private final Object desiredPositionLock = new Object();
    private boolean viewRecreated = false;
    private ScrollPosition desiredPosition;
    private int scrollPositionRetry = 0;
    private ListView mList;

    /**
     * Tracking info elements for computing scroll deltas.
     */
    private ListTrackedElement[] listTrackedElements = {
            new ListTrackedElement(0),  // top view, bottom Y
            new ListTrackedElement(1),  // mid view, bottom Y
            new ListTrackedElement(2),  // mid view, top Y
            new ListTrackedElement(3)}; // bottom view, top Y

    public interface PixelScrollListener {
        void onScroll(AbsListView view, float deltaY, long total);
        void onScrollChanged(ScrollPosition candPosition);
    }

    public ChatScrollListener(PixelScrollListener listener) {
        this.listener = listener;
    }

    /**
     * Resets total scroll counter + tracking positions (internal tracking state).
     */
    public void reset(){
        resetTotal(false);
    }

    /**
     * Resets total scroll counter + tracking positions (internal tracking state).
     * @param notify If true, onScroll() notification is sent to the listener.
     */
    public void resetTotal(boolean notify){
        final long prevTotalDelta = totalDelta;

        totalDelta = 0;
        totalDeltaValid = true;
        for (ListTrackedElement t : listTrackedElements) {
            t.reset();
        }

        if (notify && listener != null){
            listener.onScroll(null, 0-prevTotalDelta, totalDelta);
        }
    }

    public void onLoadFinished(int totalCount){
        synchronized (desiredPositionLock) {
            viewRecreated = false;
        }
    }

    public void onViewRecreated(){
        synchronized (desiredPositionLock) {
            viewRecreated = true;
            totalDeltaValid = false;
            reset();
        }
    }

    /**
     * Set desired position to be applied.
     * Stores position internally and takes care about its application.
     * Listens onScroll events and tries to apply given position until it succeeds or retry count limit
     * is reached or user overrides it manually.
     *
     * @param list
     * @param pos
     */
    public void applyScrollPosition(ListView list, ScrollPosition pos) {
        synchronized (desiredPositionLock) {
            // Applying null means reset current attempt.
            if (pos == null) {
                desiredPosition = null;
                scrollPositionRetry = 0;
                mList = null;
                Log.vf(TAG, "Apply position reset");
                return;
            }

            scrollPositionRetry = 0;
            mList = list;
            desiredPosition = new ScrollPosition();
            desiredPosition.copyFrom(pos);
            Log.vf(TAG, "Apply position set to: %s", desiredPosition);
        }
    }

    /**
     * Returns true if scroll position is near (in threshold) from the bottom.
     * @param thresholdInPix
     * @return
     */
    public boolean shouldScrollDown(long thresholdInPix){
        synchronized (desiredPositionLock) {
            final boolean shouldScroll = totalDeltaValid && totalDelta <= thresholdInPix;
            if (shouldScroll){
                Log.vf(TAG, "ScrollDown due to closeness, totalDelta: %s, threshold: %s", totalDelta, thresholdInPix);
                return true;
            }

            // If new count is not null and last visible item was also the last one that time.
            if (wasLastItemVisibleLastTime){
                return true;
            }

            return false;
        }
    }

    /**
     * Check if desired position should be applied after onScroll event.
     * Takes current position and retry count into the account.
     *
     * @param firstVisibleItem
     * @param visibleItemCount
     * @param totalItemCount
     * @return false if no position is about to be applied.
     */
    private boolean checkToDesiredPosition(int firstVisibleItem, int visibleItemCount, int totalItemCount){
        // Check if candidate position matches desired position - loose checking, just by index.
        synchronized (desiredPositionLock){
            if (desiredPosition == null || mList == null){
                return false;
            }

            // Position matches, finish, maybe let know listener this happened.
            if (desiredPosition.isToBottom() && (firstVisibleItem+visibleItemCount >= totalItemCount)
                    || desiredPosition.matchesTo(candPos, true))
            {
                Log.vf(TAG, "Desired position set (%s, bottom: %s), resetting", desiredPosition, desiredPosition.isToBottom());

                // If we are scrolling somewhere up, we don't exactly know the scroll offset
                // since we didn't measure it on the way up. Has to be invalidated.
                if (!desiredPosition.isToBottom()){
                    totalDeltaValid = false;
                }

                desiredPosition = null;
                scrollPositionRetry = 0;
                return false;
            }

            scrollPositionRetry += 1;
            if (scrollPositionRetry > 15){
                Log.vf(TAG, "Scroll pos retry expired, giving up, pos: %s", desiredPosition);
                applyScrollPosition(null, null);
                return false;
            }

            Log.vf(TAG, "Desired position not reached (%s), trying again, retry counter: %s.", desiredPosition, scrollPositionRetry);
            mList.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        Log.vf(TAG, "list.post(): Going to apply position %s", desiredPosition);
                        desiredPosition.applyPosition(mList);
                    } catch (Exception e) {
                        Log.ef(TAG, "Exception in applying position", e);
                    }
                }
            });

            return true;
        }
    }

    @Override
    public void onScrollStateChanged(AbsListView absListView, int curState) {
        // If scrolling was stopped, store its position.
        if (curState == AbsListView.OnScrollListener.SCROLL_STATE_IDLE && lastScrollState != AbsListView.OnScrollListener.SCROLL_STATE_IDLE) {
            if (listener == null){
                return;
            }

            Log.vf(TAG, "Scrolling ended, setting candidate [%s]", candPos);
            listener.onScrollChanged(candPos);
        }

        // init the values every time the list is moving
        if (curState == AbsListView.OnScrollListener.SCROLL_STATE_TOUCH_SCROLL ||
                curState == AbsListView.OnScrollListener.SCROLL_STATE_FLING)
        {
            for (ListTrackedElement t : listTrackedElements) {
                t.syncState(absListView);
            }
        }

        if (curState != lastScrollState && curState != AbsListView.OnScrollListener.SCROLL_STATE_IDLE){
            Log.vf(TAG, "Scroll state changed manually, resetting desired scroll position");
            applyScrollPosition(null, null);
        }

        //Log.vf(TAG, "ScrollState change, curState: %s, prevState: %s", curState, lastScrollState);
        lastScrollState = curState;
    }

    @Override
    public void onScroll(AbsListView absListView, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
        try {
            // Element position tracking so we know #px from the bottom.
            boolean wasTracked = false;
            for (ListTrackedElement t : listTrackedElements) {
                if (wasTracked) {
                    t.syncState(absListView);
                    continue;
                }

                if (t.isSafeToTrack(absListView)) {
                    wasTracked = true;
                    final int delta = t.getDeltaY();
                    totalDelta += delta;

                    listener.onScroll(absListView, delta, totalDelta);
                    t.syncState(absListView);
                } else {
                    t.reset();
                }
            }

            // Store candidate scroll position. When scrolling stops, this position is stored.
            candPos.setCount(totalItemCount);
            candPos.setIndex(firstVisibleItem);
            candPos.setOffset(ScrollPosition.getOffset(absListView));
            wasLastItemVisibleLastTime = firstVisibleItem + visibleItemCount >= totalItemCount;

            // Desired position check.
            synchronized (desiredPositionLock){
                if (desiredPosition != null){
                    boolean aboutToChange = checkToDesiredPosition(firstVisibleItem, visibleItemCount, totalItemCount);
                    if (aboutToChange){
                        return;
                    }
                }
            }

            // Check consistency of the state.
            // Screen rotation or very fast scrolling could lead to missing some items.
            // When scroll reaches bottom of the list, reset counters to the zero.
            checkConsistency(absListView, firstVisibleItem, visibleItemCount, totalItemCount);

        } catch (Exception e) {
            Log.e(TAG, "Exception in scrolling", e);
        }
    }

    /**
     * Checks total delta consistency
     */
    private void checkConsistency(AbsListView absListView, int firstVisibleItem, int visibleItemCount, int totalItemCount){
        // Potential reset when the list is scrolled to the bottom.
        final int lastPos = firstVisibleItem + visibleItemCount - 1;
        if ((lastPos + 1) != totalItemCount) {
            return;
        }

        final int idx = absListView.getLastVisiblePosition() - absListView.getFirstVisiblePosition();
        final View w = absListView.getChildAt(idx);
        if (w != null) {
            final int viewBottom = w.getBottom();
            final int lstBottomBound = absListView.getBottom() - absListView.getPaddingBottom();
            if (viewBottom <= lstBottomBound) {
                Log.vf(TAG, "Consistency check reset total counter: %s; lastPos: %s; idx: %s", totalDelta, lastPos, idx);
                resetTotal(true);
            }
        }
    }

    public long getTotalDelta() {
        return totalDelta;
    }

    public int getLastScrollState() {
        return lastScrollState;
    }

    public ScrollPosition getCandPos() {
        return candPos;
    }

    /**
     * Tracked element in the list.
     */
    private static class ListTrackedElement {
        private final int position;
        private View trackedChild;
        private int trackedChildPrevPosition;
        private int trackedChildPrevTop;

        public ListTrackedElement(int position) {
            this.position = position;
        }

        public void syncState(AbsListView view) {
            if (view.getChildCount() > 0) {
                trackedChild = getChild(view);
                trackedChildPrevTop = getY();
                trackedChildPrevPosition = view.getPositionForView(trackedChild);
            }
        }

        public void reset() {
            trackedChild = null;
        }

        public boolean isSafeToTrack(AbsListView view) {
            return (trackedChild != null) &&
                    (trackedChild.getParent() == view) && (view.getPositionForView(trackedChild) == trackedChildPrevPosition);
        }

        public int getDeltaY() {
            return getY() - trackedChildPrevTop;
        }

        private View getChild(AbsListView view) {
            switch (position) {
                case 0:
                    return view.getChildAt(0);
                case 1:
                case 2:
                    return view.getChildAt(view.getChildCount() / 2);
                case 3:
                    return view.getChildAt(view.getChildCount() - 1);
                default:
                    return null;
            }
        }

        private int getY() {
            if (position <= 1) {
                return trackedChild.getBottom();
            } else {
                return trackedChild.getTop();
            }
        }
    }
}
