package net.phonex.camera.control;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.RectF;
import android.hardware.Camera;
import android.os.Handler;
import android.os.Message;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import net.phonex.R;
import net.phonex.camera.interfaces.FocusCallback;
import net.phonex.camera.interfaces.KeyEventsListener;
import net.phonex.camera.model.FocusMode;
import net.phonex.util.Log;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

/**
 * SurfaceView for displaying preview from camera.
 * <p/>
 * Created by Matus on 21-Jul-15.
 */
public class CameraPreview extends SurfaceView implements SurfaceHolder.Callback, Camera.AutoFocusCallback, Handler.Callback {

    private static final String TAG = "CameraPreview";

    // display orientation is set in the camera parameters to obtain correct aspect ratio
    private static final int DISPLAY_ORIENTATION = 90;

    // size of focus rectangle, 1 is length of shorter screen edge
    private static final float FOCUS_AREA_SIZE = 0.33f;
    // size of focus rectangle, 1 is length of shorter screen edge
    private static final float FOCUS_TOUCH_AREA_SIZE = 0.2f;

    private static final float STROKE_WIDTH = 1f;
    private static final float FOCUS_AREA_FULL_SIZE = 2000f;
    private static final int ACCURACY = 3;

    private static final int AUTOFOCUS_TIMED_CANCEL = 17482;

    private static final int AUTOFOCUS_TIMED_CANCEL_DELAY = 5000;

    private Activity activity;
    private Camera camera;

    private RelativeLayout focusLayout;
    private ImageView focusView;
    private FocusMode focusMode = FocusMode.AUTO;

    private boolean hasAutoFocus;
    private boolean focusing;
    private boolean focused;
    private float focusKoefW;
    private float focusKoefH;
    private float prevScaleFactor;
    private FocusCallback focusCallback;
    private Rect tapArea;
    private KeyEventsListener keyEventsListener;

    private SurfaceHolder oldHolder;
    private boolean surfaceCreated;
    private boolean takingPicture;

    private boolean previewRunning;

    private boolean previewEnabled;

    private Camera.Parameters parameters;

    public CameraPreview(Activity activity, Camera camera, Camera.Parameters parameters, RelativeLayout focusLayout, FocusCallback focusCallback, KeyEventsListener keyEventsListener) {
        super(activity);
        this.activity = activity;
        this.camera = camera;
        this.parameters = parameters;
        this.focusLayout = focusLayout;
        this.focusCallback = focusCallback;
        this.keyEventsListener = keyEventsListener;

        List<String> supportedFocusModes = parameters.getSupportedFocusModes();
        hasAutoFocus = supportedFocusModes != null && supportedFocusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO);
    }

    public void surfaceCreated(SurfaceHolder holder) {
        Log.d(TAG, "surfaceCreated");
        surfaceCreated = true;
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.d(TAG, "surfaceDestroyed");
        surfaceCreated = false;
        // we will set as callback when surface holder is initialized again
        holder.removeCallback(this);
        stopPreview();
        oldHolder = null;
        if (focusCallback != null) {
            camera = null;
            focusCallback.onSurfaceDestroyed();
        } else {
            camera.release();
            camera = null;
        }
    }

    public void enablePreview() {
        Log.d(TAG, "enablePreview()");
        if (previewEnabled) {
            return;
        }
        previewEnabled = true;
        restartPreview();
    }

    public void disablePreview() {
        if (!previewEnabled) {
            return;
        }
        previewEnabled = false;
        stopPreview();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if (camera == null) {
            Log.d(TAG, "surfaceChanged() camera == null");
            return;
        }
        oldHolder = null;
        Log.df(TAG, "surfaceChanged(%1d, %2d)", width, height);
        // If your preview can change or rotate, take care of those events here.
        // Make sure to stop the preview before resizing or reformatting it.
        initFocusKoefs(width, height);
        if (holder.getSurface() == null) {
            // preview surface does not exist
            return;
        }
        // start preview if not running
        startPreview(holder);
        setOnTouchListener(new CameraTouchListener());
        if (focusCallback != null) {
            focusCallback.onCameraReady();
        }
    }

    private void initFocusKoefs(float width, float height) {
        focusKoefW = width / FOCUS_AREA_FULL_SIZE;
        focusKoefH = height / FOCUS_AREA_FULL_SIZE;
    }

    public void setFocusMode(FocusMode focusMode) {
        clearCameraFocus();
        this.focusMode = focusMode;
        focusing = false;
        setOnTouchListener(new CameraTouchListener());
    }

    private void startFocusing() {
        Log.d(TAG, "startFocusing() " + focusing);
        if (!focusing) {
            focused = false;
            focusing = true;
            if (focusMode == FocusMode.AUTO || (focusMode == FocusMode.TOUCH && tapArea == null)) {
                drawFocusFrame(createAutoFocusRect(), FocusState.FOCUSING);
            }
            camera.autoFocus(this);
        }
    }

    public void takePicture() {
        takingPicture = true;
        if (hasAutoFocus) {
            if (focusMode == FocusMode.AUTO) {
                startFocusing();
            }
            if (focusMode == FocusMode.TOUCH) {
                Log.d(TAG, "focusing == " + focusing + " focused == " + focused + " (tapArea == null) == " + (tapArea == null));
                if (focusing && tapArea != null) {
                    Log.d(TAG, "touch focus was called and user tried to take photo before focused");
                    camera.cancelAutoFocus();
                    focused();
                } else if (focused && tapArea != null) {
                    focused();
                } else {
                    startFocusing();
                }
            }
        } else {
            focused();
        }
    }

    private Rect createAutoFocusRect() {
        int left = (int) (getWidth() * (1 - FOCUS_AREA_SIZE) / 2);
        int right = getWidth() - left;
        int top = (int) (getHeight() * (1 - FOCUS_AREA_SIZE) / 2);
        int bottom = getHeight() - right;
        return new Rect(left, top, right, bottom);
    }

    private void startPreview(SurfaceHolder holder) {
        if (holder == null || holder.getSurface() == null) {
            Log.d(TAG, "startPreview, with null holder");
            return;
        }
        if (previewRunning) {
            Log.d(TAG, "startPreview, but preview running");
            return;
        }
        if (!previewEnabled) {
            Log.d(TAG, "startPreview, but preview not enabled");
            return;
        }
        Log.d(TAG, "startPreview");
        try {
            camera.setPreviewDisplay(holder);
        } catch (IOException e) {
            Log.ef(TAG, "Error starting camera preview: ", e);
        }
        oldHolder = holder;
        camera.setDisplayOrientation(DISPLAY_ORIENTATION);
        if (hasAutoFocus && !Camera.Parameters.FOCUS_MODE_AUTO.equals(parameters.getFocusMode())) {
            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
            camera.setParameters(parameters);
        }
        // this is the longest operation
        Log.df(TAG, "Starting preview");
        try {
            camera.startPreview();
            previewRunning = true;
        } catch (Exception ex) {
            Log.e(TAG, "Starting preview failed", ex);
        }
        Log.df(TAG, "Started preview");
    }

    private void restartPreview() {
        if (oldHolder != null && oldHolder.getSurface() != null) {
            Log.d(TAG, "restartPreview()");
            startPreview(oldHolder);
            Log.d(TAG, "restartPreview() - done");
            if (focusCallback != null) {
                focusCallback.onCameraReady();
            }
        } else {
            // holder is not yet usable for starting preview
            // preview will be started on holder's surfaceCreated callback
            // this is a long operation

            // Install a SurfaceHolder.Callback so we get notified when the
            // underlying surface is created and destroyed.
            Log.d(TAG, "restartPreview(), initializing surface holder");
            SurfaceHolder holder = getHolder();
            Log.d(TAG, "restartPreview(), initialized surface holder");
            if (holder != null) {
                holder.addCallback(this);
                holder.setKeepScreenOn(true);
                oldHolder = holder;
            } else {
                Log.d(TAG, "null holder from getHolder()");
            }
        }
    }

    private void stopPreview() {
        if (!previewRunning) {
            return;
        }
        if (camera == null) {
            Log.d(TAG, "stopPreview(), camera == null");
            return;
        }
        Log.d(TAG, "stopPreview()");
        try {
            if (focusCallback != null) {
                focusCallback.onCameraBusy();
            }
            Log.d(TAG, "stopPreview() Stopping preview");
            previewRunning = false;
            camera.stopPreview();
            Log.d(TAG, "stopPreview() Stopped preview");
        } catch (Exception e) {
            Log.e(TAG, "Error stopping camera preview: ", e);
        }
    }

    private enum FocusState {
        FOCUSING, // focusing in progress
        FOCUSED, // focused successfully
        OUT_OF_FOCUS // focusing failed
    }

    private void drawFocusFrame(Rect rect, FocusState focusState) {
        if (focusView == null) {
            // Always use the same ImageView, only apply tint. Reduces memory usage.
            focusView = new ImageView(activity);
            focusView.setImageResource(R.drawable.svg_focusing);
        } else {
            focusLayout.removeAllViews();
        }
        switch (focusState) {
            case FOCUSING:
                // white color over the shape of focusing image
                focusView.setColorFilter(Color.argb(255, 255, 255, 255), PorterDuff.Mode.SRC_ATOP);
                break;
            case FOCUSED:
                // green color over the shape of focusing image
                focusView.setColorFilter(Color.argb(255, 0, 200, 0), PorterDuff.Mode.SRC_ATOP);
                break;
            case OUT_OF_FOCUS:
                // red color over the shape of focusing image
                focusView.setColorFilter(Color.argb(255, 200, 0, 0), PorterDuff.Mode.SRC_ATOP);
                break;
        }
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(rect.right - rect.left, rect.bottom - rect.top);
        params.leftMargin = rect.left;
        params.topMargin = rect.top;
        focusLayout.addView(focusView, params);
    }

    private void clearCameraFocus() {
        if (hasAutoFocus && surfaceCreated) {
            focused = false;
            camera.cancelAutoFocus();
            if (focusLayout != null) {
                tapArea = null;
                try {
                    parameters.setFocusAreas(null);
                    parameters.setMeteringAreas(null);
                    camera.setParameters(parameters);
                } catch (Exception e) {
                    Log.e(TAG, "clearCameraFocus", e);
                } finally {
                    if (focusLayout != null) {
                        focusLayout.removeAllViews();
                    }
                }
            }
        }
    }

    @Override
    public void onAutoFocus(boolean success, Camera camera) {
        focusing = false;
        focused = true;

        if (tapArea == null) {
            drawFocusFrame(createAutoFocusRect(), success ? FocusState.FOCUSED : FocusState.OUT_OF_FOCUS);
        } else {
            drawFocusFrame(tapArea, success ? FocusState.FOCUSED : FocusState.OUT_OF_FOCUS);
        }

        if (focusMode == FocusMode.AUTO) {
            focused();
        }
        if (focusMode == FocusMode.TOUCH && tapArea == null) {
            focused();
        }

        if (focusMode == FocusMode.TOUCH && tapArea != null) {
            // cancel this focus after some time
            Handler focusCancelHandler = new Handler(this);
            Message message = Message.obtain(focusCancelHandler, AUTOFOCUS_TIMED_CANCEL);
            focusCancelHandler.sendMessageDelayed(message, AUTOFOCUS_TIMED_CANCEL_DELAY);
        }
    }

    private void focused() {
        focusing = false;
        if (focusCallback != null) {
            focusCallback.onFocused(camera);
        }
    }

    public void onPictureTaken() {
        takingPicture = false;
        clearCameraFocus();
    }

    protected void focusOnTouch(MotionEvent event) {
        if (focused) clearCameraFocus();
        tapArea = calculateTapArea(event.getX(), event.getY());
        int maxFocusAreas = parameters.getMaxNumFocusAreas();
        if (maxFocusAreas > 0) {
            Camera.Area area = new Camera.Area(convert(tapArea), 100);
            parameters.setFocusAreas(Arrays.asList(area));
        }
        maxFocusAreas = parameters.getMaxNumMeteringAreas();
        if (maxFocusAreas > 0) {
            Rect rectMetering = calculateTapArea(event.getX(), event.getY());
            Camera.Area area = new Camera.Area(convert(rectMetering), 100);
            parameters.setMeteringAreas(Arrays.asList(area));
        }
        camera.setParameters(parameters);
        drawFocusFrame(tapArea, FocusState.FOCUSING);
        startFocusing();
    }

    /**
     * Convert touch position x:y to {@link android.hardware.Camera.Area} position -1000:-1000 to 1000:1000.
     */
    private Rect calculateTapArea(float x, float y) {
        int areaSize = getWidth() < getHeight()
                ? Float.valueOf(FOCUS_TOUCH_AREA_SIZE * getWidth()).intValue()
                : Float.valueOf(FOCUS_TOUCH_AREA_SIZE * getHeight()).intValue();

        int left = clamp((int) x - areaSize / 2, 0, getWidth() - areaSize);
        int top = clamp((int) y - areaSize / 2, 0, getHeight() - areaSize);

        RectF rect = new RectF(left, top, left + areaSize, top + areaSize);
        Log.d(TAG, "tap: " + rect.toShortString());

        return round(rect);
    }

    private Rect round(RectF rect) {
        return new Rect(Math.round(rect.left), Math.round(rect.top), Math.round(rect.right), Math.round(rect.bottom));
    }

    private Rect convert(Rect rect) {
        Rect result = new Rect();

        result.top = normalize(rect.top / focusKoefH - 1000);
        result.left = normalize(rect.left / focusKoefW - 1000);
        result.right = normalize(rect.right / focusKoefW - 1000);
        result.bottom = normalize(rect.bottom / focusKoefH - 1000);
        Log.d(TAG, "convert: " + result.toShortString());

        return result;
    }

    private int normalize(float value) {
        if (value > 1000) {
            return 1000;
        }
        if (value < -1000) {
            return -1000;
        }
        return Math.round(value);
    }

    private int clamp(int x, int min, int max) {
        if (x > max) {
            return max;
        }
        if (x < min) {
            return min;
        }
        return x;
    }

    private void scale(float scaleFactor) {
        scaleFactor = BigDecimal.valueOf(scaleFactor).setScale(ACCURACY, BigDecimal.ROUND_HALF_UP).floatValue();
        if (Float.compare(scaleFactor, 1.0f) == 0 || Float.compare(scaleFactor, prevScaleFactor) == 0) {
            return;
        }
        if (scaleFactor > 1f) {
            keyEventsListener.zoomIn();
        }
        if (scaleFactor < 1f) {
            keyEventsListener.zoomOut();
        }
        prevScaleFactor = scaleFactor;
    }

    @Override
    public boolean handleMessage(Message message) {
        if (message != null && message.what == AUTOFOCUS_TIMED_CANCEL) {
            if (focused) {
                Log.d(TAG, "Touched autofocus timed cancel");
                clearCameraFocus();
            }
            return true;
        }
        return false;
    }

    private class CameraTouchListener implements OnTouchListener {

        private ScaleGestureDetector mScaleDetector = new ScaleGestureDetector(activity, new ScaleListener());
        private GestureDetector mTapDetector = new GestureDetector(activity, new TapListener());

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            if (takingPicture) return true;

            if (event.getPointerCount() > 1) {
                mScaleDetector.onTouchEvent(event);
                return true;
            }
            if (hasAutoFocus && focusMode == FocusMode.TOUCH) {
                mTapDetector.onTouchEvent(event);
                return true;
            }
            return true;
        }

        private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {

            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                scale(detector.getScaleFactor());
                return true;
            }

        }

        private class TapListener extends GestureDetector.SimpleOnGestureListener {

            @Override
            public boolean onSingleTapConfirmed(MotionEvent event) {
                if (takingPicture) return true;
                focusOnTouch(event);
                return true;
            }
        }
    }
}
