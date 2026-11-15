package com.example.ds4remote;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

public class ConnectionManager {
    private static ConnectionManager instance;
    private Socket socket;
    private OutputStream output;
    private volatile boolean connected = false;

    private ConnectionManager() {}

    public static synchronized ConnectionManager getInstance() {
        if (instance == null) instance = new ConnectionManager();
        return instance;
    }

    public boolean isConnected() { return connected; }
    public OutputStream getOutput() { return output; }

    public void connect(String ip) throws IOException {
        disconnect(); // Tutup koneksi lama jika ada
        Socket newSocket = new Socket();
        newSocket.connect(new InetSocketAddress(ip, 27020), 3000);
        newSocket.setTcpNoDelay(true);
        socket = newSocket;
        output = socket.getOutputStream();
        connected = true;
    }

    public void disconnect() {
        connected = false;
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}
        socket = null;
        output = null;
    }
}