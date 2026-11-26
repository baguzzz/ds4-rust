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

    private static final float ONE_FINGER_SENSITIVITY = 1.5f;

    private OnTouchpadListener listener;

    private float downX;
    private float downY;
    private float lastX;
    private float lastY;
    private float accumulatedX;
    private float accumulatedY;

    private float lastAverageY;
    private float lastPinchDistance;
    private float scrollAccumulator;
    private float zoomAccumulator;

    private long downTime;
    private boolean moved;
    private boolean twoFingerGesture;
    private boolean twoFingerMoved;
    private boolean pinchStarted;

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
    }

    public void setOnTouchpadListener(OnTouchpadListener listener) {
        this.listener = listener;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getX(0);
                downY = event.getY(0);
                lastX = downX;
                lastY = downY;
                downTime = System.currentTimeMillis();

                accumulatedX = 0;
                accumulatedY = 0;
                scrollAccumulator = 0;
                zoomAccumulator = 0;

                moved = false;
                twoFingerGesture = false;
                twoFingerMoved = false;
                pinchStarted = false;
                return true;

            case MotionEvent.ACTION_POINTER_DOWN:
                if (event.getPointerCount() >= 2) {
                    twoFingerGesture = true;
                    twoFingerMoved = false;
                    pinchStarted = false;
                    scrollAccumulator = 0;
                    zoomAccumulator = 0;
                    lastAverageY = averageY(event);
                    lastPinchDistance = pointerDistance(event);
                }
                return true;

            case MotionEvent.ACTION_MOVE:
                if (event.getPointerCount() >= 2) {
                    handleTwoFingerGesture(event);
                } else if (!twoFingerGesture) {
                    handleOneFingerMove(event);
                }
                return true;

            case MotionEvent.ACTION_POINTER_UP:
                if (event.getPointerCount() == 2
                        && twoFingerGesture
                        && !twoFingerMoved
                        && listener != null) {
                    listener.onRightClick();
                }
                return true;

            case MotionEvent.ACTION_UP:
                if (!twoFingerGesture && !moved) {
                    float dx = event.getX(0) - downX;
                    float dy = event.getY(0) - downY;
                    float distance = (float) Math.sqrt(dx * dx + dy * dy);
                    long duration = System.currentTimeMillis() - downTime;

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
        float currentX = event.getX(0);
        float currentY = event.getY(0);

        float startDx = currentX - downX;
        float startDy = currentY - downY;
        float startDistance =
                (float) Math.sqrt(startDx * startDx + startDy * startDy);

        if (startDistance > touchSlop) {
            moved = true;
        }

        float dx = (currentX - lastX) / ONE_FINGER_SENSITIVITY;
        float dy = (currentY - lastY) / ONE_FINGER_SENSITIVITY;

        accumulatedX += dx;
        accumulatedY += dy;

        int sendX = (int) accumulatedX;
        int sendY = (int) accumulatedY;

        if ((sendX != 0 || sendY != 0) && listener != null) {
            listener.onMove(sendX, sendY);
            accumulatedX -= sendX;
            accumulatedY -= sendY;
        }

        lastX = currentX;
        lastY = currentY;
    }

    private void handleTwoFingerGesture(MotionEvent event) {
        float distance = pointerDistance(event);
        float averageY = averageY(event);

        float distanceChange = distance - lastPinchDistance;

        if (Math.abs(distanceChange) > 2f) {
            pinchStarted = true;
        }

        if (pinchStarted) {
            twoFingerMoved = true;
            zoomAccumulator += distanceChange;

            while (Math.abs(zoomAccumulator) >= zoomThreshold) {
                int direction = zoomAccumulator > 0 ? 1 : -1;

                if (listener != null) {
                    listener.onZoom(direction);
                }

                zoomAccumulator -= direction * zoomThreshold;
            }
        } else {
            float deltaY = averageY - lastAverageY;

            if (Math.abs(deltaY) > 1f) {
                twoFingerMoved = true;
                scrollAccumulator += deltaY;

                while (Math.abs(scrollAccumulator) >= scrollThreshold) {
                    int direction = scrollAccumulator < 0 ? 1 : -1;

                    if (listener != null) {
                        listener.onScroll(direction);
                    }

                    scrollAccumulator -= direction * scrollThreshold;
                }
            }
        }

        lastPinchDistance = distance;
        lastAverageY = averageY;
    }

    private float averageY(MotionEvent event) {
        return (event.getY(0) + event.getY(1)) / 2f;
    }

    private float pointerDistance(MotionEvent event) {
        float dx = event.getX(1) - event.getX(0);
        float dy = event.getY(1) - event.getY(0);
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
}