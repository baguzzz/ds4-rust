package com.example.btmouse;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import android.Manifest;
import android.os.Build;
import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;

public class MainActivity extends AppCompatActivity {

    private ConnectionManager connectionManager;
    private BluetoothAdapter bluetoothAdapter;
    private List<BluetoothDevice> deviceList = new ArrayList<>();
    private BluetoothDevice selectedDevice = null;

    private TextView statusText;
    private Spinner deviceSpinner;
    private Button btnConnect;
    private int mouseButtons = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        connectionManager = ConnectionManager.getInstance();
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        statusText = findViewById(R.id.statusText);
        deviceSpinner = findViewById(R.id.deviceSpinner);
        btnConnect = findViewById(R.id.btnConnect);
        TouchpadView touchpad = findViewById(R.id.touchpad);

        Button btnLeft = findViewById(R.id.btnLeftClick);
        Button btnRight = findViewById(R.id.btnRightClick);
        Button btnScrollUp = findViewById(R.id.btnScrollUp);
        Button btnScrollDown = findViewById(R.id.btnScrollDown);

        loadPairedDevices();

        deviceSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position < deviceList.size()) {
                    selectedDevice = deviceList.get(position);
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        btnConnect.setOnClickListener(v -> {
            if (connectionManager.isConnected()) {
                connectionManager.disconnect();
                statusText.setText("Terputus");
                statusText.setTextColor(0xFFB0BEC5);
                btnConnect.setText("Connect");
            } else {
                if (selectedDevice == null) {
                    Toast.makeText(this, "Pilih perangkat dari dropdown!", Toast.LENGTH_SHORT).show();
                    return;
                }
                statusText.setText("Menghubungkan...");
                connectionManager.connectBluetooth(selectedDevice, new ConnectionManager.ConnectionCallback() {
                    @Override
                    public void onSuccess(String deviceName) {
                        runOnUiThread(() -> {
                            statusText.setText("✅ Terhubung ke " + deviceName);
                            statusText.setTextColor(0xFF4CAF50);
                            btnConnect.setText("Disconnect");
                        });
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        runOnUiThread(() -> {
                            statusText.setText("❌ Gagal: " + errorMessage);
                            statusText.setTextColor(0xFFF44336);
                            btnConnect.setText("Connect");
                        });
                    }
                });
            }
        });

        // Touchpad Listener -> Kirim X, Y (Header 0xAA otomatis ditambahkan ConnectionManager)
        touchpad.setOnTouchpadListener((dx, dy) -> {
            connectionManager.sendData(dx, dy, mouseButtons, 0);
        });

        // Klik Kiri
        btnLeft.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                mouseButtons |= 1;
                connectionManager.sendData(0, 0, mouseButtons, 0);
            } else if (event.getAction() == MotionEvent.ACTION_UP) {
                mouseButtons &= ~1;
                connectionManager.sendData(0, 0, mouseButtons, 0);
            }
            return false;
        });

        // Klik Kanan
        btnRight.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                mouseButtons |= 2;
                connectionManager.sendData(0, 0, mouseButtons, 0);
            } else if (event.getAction() == MotionEvent.ACTION_UP) {
                mouseButtons &= ~2;
                connectionManager.sendData(0, 0, mouseButtons, 0);
            }
            return false;
        });

        // Scroll Up & Down
        btnScrollUp.setOnClickListener(v -> connectionManager.sendData(0, 0, mouseButtons, 1));
        btnScrollDown.setOnClickListener(v -> connectionManager.sendData(0, 0, mouseButtons, -1));
    }

    @SuppressLint("MissingPermission")
    private void loadPairedDevices() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Toast.makeText(this, "Nyalakan Bluetooth terlebih dahulu", Toast.LENGTH_SHORT).show();
            return;
        }

        Set<BluetoothDevice> paired = bluetoothAdapter.getBondedDevices();
        deviceList.clear();
        List<String> names = new ArrayList<>();

        if (paired != null && !paired.isEmpty()) {
            for (BluetoothDevice dev : paired) {
                deviceList.add(dev);
                names.add(dev.getName() != null ? dev.getName() : dev.getAddress());
            }
            selectedDevice = deviceList.get(0);
        } else {
            names.add("Tidak ada Paired Device");
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, names);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        deviceSpinner.setAdapter(adapter);
    }
}
