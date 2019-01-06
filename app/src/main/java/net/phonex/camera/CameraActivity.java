package net.phonex.camera;

import android.content.Intent;
import android.hardware.Camera;
import android.os.Bundle;
import android.view.KeyEvent;
import android.widget.Toast;

import net.phonex.R;
import net.phonex.camera.control.CameraFragment;
import net.phonex.camera.interfaces.CameraParamsChangedListener;
import net.phonex.camera.interfaces.CameraResultListener;
import net.phonex.camera.interfaces.KeyEventsListener;
import net.phonex.camera.model.FlashMode;
import net.phonex.camera.model.FocusMode;
import net.phonex.ft.storage.FileStorageUri;
import net.phonex.pref.PreferencesConnector;
import net.phonex.ui.lock.activity.LockActionBarActivity;
import net.phonex.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by Matus on 21-Jul-15.
 */
public class CameraActivity extends LockActionBarActivity implements CameraParamsChangedListener, CameraResultListener {

    private static final String TAG = "CameraActivity";

    public static final int REQUEST_CODE = 56874;

    public static final int PHOTO_CANCELED = 456875;
    public static final int PHOTO_ACCEPTED = 456876;
    public static final int PHOTO_ERROR = 456877;

    public static final String EXTRA_URI = "uri";
    public static final String EXTRA_SINGLE_PHOTO = "single_photo";

    public static final long IDEAL_RESOLUTION = 5*1000*1000; // 5 MP

    private KeyEventsListener keyEventsListener;

    private boolean isStartedForResult;

    private PreferencesConnector preferencesConnector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        preferencesConnector = new PreferencesConnector(this);

        Intent startingIntent = getIntent();

        isStartedForResult = (startingIntent != null
                && startingIntent.hasExtra(EXTRA_SINGLE_PHOTO)
                && startingIntent.getBooleanExtra(EXTRA_SINGLE_PHOTO, false));

        Bundle bundle = createCameraParams();

        if (bundle == null) {
            Toast.makeText(this, R.string.lbl_camera_unavailable, Toast.LENGTH_LONG).show();
            return;
        }

        CameraFragment cameraFragment = CameraFragment.newInstance(bundle);
        cameraFragment.setParamsChangedListener(this);
        cameraFragment.setResultListener(this);
        keyEventsListener = cameraFragment;
        getFragmentManager()
                .beginTransaction()
                .replace(android.R.id.content, cameraFragment)
                .commit();
    }

    private Bundle createCameraParams() {
        Bundle bundle = new Bundle();

        int idealResolutionIndex = preferencesConnector.getInteger(CameraFragment.IDEAL_RESOLUTION, -1);

        if (idealResolutionIndex == -1) {
            Camera camera = getPrimaryCamera();
            if (camera == null) {
                return null;
            }
            List<Camera.Size> sizes = camera.getParameters().getSupportedPictureSizes();

            camera.release();

            List<Camera.Size> descendingSizes = new ArrayList<>(sizes);
            Collections.sort(descendingSizes, (size, size2) -> (size2.width * size2.height) - (size.width * size.height));
            idealResolutionIndex = descendingSizes.size() - 1; // smallest photo if all are larger than IDEAL_RESOLUTION
            for (Camera.Size size : descendingSizes) {
                Log.df(TAG, "Resolution %d by %d (%.1f MP) is %s than desired %.1f MP", size.width, size.height, ((float)size.width * size.height) / (1000*1000), ((float)size.width * size.height) < IDEAL_RESOLUTION ? "lower" : "higher", ((float)IDEAL_RESOLUTION) / (1000*1000));
                if (size.width * size.height < IDEAL_RESOLUTION) {
                    idealResolutionIndex = sizes.indexOf(size);
                    break;
                }
            }

            preferencesConnector.setInteger(CameraFragment.IDEAL_RESOLUTION, idealResolutionIndex);
        }



        bundle.putInt(CameraFragment.FLASH_MODE, preferencesConnector.getInteger(CameraFragment.FLASH_MODE, FlashMode.AUTO.getId()));
        // Ignore user's last choice and prefer reasonable resolution. Front camera settings are not affected.
        //bundle.putInt(CameraFragment.RESOLUTION, preferencesConnector.getInteger(CameraFragment.RESOLUTION, sizeIndex));
        bundle.putInt(CameraFragment.RESOLUTION, idealResolutionIndex);
        bundle.putInt(CameraFragment.FOCUS_MODE, preferencesConnector.getInteger(CameraFragment.FOCUS_MODE, FocusMode.TOUCH.getId()));

        bundle.putInt(CameraFragment.FLASH_MODE_FRONT, preferencesConnector.getInteger(CameraFragment.FLASH_MODE_FRONT, FlashMode.AUTO.getId()));
        bundle.putInt(CameraFragment.RESOLUTION_FRONT, preferencesConnector.getInteger(CameraFragment.RESOLUTION_FRONT, 0));
        bundle.putInt(CameraFragment.FOCUS_MODE_FRONT, preferencesConnector.getInteger(CameraFragment.FOCUS_MODE_FRONT, FocusMode.AUTO.getId()));

        bundle.putBoolean(CameraFragment.FRONT_CAMERA, false);
        bundle.putBoolean(CameraFragment.SINGLE_PHOTO, isStartedForResult);
        return bundle;
    }

    private Camera getPrimaryCamera() {
        Camera c = null;
        try {
            c = Camera.open(getCameraId(false));
        } catch (Exception e) {
            Log.e(TAG, "Failed to open camera", e);
        }
        return c;
    }

    private int getCameraId(boolean useFrontCamera) {
        int count = Camera.getNumberOfCameras();
        int result = -1;

        if (count > 0) {
            result = 0;

            Camera.CameraInfo info = new Camera.CameraInfo();
            for (int i = 0; i < count; i++) {
                Camera.getCameraInfo(i, info);

                if (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK
                        && !useFrontCamera) {
                    result = i;
                    break;
                } else if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT
                        && useFrontCamera) {
                    result = i;
                    break;
                }
            }
        }
        return result;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            // do not hijack the volume keys, zooming by pinch gesture
//            case KeyEvent.KEYCODE_VOLUME_UP:
//                keyEventsListener.zoomIn();
//                return true;
//            case KeyEvent.KEYCODE_VOLUME_DOWN:
//                keyEventsListener.zoomOut();
//                return true;
            case KeyEvent.KEYCODE_BACK:
                onBackPressed();
                return true;
            case KeyEvent.KEYCODE_CAMERA:
                keyEventsListener.takePhoto();
                return true;
        }
        return false;
    }

    // CameraParamsChangedListener stuff
    @Override
    public void onResolutionChanged(int id, boolean front) {
        preferencesConnector.setInteger(front ? CameraFragment.RESOLUTION_FRONT : CameraFragment.RESOLUTION, id);
    }

    @Override
    public void onFlashModeChanged(int id, boolean front) {
        preferencesConnector.setInteger(front ? CameraFragment.FLASH_MODE_FRONT : CameraFragment.FLASH_MODE, id);
    }

    @Override
    public void onCameraInstanceChanged(boolean front) {
        // do not remember front / back camera, always start with back
    }

    @Override
    public void onFocusModeChanged(int id, boolean front) {
        preferencesConnector.setInteger(front ? CameraFragment.FOCUS_MODE_FRONT : CameraFragment.FOCUS_MODE, id);
    }

    protected Intent setIntentData(FileStorageUri uri) {
        Intent intent = new Intent();
        if (uri != null) {
            intent.putExtra(EXTRA_URI, uri);
        }
        return intent;
    }

    @Override
    public boolean photoAccepted(FileStorageUri uri) {
        if (isStartedForResult) {
            setResult(PHOTO_ACCEPTED, setIntentData(uri));
            finish();
        } else {
            // TODO show toast?
        }
        return isStartedForResult;
    }

    @Override
    public void photoCancelled() {
        setResult(PHOTO_CANCELED, setIntentData(null));
        finish();
    }

    @Override
    public void photoError() {
        setResult(PHOTO_ERROR, setIntentData(null));
        finish();
    }

    @Override
    protected String activityAnalyticsName() {
        return this.getClass().getSimpleName();
    }
}
