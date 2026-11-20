package com.example.btmouse;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

public class TouchpadView extends View {

    public interface OnTouchpadListener {
        void onMove(int dx, int dy);
    }

    private float lastX, lastY;
    private OnTouchpadListener listener;
    private final float SENSITIVITY = 1.5f; // Semakin kecil, semakin cepat mouse bergerak

    public TouchpadView(Context context) { super(context); }
    public TouchpadView(Context context, AttributeSet attrs) { super(context, attrs); }

    public void setOnTouchpadListener(OnTouchpadListener l) { listener = l; }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                lastX = event.getX();
                lastY = event.getY();
                return true;
            case MotionEvent.ACTION_MOVE:
                float dx = (event.getX() - lastX) / SENSITIVITY;
                float dy = (event.getY() - lastY) / SENSITIVITY;

                // HID Mouse maksimal bergerak -127 sampai 127 per report
                int finalDx = (int) Math.max(-127, Math.min(127, dx));
                int finalDy = (int) Math.max(-127, Math.min(127, dy));

                if (finalDx != 0 || finalDy != 0) {
                    if (listener != null) listener.onMove(finalDx, finalDy);
                    lastX = event.getX();
                    lastY = event.getY();
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                return true;
        }
        return super.onTouchEvent(event);
    }
}
