package com.example.btmouse;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHidDevice;
import android.bluetooth.BluetoothHidDeviceAppSdpSettings;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.concurrent.Executor;

public class MainActivity extends AppCompatActivity {

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothHidDevice hidDevice;
    private BluetoothDevice connectedHost;

    private TextView statusText;

    private static final int REQ_BT_PERM = 101;
    private static final int REPORT_ID = 1;
    private static final int REPORT_SIZE = 5;

    private int mouseButtons = 0; // Bit 0: Kiri, Bit 1: Kanan, Bit 2: Tengah

    // HID Report Descriptor untuk Mouse standar (3 tombol, X, Y, Wheel)
    private static final byte[] HID_REPORT_DESCRIPTOR = {
            (byte) 0x05, (byte) 0x01, // Usage Page (Generic Desktop)
            (byte) 0x09, (byte) 0x02, // Usage (Mouse)
            (byte) 0xA1, (byte) 0x01, // Collection (Application)
            (byte) 0x85, (byte) 0x01, //   Report ID (1)
            (byte) 0x09, (byte) 0x01, //   Usage (Pointer)
            (byte) 0xA1, (byte) 0x00, //   Collection (Physical)
            (byte) 0x05, (byte) 0x09, //     Usage Page (Button)
            (byte) 0x19, (byte) 0x01, //     Usage Minimum (1)
            (byte) 0x29, (byte) 0x03, //     Usage Maximum (3)
            (byte) 0x15, (byte) 0x00, //     Logical Minimum (0)
            (byte) 0x25, (byte) 0x01, //     Logical Maximum (1)
            (byte) 0x95, (byte) 0x03, //     Report Count (3)
            (byte) 0x75, (byte) 0x01, //     Report Size (1)
            (byte) 0x81, (byte) 0x02, //     Input (Data, Var, Abs)
            (byte) 0x95, (byte) 0x01, //     Report Count (1)
            (byte) 0x75, (byte) 0x05, //     Report Size (5)
            (byte) 0x81, (byte) 0x03, //     Input (Const, Var, Abs) ; Padding
            (byte) 0x05, (byte) 0x01, //     Usage Page (Generic Desktop)
            (byte) 0x09, (byte) 0x30, //     Usage (X)
            (byte) 0x09, (byte) 0x31, //     Usage (Y)
            (byte) 0x09, (byte) 0x38, //     Usage (Wheel)
            (byte) 0x15, (byte) 0x81, //     Logical Minimum (-127)
            (byte) 0x25, (byte) 0x7F, //     Logical Maximum (127)
            (byte) 0x75, (byte) 0x08, //     Report Size (8)
            (byte) 0x95, (byte) 0x03, //     Report Count (3)
            (byte) 0x81, (byte) 0x06, //     Input (Data, Var, Rel)
            (byte) 0xC0,               //   End Collection
            (byte) 0xC0                // End Collection
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusText = findViewById(R.id.statusText);
        TouchpadView touchpad = findViewById(R.id.touchpad);
        
        Button btnLeft = findViewById(R.id.btnLeftClick);
        Button btnRight = findViewById(R.id.btnRightClick);
        Button btnScrollUp = findViewById(R.id.btnScrollUp);
        Button btnScrollDown = findViewById(R.id.btnScrollDown);
        Button btnConnect = findViewById(R.id.btnConnect);

        // --- Setup Touchpad ---
        touchpad.setOnTouchpadListener((dx, dy) -> {
            if (connectedHost != null) {
                sendMouseReport(mouseButtons, dx, dy, 0);
            }
        });

        // --- Setup Klik Mouse ---
        btnLeft.setOnTouchListener((v, event) -> {
            handleButtonTouch(event, 0);
            return false;
        });

        btnRight.setOnTouchListener((v, event) -> {
            handleButtonTouch(event, 1);
            return false;
        });

        // --- Setup Scroll ---
        btnScrollUp.setOnClickListener(v -> {
            if (connectedHost != null) sendMouseReport(mouseButtons, 0, 0, 1);
        });
        btnScrollDown.setOnClickListener(v -> {
            if (connectedHost != null) sendMouseReport(mouseButtons, 0, 0, -1);
        });

        // --- Setup Pairing ---
        btnConnect.setOnClickListener(v -> {
            checkPermissionsAndInit();
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN}, REQ_BT_PERM);
            } else {
                initBluetooth();
            }
        } else {
            initBluetooth();
        }
    }

    private void handleButtonTouch(android.view.MotionEvent event, int btnIndex) {
        if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
            mouseButtons |= (1 << btnIndex);
            if (connectedHost != null) sendMouseReport(mouseButtons, 0, 0, 0);
        } else if (event.getAction() == android.view.MotionEvent.ACTION_UP) {
            mouseButtons &= ~(1 << btnIndex);
            if (connectedHost != null) sendMouseReport(mouseButtons, 0, 0, 0);
        }
    }

    private void checkPermissionsAndInit() {
        initBluetooth();
    }

    @SuppressLint("MissingPermission")
    private void initBluetooth() {
        BluetoothManager bm = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bm.getAdapter();
        
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Toast.makeText(this, "Nyalakan Bluetooth terlebih dahulu", Toast.LENGTH_SHORT).show();
            return;
        }
        bluetoothAdapter.getProfileProxy(this, profileListener, BluetoothProfile.HID_DEVICE);
    }

    private final BluetoothProfile.ServiceListener profileListener = new BluetoothProfile.ServiceListener() {
        @Override
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                hidDevice = (BluetoothHidDevice) proxy;
                registerHidApp();
            }
        }
        @Override
        public void onServiceDisconnected(int profile) {
            hidDevice = null;
        }
    };

    @SuppressLint("MissingPermission")
    private void registerHidApp() {
        if (hidDevice == null) return;
        
        BluetoothHidDeviceAppSdpSettings sdp = new BluetoothHidDeviceAppSdpSettings(
                "BT Mouse",
                "Android Bluetooth Mouse",
                "com.example.btmouse",
                BluetoothHidDevice.SUBCLASS1_MOUSE, // Subclass Mouse
                HID_REPORT_DESCRIPTOR
        );
        
        Executor executor = getMainExecutor();
        boolean ok = hidDevice.registerApp(sdp, null, null, executor, callback);
        
        if (ok) {
            statusText.setText("Pairing Mode Aktif. Cari 'BT Mouse' di PC Anda.");
            statusText.setTextColor(0xFFFF9800);
        }
    }

    private final BluetoothHidDevice.Callback callback = new BluetoothHidDevice.Callback() {
        @Override
        public void onConnectionStateChanged(BluetoothDevice device, int state) {
            runOnUiThread(() -> {
                if (state == BluetoothProfile.STATE_CONNECTED) {
                    connectedHost = device;
                    statusText.setText("✅ Terhubung ke " + device.getName());
                    statusText.setTextColor(0xFF4CAF50);
                } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                    connectedHost = null;
                    statusText.setText("Terputus. Pairing ulang dari PC.");
                    statusText.setTextColor(0xFFF44336);
                }
            });
        }
    };

    @SuppressLint("MissingPermission")
    private void sendMouseReport(int buttons, int dx, int dy, int wheel) {
        if (hidDevice == null || connectedHost == null) return;

        // Report: [ID, Buttons, X, Y, Wheel]
        byte[] report = new byte[REPORT_SIZE];
        report[0] = (byte) REPORT_ID;
        report[1] = (byte) buttons;
        report[2] = (byte) dx;
        report[3] = (byte) dy;
        report[4] = (byte) wheel;

        try {
            hidDevice.sendReport(connectedHost, REPORT_ID, report);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_BT_PERM) initBluetooth();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (hidDevice != null) {
            try { hidDevice.unregisterApp(); } catch (Exception ignored) {}
        }
        if (bluetoothAdapter != null) {
            bluetoothAdapter.closeProfileProxy(BluetoothProfile.HID_DEVICE, hidDevice);
        }
    }
}
