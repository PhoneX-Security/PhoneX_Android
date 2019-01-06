package net.phonex.ui.lock;

import android.app.Activity;
import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import net.phonex.R;
import net.phonex.util.Log;

/**
 * Created by miroc on 10.12.14.
 */
public class PinFragment extends Fragment {

    private static final String FRAGMENT_VIEW_TYPE = "net.phonex.ui.lock.input_fragment_view_type";
    private static final String TAG = "PinFragment";

    private Listener mListener;
    private PinDisplayType mPinDisplayType;
    private BaseViewController mViewController;
    private PinFragmentConfiguration mConfig;
    private View mRootView;

    public enum PinDisplayType {
        VERIFY, CREATE, CONFIRM
    }

    public interface Listener {
        public void onValidated();
        public void onPinCreated();
        public void onTriesDepleted(int numberOfTries);
    }

    public static PinFragment newInstanceForVerification() {
        return newInstanceForVerification(null);
    }

    public static PinFragment newInstanceForVerification(PinFragmentConfiguration config) {
        return newInstance(PinDisplayType.VERIFY, config);
    }

    public static PinFragment newInstanceForCreation() {
        return newInstanceForCreation(null);
    }

    public static PinFragment newInstanceForCreation(PinFragmentConfiguration config) {
        return newInstance(PinDisplayType.CREATE, config);
    }

    private static PinFragment newInstance(PinDisplayType type, PinFragmentConfiguration config) {
        PinFragment instance = new PinFragment();
        Bundle bundle = new Bundle();
        bundle.putSerializable(FRAGMENT_VIEW_TYPE, type);
        instance.setArguments(bundle);
        instance.setConfig(config);
        Log.df(TAG, "newInstance; type [%s], configuration [%s], bundle [%s]", type, config, bundle.toString());
        return instance;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
        Bundle args = getArguments();
        mPinDisplayType = (PinDisplayType) args.getSerializable(FRAGMENT_VIEW_TYPE);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        mRootView = inflater.inflate(R.layout.layout_pin_view, container, false);
        setDisplayType(mPinDisplayType);
        initViewController();
        return mRootView;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        if (!(activity instanceof Listener)) {
            throw new ClassCastException(
                    "Hosting activity must implement PinFragment.Listener");
        } else {
            mListener = (Listener) activity;
            if (mConfig == null) setConfig(new PinFragmentConfiguration());
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
    }

    public void setConfig(PinFragmentConfiguration config) {
        mConfig = config;
    }

    public PinFragmentConfiguration getConfig() {
        return mConfig;
    }

    void onPinCreationEntered(String pinEntry) {
        mPinDisplayType = PinDisplayType.CONFIRM;
        mViewController = new ConfirmPinViewController(this, mRootView, pinEntry);
    }

    void setViewController(BaseViewController controller) {
        mViewController = controller;
    }

    void notifyValid() {
        mListener.onValidated();
    }

    void notifyCreated() {
        mListener.onPinCreated();
    }

    void notifyTriesDepleted(int numberOfTries) {
        mListener.onTriesDepleted(numberOfTries);
    }

    void setDisplayType(PinDisplayType type) {
        mPinDisplayType = type;
    }

    private void initViewController() {
        switch (mPinDisplayType) {
            case VERIFY:
                setViewController(new VerifyPinViewController(this, mRootView));
                break;
            case CREATE:
                setViewController(new CreatePinViewController(this, mRootView));
                break;
            case CONFIRM:
                mViewController.refresh(mRootView);
                break;
            default:
                throw new IllegalStateException(
                        "Invalid DisplayType " + mPinDisplayType.toString());
        }
    }

}
