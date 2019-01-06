package net.phonex.camera.control;

import android.app.Fragment;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.OrientationEventListener;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.drawable.GlideDrawable;
import com.bumptech.glide.request.animation.GlideAnimation;
import com.bumptech.glide.request.target.GlideDrawableImageViewTarget;

import net.phonex.R;
import net.phonex.camera.interfaces.CameraParamsChangedListener;
import net.phonex.camera.interfaces.CameraResultListener;
import net.phonex.camera.interfaces.FocusCallback;
import net.phonex.camera.interfaces.KeyEventsListener;
import net.phonex.camera.interfaces.PhotoSavedListener;
import net.phonex.camera.interfaces.PreviewActionListener;
import net.phonex.camera.model.FlashMode;
import net.phonex.camera.model.FocusMode;
import net.phonex.camera.util.Exif;
import net.phonex.camera.util.SavingPhotoTask;
import net.phonex.ft.storage.FileStorageUri;
import net.phonex.util.Log;
import net.phonex.util.analytics.AnalyticsReporter;
import net.phonex.util.analytics.AppButtons;
import net.phonex.util.analytics.AppEvents;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Created by Matus on 21-Jul-15.
 */
public class CameraFragment extends Fragment implements KeyEventsListener,
        CameraParamsChangedListener, FocusCallback, Camera.ShutterCallback, PreviewActionListener, PhotoSavedListener {

    // camera configuration
    public static final String RESOLUTION = "resolution";
    public static final String IDEAL_RESOLUTION = "ideal_resolution";
    public static final String RESOLUTION_LIST = "resolution_list";
    public static final String FOCUS_MODE = "focus_mode";
    public static final String AUTO_FOCUS = "auto_focus";
    public static final String FLASH_MODE = "flash_mode";
    public static final String FRONT_CAMERA = "front_camera";
    public static final String SINGLE_PHOTO = "single_photo";

    public static final String RESOLUTION_FRONT = "resolution_front";
    public static final String FOCUS_MODE_FRONT = "focus_mode_front";
    public static final String FLASH_MODE_FRONT = "flash_mode_front";

    private static final String TAG = "CameraFragment";
    // used as reference orientation for buttons rotation; portrait with speaker up
    private static final int DEFAULT_ORIENTATION = 90;
    // active configuration
    private Camera.Size resolution;
    private boolean hasFlash;
    private FlashMode flashMode;
    private boolean hasAutoFocus;
    private FocusMode focusMode;
    private boolean hasZoom;
    private int zoomIndex;
    private int minZoomIndex;
    private int maxZoomIndex;
    private boolean isStartedForResult;
    private boolean useFrontCamera;

    // camera
    private Camera camera;
    private List<Camera.Size> pictureSizes;
    private List<Camera.Size> previewSizes;
    private Camera.Parameters parameters;
    private int cameraId;
    private int outputOrientation;
    private int oldButtonOrientation; // starting point for animation
    private int oldOrientation; // last orientation when we rotated buttons
    private static final int BUTTON_ROTATION_THRESHOLD = 20;

    private int photoRotation; // rotation of photo in the preview

    // camera configuration listener
    private CameraParamsChangedListener paramsChangedListener;
    private CameraResultListener resultListener;
    private OrientationEventListener orientationListener;

    // Views
    private CameraPreview cameraPreview;
    private ViewGroup previewContainer;
    private View mCapture; // shutter (capture) button
    private ProgressBar progressBar; // progress bar under the capture button
    private ImageButton flashModeButton;
    private ImageButton cameraSettings;
    private View splashScreen;

    // Photo preview views
    private ImageButton rotateLeftButton;
    private ImageButton rotateRightButton;
    private ImageButton confirmButton;
    private ImageButton deleteButton;
    private ProgressBar savingProgressBar;
    private ProgressBar loadingProgressBar;
    private View savingProgressLayout;

    private RetryTarget previewTarget;
    private RetryTarget fullTarget;
    private PinchImageView previewImageView;
    private PinchImageView fullImageView;

    // Last picture taken (might be in memory allocated by camera or in heap, if picture was rotated)
    private byte[] photo;

    private Camera.PictureCallback pictureCallback;

    private Handler hidePhotoPreviewHandler;
    private AnalyticsReporter analyticsReporter;

    /**
     * use newInstance instead
     */
    public CameraFragment() {
        pictureCallback = new Camera.PictureCallback() {
            @Override
            public void onPictureTaken(byte[] data, Camera camera) {
                cameraPreview.onPictureTaken();
                enableControls();
                photo = data;
                showPhotoPreview();
            }
        };
    }

    public static CameraFragment newInstance(Bundle params) {
        CameraFragment fragment = new CameraFragment();
        fragment.setArguments(params);
        return fragment;
    }

    private void cameraError() {
        // TODO error message, or error layout
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        boolean useFrontCamera = getArguments().getBoolean(FRONT_CAMERA, false);
        initCamera(useFrontCamera);
        analyticsReporter = AnalyticsReporter.from(this);
    }



    private void initCamera(boolean useFrontCamera) {
        if (camera != null) {
            camera.release();
            camera = null;
        }
        camera = getCameraInstance(useFrontCamera);

        if (camera == null) {
            Log.d(TAG, "getCameraInstance returned null");
            cameraError();
            return;
        }

        cameraPreview = null;

        parameters = camera.getParameters();

        hasZoom = parameters.isZoomSupported();
        if (hasZoom) {
            zoomIndex = minZoomIndex = 0;
            maxZoomIndex = parameters.getMaxZoom();
        }

        List<String> supportedFlashModes = parameters.getSupportedFlashModes();
        hasFlash = !(supportedFlashModes == null || supportedFlashModes.isEmpty());

        List<String> supportedFocusModes = parameters.getSupportedFocusModes();
        hasAutoFocus = supportedFocusModes != null && supportedFocusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO);

        previewSizes = sortSizes(parameters.getSupportedPreviewSizes());
        pictureSizes = sortSizes(parameters.getSupportedPictureSizes());

        expandParams(getArguments(), useFrontCamera);
        initParams();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        if (camera == null) {
            View view = inflater.inflate(R.layout.camera_n_a_fragment, container, false);
            initToolbar(view);
            cameraError();
            return view;
        }

        View view = inflater.inflate(R.layout.camera_fragment, container, false);

        splashScreen = view.findViewById(R.id.splash_screen);

        initControls(view);

        return view;
    }

    private void initControls(View view) {
        progressBar = (ProgressBar) view.findViewById(R.id.progress);

        mCapture = view.findViewById(R.id.capture);
        if (mCapture != null) {
            mCapture.setOnClickListener(v -> {
                analyticsReporter.buttonClick(AppButtons.CAMERA_CAPTURE);
                takePhoto();
            });
        }

        flashModeButton = (ImageButton) view.findViewById(R.id.flash_mode);
        if (flashModeButton != null) {
            flashModeButton.setOnClickListener(v -> {
                analyticsReporter.buttonClick(AppButtons.CAMERA_FLASH);
                switchFlashMode();
            });

            if (!hasFlash) {
                flashModeButton.setVisibility(View.GONE);
            } else {
                flashModeButton.setVisibility(View.VISIBLE);
                setFlashModeImage(flashMode);
            }
        }

        cameraSettings = (ImageButton) view.findViewById(R.id.camera_settings);
        if (cameraSettings != null) {
            view.findViewById(R.id.camera_settings).setOnClickListener(v -> {
                analyticsReporter.buttonClick(AppButtons.CAMERA_SETTINGS);
                CameraSettingsDialogFragment.newInstance(packSettings(), CameraFragment.this).show(getFragmentManager());
            });
        }

        previewContainer = (ViewGroup) view.findViewById(R.id.camera_preview);

        // photo preview controls
        rotateLeftButton = (ImageButton) view.findViewById(R.id.rotate_left);
        rotateLeftButton.setOnClickListener(v -> {
            analyticsReporter.buttonClick(AppButtons.CAMERA_ROTATE);
            onRotateLeft();
        });
        rotateRightButton = (ImageButton) view.findViewById(R.id.rotate_right);
        rotateRightButton.setOnClickListener(v -> {
            analyticsReporter.buttonClick(AppButtons.CAMERA_ROTATE);
            onRotateRight();
        });
        deleteButton = (ImageButton) view.findViewById(R.id.repeat);
        deleteButton.setOnClickListener(v -> {
            analyticsReporter.buttonClick(AppButtons.CAMERA_DELETE);
            onDelete();
        });
        confirmButton = (ImageButton) view.findViewById(R.id.confirm);
        confirmButton.setOnClickListener(v -> {
            analyticsReporter.buttonClick(AppButtons.CAMERA_CONFIRM);
            onConfirm();
        });
        if (isStartedForResult) {
            confirmButton.setImageResource(R.drawable.svg_send_enabled);
        } else {
            confirmButton.setImageResource(R.drawable.btn_done);
        }
        previewImageView = (PinchImageView) view.findViewById(R.id.future_photo);
        fullImageView = (PinchImageView) view.findViewById(R.id.full_photo);
        savingProgressBar = (ProgressBar) view.findViewById(R.id.saving_progress);
        loadingProgressBar = (ProgressBar) view.findViewById(R.id.loading_progress);
        savingProgressLayout = view.findViewById(R.id.saving_progress_layout);

        // make the controls unclickable
        disableControls();
    }

    /**
     * Disable controls while photo is being taken or saved
     */
    private void disableControls() {
        mCapture.setEnabled(false);
        mCapture.setVisibility(View.INVISIBLE);
        progressBar.setVisibility(View.VISIBLE);
        cameraSettings.setEnabled(false);
        flashModeButton.setEnabled(false);

        rotateLeftButton.setEnabled(false);
        rotateRightButton.setEnabled(false);
        confirmButton.setEnabled(false);
        deleteButton.setEnabled(false);
    }

    private void enableControls() {
        mCapture.setEnabled(true);
        mCapture.setVisibility(View.VISIBLE);
        progressBar.setVisibility(View.INVISIBLE);
        cameraSettings.setEnabled(true);
        flashModeButton.setEnabled(true);

        rotateLeftButton.setEnabled(true);
        rotateRightButton.setEnabled(true);
        confirmButton.setEnabled(true);
        deleteButton.setEnabled(true);
    }

    private void initToolbar(View view) {
        Toolbar toolbar = (Toolbar) view.findViewById(R.id.my_toolbar);

        toolbar.setBackgroundColor(getResources().getColor(R.color.camera_toolbar_transparent));

        // Manually setting icon + action
        toolbar.setNavigationIcon(R.drawable.abc_ic_ab_back_mtrl_am_alpha);
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cancel();
            }
        });
    }

    private void cancel() {
        if (resultListener != null) {
            resultListener.photoCancelled();
        }
    }

    /**
     * A safe way to get an instance of the Camera object.
     */
    private Camera getCameraInstance(boolean useFrontCamera) {
        Camera c = null;
        try {
            c = Camera.open(getCameraId(useFrontCamera));
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
        cameraId = result;
        return result;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (camera != null) {
            try {
                camera.reconnect();
                Log.d(TAG, "onResume() Reconnected to camera");
            } catch (IOException e) {
                Log.e(TAG, "onResume() Could not reconnect to camera", e);
                cameraError();
            }
        } else {
            initCamera(useFrontCamera);
            Log.d(TAG, "onResume() Camera initialized");
        }
        if (orientationListener == null) {
            initOrientationListener();
        }
        orientationListener.enable();
        enablePreview();
    }

    public void enablePreview() {
        Log.d(TAG, "enablePreview()");
        if (cameraPreview != null) {
            cameraPreview.enablePreview();
            enableControls();
            Log.d(TAG, "enablePreview() preview enabled");
        } else {
            // splash screen should display also when changing camera instance
            new Handler(new Handler.Callback() {
                @Override
                public boolean handleMessage(Message msg) {
                    if (msg != null && msg.what == 9874521) {
                        initPreview();
                        return true;
                    }
                    return false;
                }
            }).sendEmptyMessageDelayed(9874521, 200);
            Log.d(TAG, "enablePreview() timed message sent");
        }
    }

    private void initPreview() {
        if (previewContainer == null) {
            Log.d(TAG, "Layout has not been inflated yet or camera not available");
            return;
        }
        View rootView = (View) previewContainer.getParent();
        if (rootView != null) {

            setPreviewContainerSize(rootView.getWidth(), rootView.getHeight(), parameters.getPreviewSize());

            RelativeLayout focusLayout = new RelativeLayout(getActivity());
            cameraPreview = new CameraPreview(getActivity(), camera, parameters, focusLayout, this, this);

            cameraPreview.setFocusMode(focusMode);
            cameraPreview.enablePreview();

            previewContainer.addView(cameraPreview);
            previewContainer.addView(focusLayout);
        } else {
            Log.w(TAG, "getView() == null");
        }
    }

    public void setParamsChangedListener(CameraParamsChangedListener paramsChangedListener) {
        this.paramsChangedListener = paramsChangedListener;
    }

    public void setResultListener(CameraResultListener resultListener) {
        this.resultListener = resultListener;
    }

    @Override
    public void onFocused(Camera camera) {
        try {
            camera.takePicture(this, null, pictureCallback);
        } catch (Exception e) {
            Log.e(TAG, "camera.takePicture() failed", e);
            cameraError();
        }
    }

    @Override
    public void onCameraReady() {
        // uncover initialization hidden by a splash screen
        Log.d(TAG, "onCameraReady()");
        splashScreen.setVisibility(View.INVISIBLE);
        enableControls();
    }

    @Override
    public void onCameraBusy() {
        Log.d(TAG, "onCameraBusy()");
        splashScreen.setVisibility(View.VISIBLE);
        disableControls();
    }

    @Override
    public void onSurfaceDestroyed() {
        Log.d(TAG, "onSurfaceDestroyed()");
        if (camera != null) {
            camera.release();
            camera = null;
        }
        Log.d(TAG, "onSurfaceDestroyed() camera released");
    }

    @Override
    public void onPause() {
        super.onPause();
        if (orientationListener != null) {
            orientationListener.disable();
            orientationListener = null;
        }
        // stop the preview and release the camera
        // so that other apps may use the camera
        // and the camera and preview will be correctly restarted on resume
        // this will be done in camera preview's surface destroyed
    }

    private void expandParams(Bundle params, boolean useFrontCamera) {
        if (params == null) {
            params = new Bundle();
        }
        int id = 0;
        if (useFrontCamera) {
            if (params.containsKey(RESOLUTION_FRONT)) {
                id = params.getInt(RESOLUTION_FRONT, 0);
            }
            if (id < pictureSizes.size()) {
                resolution = pictureSizes.get(id);
            } else if (pictureSizes.isEmpty()) {
                Log.d(TAG, "No available picture sizes");
            } else {
                resolution = pictureSizes.get(0);
                Log.df(TAG, "Picture size out of range %d of %d", id, pictureSizes.size());
            }

            id = 0;
            if (params.containsKey(FOCUS_MODE_FRONT)) {
                id = params.getInt(FOCUS_MODE_FRONT);
            }
            focusMode = FocusMode.getFocusModeById(id);
            id = 0;
            if (params.containsKey(FLASH_MODE_FRONT)) {
                id = params.getInt(FLASH_MODE_FRONT);
            }
            flashMode = FlashMode.getFlashModeById(id);
        } else {
            if (params.containsKey(RESOLUTION)) {
                id = params.getInt(RESOLUTION, 0);
            }
            if (id < pictureSizes.size()) {
                resolution = pictureSizes.get(id);
            } else if (pictureSizes.isEmpty()) {
                Log.d(TAG, "No available picture sizes");
            } else {
                resolution = pictureSizes.get(0);
                Log.df(TAG, "Picture size out of range %d of %d", id, pictureSizes.size());
            }
            id = 0;
            if (params.containsKey(FOCUS_MODE)) {
                id = params.getInt(FOCUS_MODE);
            }
            focusMode = FocusMode.getFocusModeById(id);
            id = 0;
            if (params.containsKey(FLASH_MODE)) {
                id = params.getInt(FLASH_MODE);
            }
            flashMode = FlashMode.getFlashModeById(id);
        }

        isStartedForResult = params.getBoolean(SINGLE_PHOTO, false);
    }

    private Bundle packSettings() {
        Bundle params = new Bundle();

        params.putSerializable(RESOLUTION_LIST, new ArrayList<>(pictureSizes));
        params.putInt(RESOLUTION, pictureSizes.indexOf(resolution));
        params.putInt(FOCUS_MODE, focusMode.getId());
        params.putBoolean(AUTO_FOCUS, hasAutoFocus);
        params.putBoolean(FRONT_CAMERA, useFrontCamera);
        return params;
    }

    private void initParams() {
        if (hasFlash) {
            setFlashMode(parameters, flashMode);
        }

        setPictureSize(parameters, resolution);
        setPreviewSize(parameters.getPictureSize());

        parameters.removeGpsData();

        try {
            camera.setParameters(parameters);
        } catch (RuntimeException ex) {
            Log.ef(TAG, ex, "Set parameters failed in initParams(), width = %d, height = %d, previewWidth = %d, previewHeight = %d",
                    resolution.width, resolution.height, parameters.getPreviewSize().width, parameters.getPreviewSize().height);
        }
    }

    @Override
    public void onResolutionChanged(int id, boolean front) {
        // always disable the preview before changing any parameters!
        stopPreview();

        resolution = pictureSizes.get(id);
        setPictureSize(parameters, resolution);

        if (paramsChangedListener != null) {
            paramsChangedListener.onResolutionChanged(id, front);
        }
        if (getArguments() != null) {
            getArguments().putInt(front ? RESOLUTION_FRONT : RESOLUTION, id);
        }

        setPreviewSize(parameters.getPictureSize());

        RelativeLayout parent = (RelativeLayout) previewContainer.getParent();
        setPreviewContainerSize(parent.getWidth(), parent.getHeight(), parameters.getPreviewSize());

        try {
            camera.setParameters(parameters);
        } catch (RuntimeException ex) {
            // TODO first set parameters for camera, then change layout
            Log.e(TAG, "Set parameters failed in onResolutionChanged()", ex);
            return;
        }

        enablePreview();
        // sometimes onCameraReady() is not called because CameraPreview.surfaceChanged()
        // is not called either. Since the splash screen is not visible anyway on tested devices,
        // just hide it here
        Log.d(TAG, "Forcing onCameraReady");
        onCameraReady();
    }

    private void stopPreview() {
        disableControls();
        if (cameraPreview != null) {
            cameraPreview.disablePreview();
        }
    }

    @Override
    public void onCameraInstanceChanged(boolean useFrontCamera) {
        // disable the orientation listener, because it changes camera parameters
        if (orientationListener != null) {
            orientationListener.disable();
            orientationListener = null;
        }
        // stop the current preview
        stopPreview();
        previewContainer.removeView(cameraPreview);
        // set to null, because old preview is using old camera
        cameraPreview = null;
        // obtain camera instance again, old camera is released in initCamera
        this.useFrontCamera = useFrontCamera;
        initCamera(useFrontCamera);
        // change controls, because cameras are different
        initControls(getView());
        // reenable the orientation listener
        initOrientationListener();
        orientationListener.enable();
        // start preview for the new camera (long operation)
        enablePreview();
    }

    @Override
    public void onFlashModeChanged(int id, boolean front) {
        if (paramsChangedListener != null) {
            paramsChangedListener.onFlashModeChanged(id, front);
        }
        if (getArguments() != null) {
            getArguments().putInt(front ? FLASH_MODE_FRONT : FLASH_MODE, id);
        }
    }

    @Override
    public void onFocusModeChanged(int id, boolean front) {
        focusMode = FocusMode.getFocusModeById(id);
        cameraPreview.setFocusMode(focusMode);
        if (paramsChangedListener != null) {
            paramsChangedListener.onFocusModeChanged(id, front);
        }
        if (getArguments() != null) {
            getArguments().putInt(front ? FOCUS_MODE_FRONT : FOCUS_MODE, id);
        }
    }

    @Override
    public void zoomIn() {
        if (!hasZoom) return;
        if (++zoomIndex > maxZoomIndex) {
            zoomIndex = maxZoomIndex;
        }
        setZoom(zoomIndex);
    }

    @Override
    public void zoomOut() {
        if (!hasZoom) return;
        if (--zoomIndex < minZoomIndex) {
            zoomIndex = minZoomIndex;
        }
        setZoom(zoomIndex);
    }

    @Override
    public void takePhoto() {
        disableControls();
        cameraPreview.takePicture();
    }

    private void setZoom(int index) {
        parameters.setZoom(index);
        try {
            camera.setParameters(parameters);
        } catch (RuntimeException ex) {
            Log.e(TAG, "Set parameters failed in setZoom()", ex);
            return;
        }
    }

    private void switchFlashMode() {
        switch (flashMode) {
            case AUTO:
                flashMode = FlashMode.ON;
                break;
            case ON:
                flashMode = FlashMode.OFF;
                break;
            case OFF:
                flashMode = FlashMode.AUTO;
                break;
        }
        setFlashMode(parameters, flashMode);
        setFlashModeImage(flashMode);
        try {
            camera.setParameters(parameters);
        } catch (RuntimeException ex) {
            Log.e(TAG, "Set parameters failed in switchFlashMode()", ex);
            return;
        }

        onFlashModeChanged(flashMode.getId(), useFrontCamera);
    }

    private void setFlashMode(Camera.Parameters parameters, FlashMode flashMode) {
        switch (flashMode) {
            case ON:
                parameters.setFlashMode(Camera.Parameters.FLASH_MODE_ON);
                break;
            case OFF:
                parameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
                break;
            case AUTO:
                parameters.setFlashMode(Camera.Parameters.FLASH_MODE_AUTO);
                break;
        }
    }

    private void setFlashModeImage(FlashMode flashMode) {
        switch (flashMode) {
            case ON:
                flashModeButton.setImageResource(R.drawable.cam_flash_fill_flash_icn);
                break;
            case OFF:
                flashModeButton.setImageResource(R.drawable.cam_flash_off_icn);
                break;
            case AUTO:
                flashModeButton.setImageResource(R.drawable.cam_flash_auto_icn);
                break;
        }
    }

    private void setPictureSize(Camera.Parameters parameters, Camera.Size size) {
        if (size != null) {
            parameters.setPictureSize(size.width, size.height);
        }
    }

    private void setPreviewSize(Camera.Size photoSize) {
        Camera.Size size = previewSizes.get(0);

        boolean selected = false;
        int precision = 100;

        while (!selected && precision > 1) {
            for (int i = 0; i < previewSizes.size(); i++) {
                if ((int) (precision * ((double) previewSizes.get(i).width / previewSizes.get(i).height))
                        == (int) (precision * ((double) photoSize.width / photoSize.height))) {
                    size = previewSizes.get(i);
                    selected = true;
                    Log.d(TAG, "Photo size is " + photoSize.width + "x" + photoSize.height);
                    Log.d(TAG, "Selected preview size is " + size.width + "x" + size.height + " (aspect ratio precision " + precision + ")");
                    break;
                }
            }
            precision /= 5;
        }

        parameters.setPreviewSize(size.width, size.height);
    }

    /**
     * @param width       Parent width
     * @param height      Parent height
     * @param previewSize size of corresponding preview
     */
    private void setPreviewContainerSize(int width, int height, Camera.Size previewSize) {
        int w, h;
        // fit height
        h = height;
        w = (int) (((double) h / previewSize.width) * previewSize.height);
        Log.d(TAG, "Parent: " + width + "x" + height + " Preview: " + w + "x" + h);
        if (w > width) {
            // fit width
            w = width;
            h = (int) (((double) width / previewSize.height) * previewSize.width);
            Log.d(TAG, "Not good enough, again: " + w + "x" + h);
        }
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(w, h);
        int hMargin = Math.max((width - w) / 2, 0);
        int wMargin = Math.max((height - h) / 2, 0);
        params.setMargins(hMargin, wMargin, hMargin, wMargin);
        previewContainer.setLayoutParams(params);
    }

    private List<Camera.Size> sortSizes(List<Camera.Size> sizes) {
        List<Camera.Size> newSizes = new ArrayList<>(sizes);
        Collections.sort(newSizes, new Comparator<Camera.Size>() {
            @Override
            public int compare(Camera.Size size, Camera.Size size2) {
                return (size2.width * size2.height) - (size.width * size.height);
            }
        });

        return newSizes;
    }

    private void rotateButtons(int oldOrientation, int newOrientation) {
        // if objects have different size they need their own animation!
        // e.g. when flash button was not shown, it had different size and all animations were broken
        // check that if rotation is around bad pivot point!

        Animation animation = new RotateAnimation(oldOrientation, newOrientation, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        animation.setDuration(200);
        animation.setRepeatCount(0);
        animation.setFillAfter(true);

        // rotate all buttons, even invisible
        rotateLeftButton.startAnimation(animation);
        rotateRightButton.startAnimation(animation);
        confirmButton.startAnimation(animation);
        deleteButton.startAnimation(animation);

        Animation flashButtonAnimation = new RotateAnimation(oldOrientation, newOrientation, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        flashButtonAnimation.setDuration(200);
        flashButtonAnimation.setRepeatCount(0);
        flashButtonAnimation.setFillAfter(true);

        flashModeButton.startAnimation(flashButtonAnimation);

        mCapture.startAnimation(animation);
        cameraSettings.startAnimation(animation);

        previewImageView.setRotation((photoRotation + newOrientation) % 360);
        previewImageView.postInvalidate();
        fullImageView.setRotation((photoRotation + newOrientation) % 360);
        fullImageView.postInvalidate();
    }

    private void initOrientationListener() {
        outputOrientation = 999;
        oldButtonOrientation = DEFAULT_ORIENTATION;
        oldOrientation = 999;
        orientationListener = new OrientationEventListener(getActivity()) {

            @Override
            public void onOrientationChanged(int orientation) {
                if (camera != null && orientation != ORIENTATION_UNKNOWN) {
                    int newOutputOrientation = getCameraPictureRotation(orientation, false);
                    if (newOutputOrientation != outputOrientation) {
                        outputOrientation = newOutputOrientation;
                        try {
                            parameters.setRotation(outputOrientation);
                            camera.setParameters(parameters);
                        } catch (Exception e) {
                            Log.e(TAG, "Exception updating camera parameters in orientation change", e);
                        }
                    }
                    // buttons would flicker if orientation was changed quickly e.g. 89-91-89-91
                    // check if orientation changed by some threshold
                    if (Math.abs(orientation - oldOrientation) > BUTTON_ROTATION_THRESHOLD) {
                        int newButtonOrientation = DEFAULT_ORIENTATION - getCameraPictureRotation(orientation, true);
                        if (newButtonOrientation != oldButtonOrientation) {
                            rotateButtons(oldButtonOrientation, newButtonOrientation);
                            oldButtonOrientation = newButtonOrientation;
                            oldOrientation = orientation;
                        }
                    }
                }
            }
        };
    }

    /**
     * Orientation for camera is different than for buttons, because front camera will mirror image
     * @param orientation
     * @param forButtons
     * @return
     */
    private int getCameraPictureRotation(int orientation, boolean forButtons) {
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(cameraId, info);
        int rotation;

        orientation = (orientation + 45) / 90 * 90;

        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            if (forButtons) {
                rotation = (info.orientation + orientation + 180) % 360;
            } else {
                rotation = (info.orientation - orientation + 360) % 360;
            }
        } else { // back-facing camera
            rotation = (info.orientation + orientation) % 360;
        }

        return rotation;
    }

    @Override
    public void onShutter() {
        Log.d(TAG, "onShutter()");

        Animation fadeOut = new AlphaAnimation(1, 0.7f);
        fadeOut.setInterpolator(new AccelerateInterpolator());
        fadeOut.setDuration(100);

        Animation fadeIn = new AlphaAnimation(0.7f, 1);
        fadeIn.setInterpolator(new DecelerateInterpolator());
        fadeIn.setStartOffset(100);
        fadeIn.setDuration(100);

        AnimationSet animation = new AnimationSet(false);
        animation.addAnimation(fadeOut);
        animation.addAnimation(fadeIn);
        cameraPreview.startAnimation(animation);
    }

    private void showPhotoPreview() {
        stopPreview();
        View rootView = getView();
        if (rootView == null) {
            Log.e(TAG, "Cannot display preview");
            return;
        }

        splashScreen.setVisibility(View.INVISIBLE);
        enableControls();

        View photoPreview = rootView.findViewById(R.id.photo_preview);
        photoPreview.setVisibility(View.VISIBLE);
        photoPreview.setClickable(true);
        savingProgressLayout.setVisibility(View.INVISIBLE);
        loadPhoto(false);
        // this will center photo and fit to screen
        //imageView.locateImage();
    }

    private void hidePhotoPreview() {
        View rootView = getView();
        if (rootView == null) {
            Log.e(TAG, "Cannot hide preview");
            return;
        }

        View photoPreview = rootView.findViewById(R.id.photo_preview);
        photoPreview.setClickable(false);
        photoPreview.setVisibility(View.GONE);

        // Clear the image from Glide's cache. We will need the memory if other photo will be taken.
        // The photo is unlikely to be displayed again soon.
        Glide.clear(previewTarget);
        Glide.clear(fullTarget);

        // rotate view back
        previewImageView.postRotate(-photoRotation);
        previewImageView.postInvalidate();

        fullImageView.postRotate(-photoRotation);
        fullImageView.postInvalidate();

        photoRotation = 0; // next photo is not rotated, exactly as taken
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
        AnalyticsReporter.from(this).event(AppEvents.PHOTO_TAKEN);

        if (photo == null) {
            Log.e(TAG, "loadPhoto with null photo");
            // force error
            Glide.with(getActivity())
                    .load("")
                    .error(R.drawable.ic_broken_image_black_48px)
                    .into(previewImageView);
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
        } else {
            width = 300;
            height = 300;
        }

        Log.df(TAG, "Loading image in resolution %d x %d", width, height);

        Glide.with(getActivity())
                .load(photo)
                .override(width, height)
                //.placeholder(R.drawable.ic_photo_black_48px) // no placeholder, it will cover image!
                .error(R.drawable.ic_broken_image_black_48px) // if preview fails, full will not start
                // no cache, because we have the image in memory (shared with native camera code)
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .crossFade() // animate
                .into(target);
    }

    private void rotatePhoto(int angle) {
        if (photo == null) {
            Log.e(TAG, "rotatePhoto with null photo");
            return;
        }

        if (angle != 90 && angle != -90) {
            Log.w(TAG, "Unsupported angle " + angle);
            return;
        }

        byte[] newJpeg = Exif.rotate(photo, angle);
        if (newJpeg == null) {
            Log.e(TAG, "setOrientation failed");
            return;
        } else {
            Log.d(TAG, "setOrientation ok");
            photo = newJpeg;
        }

        photoRotation += angle;
        photoRotation %= 360;

        previewImageView.postRotate(angle);
        previewImageView.postInvalidate();

        fullImageView.postRotate(angle);
        fullImageView.postInvalidate();
    }

    @Override
    public void onDelete() {
        hidePhotoPreview();
        photo = null;
        enablePreview();
    }

    @Override
    public void onConfirm() {
        disableControls();
        new SavingPhotoTask(getActivity(), photo, this).execute((Void) null);
    }

    @Override
    public void onRotateLeft() {
        disableControls();
        rotatePhoto(-90);
        enableControls();
    }

    @Override
    public void onRotateRight() {
        disableControls();
        rotatePhoto(90);
        enableControls();
    }


    @Override
    public void photoSaved(FileStorageUri uri) {
        AnalyticsReporter.from(this).event(AppEvents.PHOTO_SAVED);
        if (resultListener != null) {
            if (!resultListener.photoAccepted(uri)) {
                // activity is not ending
                hidePhotoPreview();
                photo = null;
                enablePreview();
                Toast.makeText(getActivity(), String.format(getString(R.string.camera_photo_saved), uri.getFilename()), Toast.LENGTH_SHORT).show();
            }
        } else {
            Log.d(TAG, "CameraResultListener is null");
            getActivity().finish();
        }
    }

    @Override
    public void photoError() {
        enableControls();
        if (resultListener != null) {
            resultListener.photoError();
        } else {
            Log.d(TAG, "CameraResultListener is null");
            getActivity().finish();
        }
    }

    @Override
    public void photoProgress(Integer percentageDone) {
        savingProgressLayout.setVisibility(View.VISIBLE);
        savingProgressBar.setIndeterminate(false);
        savingProgressBar.setMax(100);
        savingProgressBar.setProgress(percentageDone);
    }
}
