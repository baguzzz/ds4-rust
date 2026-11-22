package com.example.btmouse;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

import java.io.IOException;
import java.io.OutputStream;
import java.util.UUID;

public class ConnectionManager {
    private static final String TAG = "ConnectionManager";
    private static final UUID MY_UUID =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private static ConnectionManager instance;

    private BluetoothSocket bluetoothSocket;
    private OutputStream outputStream;
    private volatile boolean isConnected = false;

    private ConnectionManager() {}

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
    public void connectBluetooth(
            BluetoothDevice device,
            ConnectionCallback callback
    ) {
        if (isConnected) {
            disconnect();
        }

        new Thread(() -> {
            BluetoothSocket socket = null;

            try {
                BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();

                if (adapter == null) {
                    throw new IOException("Bluetooth tidak tersedia");
                }

                if (!adapter.isEnabled()) {
                    throw new IOException("Bluetooth belum aktif");
                }

                if (adapter.isDiscovering()) {
                    adapter.cancelDiscovery();
                }

                socket = device.createRfcommSocketToServiceRecord(MY_UUID);
                bluetoothSocket = socket;

                socket.connect();

                outputStream = socket.getOutputStream();
                isConnected = true;

                if (callback != null) {
                    callback.onSuccess(
                            device.getName() != null
                                    ? device.getName()
                                    : device.getAddress()
                    );
                }
            } catch (Exception e) {
                Log.e(TAG, "Connection error", e);

                if (socket != null) {
                    try {
                        socket.close();
                    } catch (IOException ignored) {}
                }

                bluetoothSocket = null;
                outputStream = null;
                isConnected = false;

                if (callback != null) {
                    callback.onFailure(e.getMessage());
                }
            }
        }).start();
    }

    public synchronized void sendData(
            int dx,
            int dy,
            int buttons,
            int wheel
    ) {
        if (!isConnected || outputStream == null) {
            return;
        }

        byte[] packet = new byte[5];
        packet[0] = (byte) 0xAA;
        packet[1] = (byte) dx;
        packet[2] = (byte) dy;
        packet[3] = (byte) buttons;
        packet[4] = (byte) wheel;

        try {
            outputStream.write(packet);
            outputStream.flush();
        } catch (IOException e) {
            Log.e(TAG, "Error sending data", e);
            disconnect();
        }
    }

    public synchronized void disconnect() {
        isConnected = false;

        if (outputStream != null) {
            try {
                outputStream.close();
            } catch (IOException ignored) {}
            outputStream = null;
        }

        if (bluetoothSocket != null) {
            try {
                bluetoothSocket.close();
            } catch (IOException ignored) {}
            bluetoothSocket = null;
        }
    }
}