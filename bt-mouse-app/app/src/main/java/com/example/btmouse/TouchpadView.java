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
    }

    private float lastX;
    private float lastY;
    private float downX;
    private float downY;
    private float accumulatedX;
    private float accumulatedY;

    private long downTime;
    private boolean moved;

    private OnTouchpadListener listener;
    private final float sensitivity = 1.5f;
    private final float touchSlop;

    public TouchpadView(Context context) {
        this(context, null);
    }

    public TouchpadView(Context context, AttributeSet attrs) {
        super(context, attrs);
        touchSlop =
                ViewConfiguration
                        .get(context)
                        .getScaledTouchSlop();
    }

    public void setOnTouchpadListener(
            OnTouchpadListener listener
    ) {
        this.listener = listener;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();

        if (action == MotionEvent.ACTION_DOWN) {
            lastX = event.getX();
            lastY = event.getY();
            downX = lastX;
            downY = lastY;
            downTime = System.currentTimeMillis();
            accumulatedX = 0;
            accumulatedY = 0;
            moved = false;
            return true;
        }

        if (action == MotionEvent.ACTION_MOVE) {
            float currentX = event.getX();
            float currentY = event.getY();

            float deltaFromDownX = currentX - downX;
            float deltaFromDownY = currentY - downY;
            float distance =
                    (float) Math.sqrt(
                            deltaFromDownX * deltaFromDownX
                                    + deltaFromDownY * deltaFromDownY
                    );

            if (distance > touchSlop) {
                moved = true;
            }

            float dx =
                    (currentX - lastX) / sensitivity;
            float dy =
                    (currentY - lastY) / sensitivity;

            accumulatedX += dx;
            accumulatedY += dy;

            int sendX = (int) accumulatedX;
            int sendY = (int) accumulatedY;

            if (
                    (sendX != 0 || sendY != 0)
                            && listener != null
            ) {
                listener.onMove(sendX, sendY);
                accumulatedX -= sendX;
                accumulatedY -= sendY;
            }

            lastX = currentX;
            lastY = currentY;
            return true;
        }

        if (
                action == MotionEvent.ACTION_UP
                        || action == MotionEvent.ACTION_CANCEL
        ) {
            if (
                    action == MotionEvent.ACTION_UP
                            && !moved
            ) {
                long duration =
                        System.currentTimeMillis() - downTime;

                float dx = event.getX() - downX;
                float dy = event.getY() - downY;
                float distance =
                        (float) Math.sqrt(
                                dx * dx + dy * dy
                        );

                if (
                        duration < 500
                                && distance <= touchSlop
                ) {
                    if (listener != null) {
                        listener.onLeftClick();
                    }
                }
            }

            return true;
        }

        return super.onTouchEvent(event);
    }
}