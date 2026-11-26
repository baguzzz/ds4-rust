package com.example.btmouse;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

public class TouchpadView extends View {

    public interface OnTouchpadListener {
        void onMove(int dx, int dy);
    }

    private static final float SENSITIVITY = 1.5f;

    private float lastX;
    private float lastY;
    private float accumulatedX;
    private float accumulatedY;
    private OnTouchpadListener listener;

    public TouchpadView(Context context) {
        super(context);
    }

    public TouchpadView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setOnTouchpadListener(OnTouchpadListener listener) {
        this.listener = listener;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                lastX = event.getX();
                lastY = event.getY();
                accumulatedX = 0f;
                accumulatedY = 0f;
                return true;

            case MotionEvent.ACTION_MOVE:
                float currentX = event.getX();
                float currentY = event.getY();

                float dx = (currentX - lastX) / SENSITIVITY;
                float dy = (currentY - lastY) / SENSITIVITY;

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
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                accumulatedX = 0f;
                accumulatedY = 0f;
                return true;
        }

        return super.onTouchEvent(event);
    }
}