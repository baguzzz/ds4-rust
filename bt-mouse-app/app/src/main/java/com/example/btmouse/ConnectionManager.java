package com.example.btmouse;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.os.Looper;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.UUID;

public class ConnectionManager {

    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static ConnectionManager instance;

    private BluetoothSocket bluetoothSocket;
    private OutputStream outputStream;
    private boolean isConnected = false;
    private ConnectionListener listener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface ConnectionListener {
        void onConnected(String deviceName);
        void onDisconnected(String reason);
    }

    private ConnectionManager() {}

    public static synchronized ConnectionManager getInstance() {
        if (instance == null) {
            instance = new ConnectionManager();
        }
        return instance;
    }

    public void setConnectionListener(ConnectionListener l) {
        this.listener = l;
    }

    public boolean isConnected() {
        return isConnected;
    }

    @SuppressLint("MissingPermission")
    public void connectBluetooth(BluetoothDevice device) {
        if (isConnected) disconnect();

        new Thread(() -> {
            try {
                BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
                if (adapter != null) adapter.cancelDiscovery();

                BluetoothSocket socket = null;
                try {
                    // Coba refleksi ke RFCOMM Channel 1
                    Method m = device.getClass().getMethod("createRfcommSocket", int.class);
                    socket = (BluetoothSocket) m.invoke(device, 1);
                } catch (Exception e) {
                    socket = device.createRfcommSocketToServiceRecord(MY_UUID);
                }

                socket.connect();
                bluetoothSocket = socket;
                outputStream = socket.getOutputStream();
                isConnected = true;

                final String name = device.getName() != null ? device.getName() : device.getAddress();
                mainHandler.post(() -> {
                    if (listener != null) listener.onConnected(name);
                });

            } catch (IOException e) {
                isConnected = false;
                final String errMsg = e.getMessage();
                mainHandler.post(() -> {
                    if (listener != null) listener.onDisconnected("Gagal connect: " + errMsg);
                });
            }
        }).start();
    }

    public void disconnect() {
        isConnected = false;
        new Thread(() -> {
            try {
                if (outputStream != null) outputStream.close();
                if (bluetoothSocket != null) bluetoothSocket.close();
            } catch (IOException ignored) {}
            outputStream = null;
            bluetoothSocket = null;
            mainHandler.post(() -> {
                if (listener != null) listener.onDisconnected("Terputus");
            });
        }).start();
    }

    public synchronized void sendData(int dx, int dy, int buttons, int wheel) {
        if (!isConnected || outputStream == null) return;
        try {
            byte[] buffer = new byte[4];
            buffer[0] = (byte) dx;
            buffer[1] = (byte) dy;
            buffer[2] = (byte) buttons;
            buffer[3] = (byte) wheel;
            outputStream.write(buffer);
            outputStream.flush();
        } catch (IOException e) {
            isConnected = false;
            mainHandler.post(() -> {
                if (listener != null) listener.onDisconnected("Terputus saat kirim data");
            });
        }
    }
}
