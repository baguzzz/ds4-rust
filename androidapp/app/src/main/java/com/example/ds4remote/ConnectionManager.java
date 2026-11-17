package com.example.ds4remote;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.UUID;

public class ConnectionManager {
    private static ConnectionManager instance;
    private volatile boolean connected = false;
    private OutputStream output = null;
    
    private Socket wifiSocket = null;
    private BluetoothSocket btSocket = null;

    // UUID standar untuk Bluetooth Serial Port Profile (SPP)
    public static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private ConnectionManager() {}

    public static synchronized ConnectionManager getInstance() {
        if (instance == null) instance = new ConnectionManager();
        return instance;
    }

    public boolean isConnected() { return connected; }
    public OutputStream getOutput() { return output; }

    // ================= KONEKSI WI-FI =================
    public void connectWifi(String ip) throws IOException {
        disconnect();
        Socket newSocket = new Socket();
        newSocket.connect(new InetSocketAddress(ip, 27020), 3000);
        newSocket.setTcpNoDelay(true);
        wifiSocket = newSocket;
        output = wifiSocket.getOutputStream();
        connected = true;
    }

    // ================= KONEKSI BLUETOOTH =================
    public void connectBluetooth(BluetoothDevice device) throws IOException {
        disconnect();
        BluetoothSocket newSocket = device.createRfcommSocketToServiceRecord(MY_UUID);
        
        // Membatalkan proses scan (wajib agar koneksi stabil)
        BluetoothAdapter.getDefaultAdapter().cancelDiscovery(); 
        
        newSocket.connect();
        btSocket = newSocket;
        output = btSocket.getOutputStream();
        connected = true;
    }

    public void disconnect() {
        connected = false;
        try { if (wifiSocket != null) wifiSocket.close(); } catch (IOException ignored) {}
        try { if (btSocket != null) btSocket.close(); } catch (IOException ignored) {}
        wifiSocket = null;
        btSocket = null;
        output = null;
    }
}