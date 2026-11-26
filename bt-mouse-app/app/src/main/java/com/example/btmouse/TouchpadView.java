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

    private static final float MOVE_SENSITIVITY = 1.5f;

    private final float touchSlop;
    private final float scrollThreshold;
    private final float zoomThreshold;

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
    private boolean rightClickSent;

    public TouchpadView(Context context) {
        this(context, null);
    }

    public TouchpadView(Context context, AttributeSet attrs) {
        super(context, attrs);

        float density = getResources().getDisplayMetrics().density;

        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        scrollThreshold = 30f * density;
        zoomThreshold = 24f * density;

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
                downX = event.getX(0);
                downY = event.getY(0);
                lastX = downX;
                lastY = downY;
                downTime = System.currentTimeMillis();

                accumulatedX = 0f;
                accumulatedY = 0f;
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
                    rightClickSent = false;

                    scrollAccumulator = 0f;
                    zoomAccumulator = 0f;

                    lastAverageY = getAverageY(event);
                    lastPinchDistance = getPointerDistance(event);
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
                // Saat dua jari diangkat tanpa gerakan, anggap sebagai klik kanan.
                if (event.getPointerCount() == 2
                        && twoFingerGesture
                        && !twoFingerMoved
                        && !rightClickSent) {
                    rightClickSent = true;

                    if (listener != null) {
                        listener.onRightClick();
                    }
                }
                return true;

            case MotionEvent.ACTION_UP:
                // Tap satu jari tanpa gerakan = klik kiri.
                if (!twoFingerGesture && !moved) {
                    long duration =
                            System.currentTimeMillis() - downTime;

                    float dx = event.getX(0) - downX;
                    float dy = event.getY(0) - downY;
                    float distance =
                            (float) Math.sqrt(dx * dx + dy * dy);

                    if (duration < 500 && distance <= touchSlop * 1.5f) {
                        if (listener != null) {
                            listener.onLeftClick();
                        }
                    }
                }
                return true;

            case MotionEvent.ACTION_CANCEL:
                resetGestureState();
                return true;

            default:
                return true;
        }
    }

    private void handleOneFingerMove(MotionEvent event) {
        float currentX = event.getX(0);
        float currentY = event.getY(0);

        float fromDownX = currentX - downX;
        float fromDownY = currentY - downY;
        float distanceFromDown =
                (float) Math.sqrt(
                        fromDownX * fromDownX
                                + fromDownY * fromDownY
                );

        if (distanceFromDown > touchSlop) {
            moved = true;
        }

        float deltaX = (currentX - lastX) / MOVE_SENSITIVITY;
        float deltaY = (currentY - lastY) / MOVE_SENSITIVITY;

        accumulatedX += deltaX;
        accumulatedY += deltaY;

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

    private void handleTwoFingerMove(MotionEvent event) {
        float currentDistance = getPointerDistance(event);
        float currentAverageY = getAverageY(event);
        float distanceChange = currentDistance - lastPinchDistance;

        // Jika jarak antarjari berubah, pilih gesture pinch/zoom.
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
            // Jika jarak jari tidak berubah, gerakan vertikal menjadi scroll.
            float deltaY = currentAverageY - lastAverageY;

            if (Math.abs(deltaY) > 1f) {
                twoFingerMoved = true;
                scrollAccumulator += deltaY;

                while (Math.abs(scrollAccumulator) >= scrollThreshold) {
                    // Dua jari naik = scroll ke atas.
                    int direction = scrollAccumulator < 0f ? 1 : -1;

                    if (listener != null) {
                        listener.onScroll(direction);
                    }

                    scrollAccumulator -= direction * scrollThreshold;
                }
            }
        }

        lastPinchDistance = currentDistance;
        lastAverageY = currentAverageY;
    }

    private float getAverageY(MotionEvent event) {
        return (event.getY(0) + event.getY(1)) / 2f;
    }

    private float getPointerDistance(MotionEvent event) {
        float dx = event.getX(1) - event.getX(0);
        float dy = event.getY(1) - event.getY(0);

        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private void resetGestureState() {
        moved = false;
        twoFingerGesture = false;
        twoFingerMoved = false;
        pinchStarted = false;
        rightClickSent = false;
        accumulatedX = 0f;
        accumulatedY = 0f;
        scrollAccumulator = 0f;
        zoomAccumulator = 0f;
    }
}