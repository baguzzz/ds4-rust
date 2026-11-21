package com.example.btmouse;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.os.Bundle;
import android.view.MotionEvent;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class MainActivity extends AppCompatActivity implements ConnectionManager.ConnectionListener {

    private ConnectionManager connectionManager;
    private BluetoothAdapter bluetoothAdapter;
    private List<BluetoothDevice> deviceList = new ArrayList<>();
    private ArrayAdapter<String> adapter;

    private Spinner deviceSpinner;
    private TextView statusText;
    private Button btnConnect;
    private int mouseButtons = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusText = findViewById(R.id.statusText);
        btnConnect = findViewById(R.id.btnConnect);
        deviceSpinner = findViewById(R.id.deviceSpinner);
        TouchpadView touchpad = findViewById(R.id.touchpad);

        Button btnLeft = findViewById(R.id.btnLeftClick);
        Button btnRight = findViewById(R.id.btnRightClick);
        Button btnScrollUp = findViewById(R.id.btnScrollUp);
        Button btnScrollDown = findViewById(R.id.btnScrollDown);

        connectionManager = ConnectionManager.getInstance();
        connectionManager.setConnectionListener(this);

        setupDeviceSpinner();

        btnConnect.setOnClickListener(v -> {
            if (connectionManager.isConnected()) {
                connectionManager.disconnect();
            } else {
                connectSelectedDevice();
            }
        });

        touchpad.setOnTouchpadListener((dx, dy) -> connectionManager.sendData(dx, dy, mouseButtons, 0));

        btnLeft.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                mouseButtons |= 1; connectionManager.sendData(0, 0, mouseButtons, 0);
            } else if (event.getAction() == MotionEvent.ACTION_UP) {
                mouseButtons &= ~1; connectionManager.sendData(0, 0, mouseButtons, 0);
            }
            return false;
        });

        btnRight.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                mouseButtons |= 2; connectionManager.sendData(0, 0, mouseButtons, 0);
            } else if (event.getAction() == MotionEvent.ACTION_UP) {
                mouseButtons &= ~2; connectionManager.sendData(0, 0, mouseButtons, 0);
            }
            return false;
        });

        btnScrollUp.setOnClickListener(v -> connectionManager.sendData(0, 0, mouseButtons, 1));
        btnScrollDown.setOnClickListener(v -> connectionManager.sendData(0, 0, mouseButtons, -1));

        updateUIStatus();
    }

    @SuppressLint("MissingPermission")
    private void setupDeviceSpinner() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        List<String> deviceNames = new ArrayList<>();

        if (bluetoothAdapter != null && bluetoothAdapter.isEnabled()) {
            Set<BluetoothDevice> paired = bluetoothAdapter.getBondedDevices();
            if (paired != null && !paired.isEmpty()) {
                for (BluetoothDevice dev : paired) {
                    deviceList.add(dev);
                    String name = dev.getName() != null ? dev.getName() : dev.getAddress();
                    deviceNames.add(name);
                }
            }
        }

        if (deviceNames.isEmpty()) {
            deviceNames.add("Tidak ada BT terpasang");
        }

        adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, deviceNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        deviceSpinner.setAdapter(adapter);
    }

    private void connectSelectedDevice() {
        int pos = deviceSpinner.getSelectedItemPosition();
        if (pos >= 0 && pos < deviceList.size()) {
            BluetoothDevice selectedDev = deviceList.get(pos);
            statusText.setText("Menghubungkan...");
            statusText.setTextColor(0xFFFF9800);
            connectionManager.connectBluetooth(selectedDev);
        } else {
            Toast.makeText(this, "Pilih perangkat Bluetooth terlebih dahulu", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateUIStatus() {
        if (connectionManager.isConnected()) {
            statusText.setText("✅ Terhubung");
            statusText.setTextColor(0xFF4CAF50);
            btnConnect.setText("Disconnect");
        } else {
            statusText.setText("Belum Terhubung");
            statusText.setTextColor(0xFFB0BEC5);
            btnConnect.setText("Connect");
        }
    }

    @Override
    public void onConnected(String deviceName) {
        statusText.setText("✅ Terhubung: " + deviceName);
        statusText.setTextColor(0xFF4CAF50);
        btnConnect.setText("Disconnect");
        Toast.makeText(this, "Terhubung ke " + deviceName, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDisconnected(String reason) {
        statusText.setText("❌ " + reason);
        statusText.setTextColor(0xFFF44336);
        btnConnect.setText("Connect");
    }
}
