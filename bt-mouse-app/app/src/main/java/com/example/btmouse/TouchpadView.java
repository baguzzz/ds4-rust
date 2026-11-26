package com.example.btmouse;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

public class TouchpadView extends View {

    public interface OnTouchpadListener {
        void onMove(int dx, int dy);
        void onLeftClick();
        void onRightClick();
        void onScroll(int direction);
        void onZoom(int direction);
    }

    private OnTouchpadListener listener;

    private float lastX;
    private float lastY;
    private float accumX;
    private float accumY;

    private float twoFingerLastY;
    private float lastPinchDistance;
    private float scrollAccumulator;
    private float zoomAccumulator;

    private float downX;
    private float downY;

    private boolean moved;
    private boolean twoFingerGesture;
    private boolean twoFingerMoved;
    private boolean pinchStarted;
    private boolean rightClickSent;

    private long downTime;

    private final float sensitivity = 1.5f;
    private final float touchSlop;
    private final float scrollThreshold;
    private final float zoomThreshold;

    public TouchpadView(Context context) {
        this(context, null);
    }

    public TouchpadView(Context context, AttributeSet attrs) {
        super(context, attrs);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        scrollThreshold = 30f * getResources().getDisplayMetrics().density;
        zoomThreshold = 24f * getResources().getDisplayMetrics().density;
        setClickable(true);
    }

    public void setOnTouchpadListener(OnTouchpadListener listener) {
        this.listener = listener;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                lastX = event.getX(0);
                lastY = event.getY(0);
                downX = lastX;
                downY = lastY;
                downTime = System.currentTimeMillis();

                accumX = 0f;
                accumY = 0f;
                scrollAccumulator = 0f;
                zoomAccumulator = 0f;

                moved = false;
                twoFingerGesture = false;
                twoFingerMoved = false;
                pinchStarted = false;
                rightClickSent = false;
                return true;

            case MotionEvent.ACTION_POINTER_DOWN:
                if (event.getPointerCount() >= 2) {
                    twoFingerGesture = true;
                    twoFingerMoved = false;
                    pinchStarted = false;
                    scrollAccumulator = 0f;
                    zoomAccumulator = 0f;

                    twoFingerLastY = averageY(event);
                    lastPinchDistance = distanceBetweenFirstTwoPointers(event);
                }
                return true;

            case MotionEvent.ACTION_MOVE:
                if (event.getPointerCount() >= 2) {
                    handleTwoFingerMove(event);
                } else if (!twoFingerGesture) {
                    handleOneFingerMove(event);
                }
                return true;

            case MotionEvent.ACTION_POINTER_UP:
                if (twoFingerGesture
                        && event.getPointerCount() == 2
                        && !twoFingerMoved
                        && !rightClickSent) {
                    rightClickSent = true;
                    if (listener != null) {
                        listener.onRightClick();
                    }
                }
                return true;

            case MotionEvent.ACTION_UP:
                if (!twoFingerGesture && !moved) {
                    long duration = System.currentTimeMillis() - downTime;
                    float distance = distance(downX, downY, event.getX(), event.getY());

                    if (duration < 500 && distance <= touchSlop * 1.5f) {
                        if (listener != null) {
                            listener.onLeftClick();
                        }
                    }
                }
                return true;

            case MotionEvent.ACTION_CANCEL:
                return true;

            default:
                return super.onTouchEvent(event);
        }
    }

    private void handleOneFingerMove(MotionEvent event) {
        float x = event.getX(0);
        float y = event.getY(0);

        if (distance(downX, downY, x, y) > touchSlop) {
            moved = true;
        }

        float dx = (x - lastX) / sensitivity;
        float dy = (y - lastY) / sensitivity;

        accumX += dx;
        accumY += dy;

        int sendX = (int) accumX;
        int sendY = (int) accumY;

        if ((sendX != 0 || sendY != 0) && listener != null) {
            listener.onMove(sendX, sendY);
            accumX -= sendX;
            accumY -= sendY;
        }

        lastX = x;
        lastY = y;
    }

    private void handleTwoFingerMove(MotionEvent event) {
        float currentDistance = distanceBetweenFirstTwoPointers(event);
        float currentAverageY = averageY(event);

        float distanceChange = currentDistance - lastPinchDistance;

        if (Math.abs(distanceChange) > 2f) {
            pinchStarted = true;
        }

        if (pinchStarted) {
            twoFingerMoved = true;
            zoomAccumulator += distanceChange;

            while (Math.abs(zoomAccumulator) >= zoomThreshold) {
                int direction = zoomAccumulator > 0f ? 1 : -1;
                if (listener != null) {
                    listener.onZoom(direction);
                }
                zoomAccumulator -= direction * zoomThreshold;
            }
        } else {
            float deltaY = currentAverageY - twoFingerLastY;

            if (Math.abs(deltaY) > 1f) {
                twoFingerMoved = true;
                scrollAccumulator += deltaY;

                while (Math.abs(scrollAccumulator) >= scrollThreshold) {
                    // Geser dua jari ke atas = scroll ke atas.
                    int direction = scrollAccumulator < 0f ? 1 : -1;
                    if (listener != null) {
                        listener.onScroll(direction);
                    }
                    scrollAccumulator -= direction * scrollThreshold;
                }
            }
        }

        lastPinchDistance = currentDistance;
        twoFingerLastY = currentAverageY;
    }

    private float averageY(MotionEvent event) {
        return (event.getY(0) + event.getY(1)) / 2f;
    }

    private float distanceBetweenFirstTwoPointers(MotionEvent event) {
        return distance(
                event.getX(0),
                event.getY(0),
                event.getX(1),
                event.getY(1)
        );
    }

    private float distance(float x1, float y1, float x2, float y2) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
}