package net.phonex.camera.control;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.ImageView;

/**
 * Created by Matus on 21-Jul-15.
 */
public class PinchImageView extends PreviewImageView {

    // We can be in one of these 3 states
    private static final int NONE = 0;
    private static final int DRAG = 1;
    private static final int ZOOM = 2;
    private static final int CLICK = 3;
    private final float minScale = 1f;
    private int mode = NONE;
    private Matrix matrix = new Matrix();
    // Remember some things for zooming
    private PointF last = new PointF();
    private PointF start = new PointF();
    private float maxScale = 3f;
    private float[] m;
    private float redundantXSpace, redundantYSpace;
    private float width, height;
    private float saveScale = 1f;
    private float right, bottom, origWidth, origHeight, bmWidth, bmHeight;

    private ScaleGestureDetector mScaleDetector;
    private float angle;

    public PinchImageView(Context context) {
        super(context);
        sharedConstructing(context);
    }

    public PinchImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        sharedConstructing(context);
    }

    public void postRotate(float degrees) {
        angle += degrees + 360;
        angle %= 360;
    }

    public void setRotation(float degrees) {
        angle = degrees;
        angle %= 360;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.rotate(angle, canvas.getWidth() / 2, canvas.getHeight() / 2);
        super.onDraw(canvas);
    }

    private void sharedConstructing(Context context) {
        super.setClickable(true);

        mScaleDetector = new ScaleGestureDetector(context, new ScaleListener());
        matrix.setTranslate(minScale, minScale);
        m = new float[9];
        setImageMatrix(matrix);
        setScaleType(ImageView.ScaleType.MATRIX);

        setOnTouchListener(new View.OnTouchListener() {

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                mScaleDetector.onTouchEvent(event);

                matrix.getValues(m);
                float x = m[Matrix.MTRANS_X];
                float y = m[Matrix.MTRANS_Y];

                float motionX = event.getX();
                float motionY = event.getY();

                switch ((int) angle) {
                    case 90:
                        motionX = event.getY();
                        motionY = -event.getX();
                        break;
                    case 180:
                        motionX = -event.getX();
                        motionY = -event.getY();
                        break;
                    case 270:
                        motionX = -event.getY();
                        motionY = event.getX();
                        break;
                }

                PointF curr = new PointF(motionX, motionY);

                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        last.set(motionX, motionY);
                        start.set(last);
                        mode = DRAG;
                        break;
                    case MotionEvent.ACTION_MOVE:
                        if (mode == DRAG) {
                            float deltaX = curr.x - last.x;
                            float deltaY = curr.y - last.y;
                            float scaleWidth = Math.round(origWidth * saveScale);
                            float scaleHeight = Math.round(origHeight * saveScale);
                            if (scaleWidth < width) {
                                deltaX = 0;
                                if (y + deltaY > 0)
                                    deltaY = -y;
                                else if (y + deltaY < -bottom)
                                    deltaY = -(y + bottom);

                            } else if (scaleHeight < height) {
                                deltaY = 0;
                                if (x + deltaX > 0)
                                    deltaX = -x;
                                else if (x + deltaX < -right)
                                    deltaX = -(x + right);

                            } else {
                                if (x + deltaX > 0)
                                    deltaX = -x;
                                else if (x + deltaX < -right)
                                    deltaX = -(x + right);

                                if (y + deltaY > 0)
                                    deltaY = -y;
                                else if (y + deltaY < -bottom)
                                    deltaY = -(y + bottom);

                            }
                            matrix.postTranslate(deltaX, deltaY);
                            last.set(curr.x, curr.y);
                        }
                        break;

                    case MotionEvent.ACTION_UP:
                        mode = NONE;
                        int xDiff = (int) Math.abs(curr.x - start.x);
                        int yDiff = (int) Math.abs(curr.y - start.y);
                        if (xDiff < CLICK && yDiff < CLICK) {
                            performClick();
                        }
                        break;

                    case MotionEvent.ACTION_POINTER_UP:
                        mode = NONE;
                        break;
                }
                setImageMatrix(matrix);
                invalidate();
                // indicate event was handled
                return true;
            }

        });
    }

    @Override
    public void setImageBitmap(Bitmap bm) {
        super.setImageBitmap(bm);
        if (bm != null) {
            bmWidth = bm.getWidth();
            bmHeight = bm.getHeight();
        }
    }

    @Override
    public void setImageDrawable(Drawable drawable) {
        super.setImageDrawable(drawable);
        if (drawable == null) {
            return;
        }

        if (drawable instanceof BitmapDrawable) {
            BitmapDrawable bitmapDrawable = (BitmapDrawable) drawable;
            if (bitmapDrawable.getBitmap() != null) {
                bmWidth = bitmapDrawable.getBitmap().getWidth();
                bmHeight = bitmapDrawable.getBitmap().getHeight();
            }
        } else {
            int intrinsicHeight = drawable.getIntrinsicHeight();
            int intrinsicWidth = drawable.getIntrinsicWidth();
            bmHeight = intrinsicHeight > 0 ? intrinsicHeight : 0;
            bmWidth = intrinsicWidth > 0 ? intrinsicWidth : 0;
        }
    }

    public void setMaxZoom(float x) {
        maxScale = x;
    }

    private void scale(float scaleFactor, float focusX, float focusY) {
        float origScale = saveScale;
        saveScale *= scaleFactor;
        if (saveScale > maxScale) {
            saveScale = maxScale;
            scaleFactor = maxScale / origScale;
        } else if (saveScale < minScale) {
            saveScale = minScale;
            scaleFactor = minScale / origScale;
        }
        right = width * saveScale - width - (2 * redundantXSpace * saveScale);
        bottom = height * saveScale - height - (2 * redundantYSpace * saveScale);
        if (origWidth * saveScale <= width || origHeight * saveScale <= height) {
            matrix.postScale(scaleFactor, scaleFactor, width / 2, height / 2);
            if (scaleFactor < 1) {
                matrix.getValues(m);
                float x = m[Matrix.MTRANS_X];
                float y = m[Matrix.MTRANS_Y];
                if (scaleFactor < 1) {
                    if (Math.round(origWidth * saveScale) < width) {
                        if (y < -bottom)
                            matrix.postTranslate(0, -(y + bottom));
                        else if (y > 0)
                            matrix.postTranslate(0, -y);
                    } else {
                        if (x < -right)
                            matrix.postTranslate(-(x + right), 0);
                        else if (x > 0)
                            matrix.postTranslate(-x, 0);
                    }
                }
            }
        } else {
            matrix.postScale(scaleFactor, scaleFactor, focusX, focusY);
            matrix.getValues(m);
            float x = m[Matrix.MTRANS_X];
            float y = m[Matrix.MTRANS_Y];
            if (scaleFactor < 1) {
                if (x < -right)
                    matrix.postTranslate(-(x + right), 0);
                else if (x > 0)
                    matrix.postTranslate(-x, 0);
                if (y < -bottom)
                    matrix.postTranslate(0, -(y + bottom));
                else if (y > 0)
                    matrix.postTranslate(0, -y);
            }
        }
    }

    public boolean canScrollHorizontally(int direction) {
        matrix.getValues(m);
        float x = Math.abs(m[Matrix.MTRANS_X]);
        float scaleWidth = Math.round(origWidth * saveScale);

        if (scaleWidth < width) {
            return false;

        } else {
            if (x - direction <= 0)
                return false; // reach left edge
            else if (x + width - direction >= scaleWidth)
                return false; // reach right edge

            return true;
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // this is a quick fix, the image view is always square
        // if image is taken in landscape or portrait, it will cover full screen
        // FIXME - if rotated, image will be cropped
        if (widthMeasureSpec > heightMeasureSpec) heightMeasureSpec = widthMeasureSpec;
        if (heightMeasureSpec > widthMeasureSpec) widthMeasureSpec = heightMeasureSpec;
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        width = View.MeasureSpec.getSize(widthMeasureSpec);
        height = View.MeasureSpec.getSize(heightMeasureSpec);
        locateImage();
    }

    protected void locateImage() {
        if ((Float.compare(bmWidth, 0) * Float.compare(bmHeight, 0)) == 0) {
            return;
        }

        // Fit to screen.
        float scale;
        float scaleX = width / bmWidth;
        float scaleY = height / bmHeight;
        scale = Math.min(scaleX, scaleY);
        matrix.setScale(scale, scale);
        setImageMatrix(matrix);
        saveScale = 1f;

        // Center the image
        redundantYSpace = height - (scale * bmHeight);
        redundantXSpace = width - (scale * bmWidth);
        redundantYSpace /= 2;
        redundantXSpace /= 2;

        matrix.postTranslate(redundantXSpace, redundantYSpace);

        origWidth = width - 2 * redundantXSpace;
        origHeight = height - 2 * redundantYSpace;
        right = width * saveScale - width - (2 * redundantXSpace * saveScale);
        bottom = height * saveScale - height - (2 * redundantYSpace * saveScale);
        setImageMatrix(matrix);
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {

        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            mode = ZOOM;
            return true;
        }

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            scale(detector.getScaleFactor(), detector.getFocusX(), detector.getFocusY());
            return true;
        }

    }
}
