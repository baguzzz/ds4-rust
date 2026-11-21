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
    private float accumX = 0, accumY = 0; // Menampung pecahan desimal
    private OnTouchpadListener listener;
    private final float SENSITIVITY = 1.5f;

    public TouchpadView(Context context) { super(context); }
    public TouchpadView(Context context, AttributeSet attrs) { super(context, attrs); }

    public void setOnTouchpadListener(OnTouchpadListener l) { listener = l; }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                lastX = event.getX();
                lastY = event.getY();
                accumX = 0;
                accumY = 0;
                return true;
            case MotionEvent.ACTION_MOVE:
                float dx = (event.getX() - lastX) / SENSITIVITY;
                float dy = (event.getY() - lastY) / SENSITIVITY;

                accumX += dx;
                accumY += dy;

                int finalDx = (int) accumX;
                int finalDy = (int) accumY;

                if (finalDx != 0 || finalDy != 0) {
                    if (listener != null) listener.onMove(finalDx, finalDy);
                    // Kurangi akumulasi dengan nilai yang sudah dikirim
                    accumX -= finalDx;
                    accumY -= finalDy;
                }
                
                lastX = event.getX();
                lastY = event.getY();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                return true;
        }
        return super.onTouchEvent(event);
    }
}
