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
import java.lang.reflect.Method;
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
        if (isConnected) {
            Toast.makeText(this, "Sudah terhubung", Toast.LENGTH_SHORT).show();
            return;
        }

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Toast.makeText(this, "Nyalakan Bluetooth HP terlebih dahulu", Toast.LENGTH_SHORT).show();
            return;
        }

        // 1. Ambil SEMUA perangkat yang sudah di-pairing
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        if (pairedDevices.isEmpty()) {
            Toast.makeText(this, "Tidak ada perangkat ter-pairing. Pairing HP ke PC dulu!", Toast.LENGTH_LONG).show();
            return;
        }

        // Ambil perangkat pertama (asumsi PC adalah satu-satunya yang di-pairing)
        BluetoothDevice targetDevice = pairedDevices.iterator().next();
        
        // Opsional: Jika ada banyak, cari yang namanya cocok
        for (BluetoothDevice device : pairedDevices) {
            if (device.getName() != null && device.getName().contains("ACER")) { // Ganti "ACER" dengan nama PC Anda
                targetDevice = device;
                break;
            }
        }

        final BluetoothDevice finalDevice = targetDevice;
        statusText.setText("Menghubungkan ke " + finalDevice.getName() + "...");

        // 2. Proses koneksi di Thread terpisah
        new Thread(() -> {
            try {
                bluetoothAdapter.cancelDiscovery();
                
                // 3. TRIK REFLEKSI CHANNEL 1 (Kunci keberhasilan koneksi)
                BluetoothSocket newSocket = null;
                try {
                    Method m = finalDevice.getClass().getMethod("createRfcommSocket", int.class);
                    newSocket = (BluetoothSocket) m.invoke(finalDevice, 1);
                } catch (Exception e) {
                    // Jika refleksi gagal, fallback ke metode standar
                    newSocket = finalDevice.createRfcommSocketToServiceRecord(MY_UUID);
                }

                newSocket.connect();
                bluetoothSocket = newSocket;
                outputStream = bluetoothSocket.getOutputStream();
                isConnected = true;

                runOnUiThread(() -> {
                    statusText.setText("✅ Terhubung ke " + finalDevice.getName());
                    statusText.setTextColor(0xFF4CAF50);
                });
            } catch (IOException e) {
                runOnUiThread(() -> {
                    statusText.setText("❌ Gagal Connect: " + e.getMessage());
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
            runOnUiThread(() -> {
                statusText.setText("Terputus");
                statusText.setTextColor(0xFFF44336);
            });
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (bluetoothSocket != null) bluetoothSocket.close();
        } catch (IOException ignored) {}
    }
}
