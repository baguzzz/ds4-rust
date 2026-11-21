package com.example.btmouse;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.UUID;

public class ConnectionManager {

    private static final String TAG = "ConnectionManager";
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static ConnectionManager instance;

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket bluetoothSocket;
    private OutputStream outputStream;
    private boolean isConnected = false;

    private ConnectionManager() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    public static synchronized ConnectionManager getInstance() {
        if (instance == null) {
            instance = new ConnectionManager();
        }
        return instance;
    }

    public boolean isConnected() {
        return isConnected;
    }

    public interface ConnectionCallback {
        void onSuccess(String deviceName);
        void onFailure(String errorMessage);
    }

    @SuppressLint("MissingPermission")
    public void connectBluetooth(BluetoothDevice device, ConnectionCallback callback) {
        if (isConnected) disconnect();

        new Thread(() -> {
            try {
                if (bluetoothAdapter != null && bluetoothAdapter.isDiscovering()) {
                    bluetoothAdapter.cancelDiscovery();
                }

                BluetoothSocket socket = null;
                // 1. Coba koneksi SPP RFCOMM standar via UUID
                try {
                    socket = device.createRfcommSocketToServiceRecord(MY_UUID);
                    socket.connect();
                } catch (IOException e1) {
                    Log.w(TAG, "Standard UUID connect failed, trying reflection Channel 1...");
                    // 2. Fallback ke Refleksi Channel 1 (seperti pada androidapp)
                    Method m = device.getClass().getMethod("createRfcommSocket", int.class);
                    socket = (BluetoothSocket) m.invoke(device, 1);
                    socket.connect();
                }

                bluetoothSocket = socket;
                outputStream = bluetoothSocket.getOutputStream();
                isConnected = true;

                if (callback != null) {
                    callback.onSuccess(device.getName());
                }
            } catch (Exception e) {
                Log.e(TAG, "Connection error", e);
                disconnect();
                if (callback != null) {
                    callback.onFailure(e.getMessage());
                }
            }
        }).start();
    }

    public void sendData(int dx, int dy, int buttons, int wheel) {
        if (!isConnected || outputStream == null) return;

        // Mengirim Paket 5-Byte dengan Header 0xAA (Persis seperti androidapp Gamepad)
        byte[] buffer = new byte[5];
        buffer[0] = (byte) 0xAA; // Header Sync Byte
        buffer[1] = (byte) dx;
        buffer[2] = (byte) dy;
        buffer[3] = (byte) buttons;
        buffer[4] = (byte) wheel;

        try {
            outputStream.write(buffer);
            outputStream.flush();
        } catch (IOException e) {
            Log.e(TAG, "Error sending data, disconnecting...", e);
            disconnect();
        }
    }

    public synchronized void disconnect() {
        isConnected = false;
        try {
            if (outputStream != null) {
                outputStream.close();
                outputStream = null;
            }
            if (bluetoothSocket != null) {
                bluetoothSocket.close();
                bluetoothSocket = null;
            }
        } catch (IOException e) {
            Log.e(TAG, "Error closing socket", e);
        }
    }
}
