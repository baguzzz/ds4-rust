package com.example.btmouse;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket bluetoothSocket;
    private OutputStream outputStream;
    private boolean isConnected = false;

    private TextView statusText;
    private int mouseButtons = 0;

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

        // Touchpad -> Kirim X, Y
        touchpad.setOnTouchpadListener((dx, dy) -> {
            sendData(dx, dy, mouseButtons, 0);
        });

        // Tombol Klik Kiri
        btnLeft.setOnTouchListener((v, event) -> {
            if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                mouseButtons |= 1; sendData(0, 0, mouseButtons, 0);
            } else if (event.getAction() == android.view.MotionEvent.ACTION_UP) {
                mouseButtons &= ~1; sendData(0, 0, mouseButtons, 0);
            }
            return false;
        });

        // Tombol Klik Kanan
        btnRight.setOnTouchListener((v, event) -> {
            if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                mouseButtons |= 2; sendData(0, 0, mouseButtons, 0);
            } else if (event.getAction() == android.view.MotionEvent.ACTION_UP) {
                mouseButtons &= ~2; sendData(0, 0, mouseButtons, 0);
            }
            return false;
        });

        // Scroll
        btnScrollUp.setOnClickListener(v -> sendData(0, 0, mouseButtons, 1));
        btnScrollDown.setOnClickListener(v -> sendData(0, 0, mouseButtons, -1));

        // Tombol Connect
        btnConnect.setOnClickListener(v -> connectToPC());
    }

    @SuppressLint("MissingPermission")
    private void connectToPC() {
        if (isConnected) return;

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Toast.makeText(this, "Nyalakan Bluetooth", Toast.LENGTH_SHORT).show();
            return;
        }

        // Mencari perangkat PC yang sudah di-pairing
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        BluetoothDevice targetDevice = null;
        
        // CARI NAMA PC ANDA DI SINI (Ubah "LAPTOP" atau "PC" sesuai nama Bluetooth komputer Anda)
        for (BluetoothDevice device : pairedDevices) {
            String name = device.getName();
            if (name != null && (name.contains("PC") || name.contains("LAPTOP") || name.contains("DESKTOP"))) {
                targetDevice = device;
                break;
            }
        }

        if (targetDevice == null) {
            Toast.makeText(this, "Pairing HP ke PC dulu di Pengaturan Bluetooth!", Toast.LENGTH_LONG).show();
            return;
        }

        final BluetoothDevice finalDevice = targetDevice;
        statusText.setText("Menghubungkan...");

        new Thread(() -> {
            try {
                bluetoothAdapter.cancelDiscovery();
                bluetoothSocket = finalDevice.createRfcommSocketToServiceRecord(MY_UUID);
                bluetoothSocket.connect();
                outputStream = bluetoothSocket.getOutputStream();
                isConnected = true;
                runOnUiThread(() -> {
                    statusText.setText("✅ Terhubung ke " + finalDevice.getName());
                    statusText.setTextColor(0xFF4CAF50);
                });
            } catch (IOException e) {
                runOnUiThread(() -> {
                    statusText.setText("❌ Gagal. Pastikan PC membuat Port COM Bluetooth.");
                    statusText.setTextColor(0xFFF44336);
                });
            }
        }).start();
    }

    private void sendData(int dx, int dy, int buttons, int wheel) {
        if (!isConnected || outputStream == null) return;
        try {
            // Format paket: [X, Y, Buttons, Wheel] (4 byte)
            byte[] buffer = new byte[4];
            buffer[0] = (byte) dx;
            buffer[1] = (byte) dy;
            buffer[2] = (byte) buttons;
            buffer[3] = (byte) wheel;
            outputStream.write(buffer);
            outputStream.flush();
        } catch (IOException e) {
            isConnected = false;
            runOnUiThread(() -> statusText.setText("Terputus"));
        }
    }
}
