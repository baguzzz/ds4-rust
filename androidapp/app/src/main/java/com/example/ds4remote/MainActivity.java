package com.example.ds4remote;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends AppCompatActivity {

    private static final byte SYNC = (byte) 0xAA;
    private static final int PACKET_SIZE = 12;
    private static final int SEND_INTERVAL_MS = 16;

    private ImageView statusIcon, btnOpenSettings;
    private FrameLayout gamepadArea;

    private final AtomicBoolean isSending = new AtomicBoolean(true);
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    private boolean isEditMode = false;
    private SharedPreferences prefs;

    private volatile int buttons = 0;
    private volatile int lx = 128, ly = 128, rx = 128, ry = 128;
    private volatile int l2 = 0, r2 = 0;
    private volatile int dpad = 8;

    private static final int BTN_CROSS=0, BTN_CIRCLE=1, BTN_SQUARE=2, BTN_TRIANGLE=3, BTN_L1=4, BTN_R1=5,
            BTN_SHARE=6, BTN_OPTIONS=7, BTN_L3=8, BTN_R3=9, BTN_PS=10, BTN_TOUCHPAD=11;

    private final int[] allIds = {
        R.id.dpadUp, R.id.dpadDown, R.id.dpadLeft, R.id.dpadRight,
        R.id.btnY, R.id.btnX, R.id.btnA, R.id.btnB,
        R.id.btnL1, R.id.containerL2, R.id.btnR1, R.id.containerR2,
        R.id.btnShare, R.id.btnPS, R.id.btnOptions,
        R.id.leftStick, R.id.rightStick,
        R.id.btnL3, R.id.btnTouchpad, R.id.btnR3
    };

    private final ActivityResultLauncher<String[]> importLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            uri -> {
                if (uri != null) {
                    readLayoutFromUri(uri);
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("GamepadLayout", MODE_PRIVATE);
        gamepadArea = findViewById(R.id.gamepadArea);
        statusIcon = findViewById(R.id.statusIcon);
        btnOpenSettings = findViewById(R.id.btnOpenSettings);

        btnOpenSettings.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, SettingsActivity.class));
        });

        JoystickView leftStick = findViewById(R.id.leftStick);
        JoystickView rightStick = findViewById(R.id.rightStick);

        leftStick.setOnMoveListener((x, y) -> {
            if (isEditMode) return;
            lx = clamp255((x + 1f) / 2f * 255f);
            ly = clamp255((y + 1f) / 2f * 255f);
        });
        rightStick.setOnMoveListener((x, y) -> {
            if (isEditMode) return;
            rx = clamp255((x + 1f) / 2f * 255f);
            ry = clamp255((y + 1f) / 2f * 255f);
        });

        applyEditTouchOnly(leftStick);
        applyEditTouchOnly(rightStick);

        setupButton(R.id.btnA, BTN_CROSS);
        setupButton(R.id.btnB, BTN_CIRCLE);
        setupButton(R.id.btnX, BTN_SQUARE);
        setupButton(R.id.btnY, BTN_TRIANGLE);
        setupButton(R.id.btnL1, BTN_L1);
        setupButton(R.id.btnR1, BTN_R1);
        setupButton(R.id.btnShare, BTN_SHARE);
        setupButton(R.id.btnOptions, BTN_OPTIONS);
        setupButton(R.id.btnL3, BTN_L3);
        setupButton(R.id.btnR3, BTN_R3);
        setupButton(R.id.btnPS, BTN_PS);
        setupButton(R.id.btnTouchpad, BTN_TOUCHPAD);

        setupTriggerSlider(R.id.btnL2, R.id.containerL2, true);
        setupTriggerSlider(R.id.btnR2, R.id.containerR2, false);

        setupDpad(R.id.dpadUp, 0);
        setupDpad(R.id.dpadRight, 2);
        setupDpad(R.id.dpadDown, 4);
        setupDpad(R.id.dpadLeft, 6);

        gamepadArea.post(this::loadLayout);
        startSendLoop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatusIcon();
        // Jika baru balik dari Settings setelah Reset, muat ulang layout
        if (ConnectionManager.getInstance().isConnected()) {
            statusIcon.setImageResource(R.drawable.ic_wifi_on);
        } else {
            statusIcon.setImageResource(R.drawable.ic_wifi_off);
        }
    }

    private void updateStatusIcon() {
        if (ConnectionManager.getInstance().isConnected()) {
            statusIcon.setImageResource(R.drawable.ic_wifi_on);
        } else {
            statusIcon.setImageResource(R.drawable.ic_wifi_off);
        }
    }

    private void setupTriggerSlider(int seekBarId, int containerId, boolean isL2) {
        SeekBar sb = findViewById(seekBarId);
        View container = findViewById(containerId);
        
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (isEditMode) return;
                int val = (int) ((progress / 100.0) * 255);
                if (isL2) l2 = val;
                else r2 = val;
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        sb.setOnTouchListener((v, event) -> {
            if (isEditMode) return false;
            if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                ObjectAnimator animator = ObjectAnimator.ofInt(sb, "progress", sb.getProgress(), 0);
                animator.setDuration(150);
                animator.start();
            }
            return false;
        });

        applyEditTouchOnly(container);
    }

    private void startSendLoop() {
        new Thread(() -> {
            while (isSending.get()) {
                if (ConnectionManager.getInstance().isConnected()) {
                    try { 
                        sendPacket(); 
                    } catch (IOException ex) { 
                        ConnectionManager.getInstance().disconnect(); 
                        uiHandler.post(this::updateStatusIcon);
                    }
                }
                try { Thread.sleep(SEND_INTERVAL_MS); } catch (InterruptedException e) { return; }
            }
        }).start();
    }

    private void sendPacket() throws IOException {
        OutputStream out = ConnectionManager.getInstance().getOutput();
        if (out == null) return;
        
        byte[] p = new byte[PACKET_SIZE];
        p[0] = SYNC;
        p[1] = (byte)(buttons & 0xFF);
        p[2] = (byte)((buttons >> 8) & 0xFF);
        p[3] = (byte) lx; p[4] = (byte) ly;
        p[5] = (byte) rx; p[6] = (byte) ry;
        p[7] = (byte) l2; p[8] = (byte) r2;
        p[9] = (byte) dpad;
        p[10] = 0;
        byte ck = 0;
        for (int i = 1; i < PACKET_SIZE - 1; i++) ck ^= p[i];
        p[PACKET_SIZE - 1] = ck;
        
        out.write(p);
    }

    // --- Sisa fungsi UI (Button, Dpad, Edit, Save/Load) sama seperti sebelumnya ---
    // (Copy semua fungsi setupButton, setupDpad, applyEdit..., loadLayout, saveLayout, dll di sini)
    
    private static int clamp255(float v) { return (int) Math.max(0, Math.min(255, v)); }

    private void setupButton(int id, int bitIdx) {
        Button b = findViewById(id);
        applyEditAndGamepadTouch(b, () -> { if (!isEditMode) buttons |= (1 << bitIdx); }, () -> { if (!isEditMode) buttons &= ~(1 << bitIdx); });
    }
    private void setupDpad(int id, int dpadValue) {
        View b = findViewById(id);
        applyEditAndGamepadTouch(b, () -> { if (!isEditMode) dpad = dpadValue; }, () -> { if (!isEditMode) dpad = 8; });
    }

    private void applyEditAndGamepadTouch(View view, Runnable onPress, Runnable onRelease) {
        view.setOnTouchListener(new View.OnTouchListener() {
            float startX, startY, initialX, initialY;
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (isEditMode) {
                    switch (event.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN: startX = event.getRawX(); startY = event.getRawY(); initialX = v.getX(); initialY = v.getY(); return true;
                        case MotionEvent.ACTION_MOVE:
                            float dx = event.getRawX() - startX; float dy = event.getRawY() - startY;
                            float newX = initialX + dx; float newY = initialY + dy;
                            newX = Math.max(0, Math.min(newX, gamepadArea.getWidth() - v.getWidth()));
                            newY = Math.max(0, Math.min(newY, gamepadArea.getHeight() - v.getHeight()));
                            v.setX(newX); v.setY(newY); return true;
                    }
                    return true;
                } else {
                    switch (event.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN: if (onPress != null) onPress.run(); return true;
                        case MotionEvent.ACTION_UP: case MotionEvent.ACTION_CANCEL: if (onRelease != null) onRelease.run(); return true;
                    }
                    return false;
                }
            }
        });
    }

    private void applyEditTouchOnly(View view) {
        view.setOnTouchListener(new View.OnTouchListener() {
            float startX, startY, initialX, initialY;
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (!isEditMode) return false;
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN: startX = event.getRawX(); startY = event.getRawY(); initialX = v.getX(); initialY = v.getY(); return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - startX; float dy = event.getRawY() - startY;
                        float newX = initialX + dx; float newY = initialY + dy;
                        newX = Math.max(0, Math.min(newX, gamepadArea.getWidth() - v.getWidth()));
                        newY = Math.max(0, Math.min(newY, gamepadArea.getHeight() - v.getHeight()));
                        v.setX(newX); v.setY(newY); return true;
                }
                return true;
            }
        });
    }

    private void loadLayout() {
        boolean loaded = false;
        for (int id : allIds) {
            View child = findViewById(id);
            if (child != null) {
                String idName = getResources().getResourceEntryName(id);
                if (prefs.contains(idName + "_x")) {
                    applyClampedPosition(child, prefs.getFloat(idName + "_x", 0f), prefs.getFloat(idName + "_y", 0f), prefs.getInt(idName + "_w", child.getWidth()), prefs.getInt(idName + "_h", child.getHeight()));
                    loaded = true;
                }
            }
        }
        if (!loaded) loadDefaultFromJson();
    }

    private void loadDefaultFromJson() {
        try {
            InputStream is = getAssets().open("layout_default.json");
            byte[] buffer = new byte[is.available()]; is.read(buffer); is.close();
            applyJsonLayout(new String(buffer, StandardCharsets.UTF_8), false);
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void applyJsonLayout(String json, boolean saveToPrefs) {
        try {
            JSONObject obj = new JSONObject(json);
            float density = getResources().getDisplayMetrics().density;
            SharedPreferences.Editor editor = prefs.edit();
            for (int id : allIds) {
                String idName = getResources().getResourceEntryName(id);
                if (obj.has(idName)) {
                    View child = findViewById(id);
                    if (child != null) {
                        JSONObject props = obj.getJSONObject(idName);
                        applyClampedPosition(child, (float)props.getDouble("x")*density, (float)props.getDouble("y")*density, (int)(props.getInt("w")*density), (int)(props.getInt("h")*density));
                        if (saveToPrefs) {
                            editor.putFloat(idName + "_x", child.getX()); editor.putFloat(idName + "_y", child.getY());
                            editor.putInt(idName + "_w", child.getWidth()); editor.putInt(idName + "_h", child.getHeight());
                        }
                    }
                }
            }
            if (saveToPrefs) editor.apply();
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void applyClampedPosition(View child, float x, float y, int w, int h) {
        ViewGroup.LayoutParams params = child.getLayoutParams(); params.width = w; params.height = h; child.setLayoutParams(params);
        child.setX(Math.max(0, Math.min(x, gamepadArea.getWidth() - w)));
        child.setY(Math.max(0, Math.min(y, gamepadArea.getHeight() - h)));
    }

    private void resetLayout() { prefs.edit().clear().apply(); loadDefaultFromJson(); }

    private void readLayoutFromUri(Uri uri) {
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            byte[] buffer = new byte[is.available()]; is.read(buffer); is.close();
            applyJsonLayout(new String(buffer, StandardCharsets.UTF_8), true);
            Toast.makeText(this, "Import layout berhasil!", Toast.LENGTH_SHORT).show();
        } catch (Exception e) { e.printStackTrace(); }
    }

    // Pastikan Anda memindahkan fungsi writeLayoutToUri, exportLauncher, dll ke sini jika belum.
    // Karena kita memindahkan tombol Export ke Settings, Anda bisa membuat logika export di MainActivity dan memanggilnya lewat Intent.
    // Namun untuk kesederhanaan, UI Settings saat ini menyarankan export via mode Edit di MainActivity.

    // Jangan lupa tambahkan logika untuk mendeteksi "Mode Edit" dari Settings.
    // Karena kita mengubah cara kerja, kita bisa membaca flag dari SharedPreferences.
    @Override
    protected void onStart() {
        super.onStart();
        // Baca apakah user baru saja menekan tombol Edit di Settings
        isEditMode = prefs.getBoolean("isEditMode", false);
        if (isEditMode) {
            Toast.makeText(this, "Mode Edit: Geser tombol, lalu klik ikon Gear untuk simpan", Toast.LENGTH_LONG).show();
        }
    }

    // Fungsi untuk menyimpan layout saat keluar dari mode edit
    // Bisa dipanggil saat user kembali ke Settings
}