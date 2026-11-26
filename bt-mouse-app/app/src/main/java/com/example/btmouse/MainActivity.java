package com.example.btmouse;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.MotionEvent;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_BLUETOOTH_CONNECT = 1001;
    private static final int LEFT_BUTTON = 1;
    private static final int RIGHT_BUTTON = 2;

    private ConnectionManager connectionManager;
    private BluetoothAdapter bluetoothAdapter;

    private final List<BluetoothDevice> deviceList = new ArrayList<>();
    private BluetoothDevice selectedDevice;

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

        checkBluetoothPermission();

        deviceSpinner.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(
                            AdapterView<?> parent,
                            android.view.View view,
                            int position,
                            long id
                    ) {
                        if (position >= 0 && position < deviceList.size()) {
                            selectedDevice = deviceList.get(position);
                        }
                    }

                    @Override
                    public void onNothingSelected(
                            AdapterView<?> parent
                    ) {
                        selectedDevice = null;
                    }
                }
        );

        btnConnect.setOnClickListener(v -> {
            if (connectionManager.isConnected()) {
                connectionManager.disconnect();
                statusText.setText("Terputus");
                statusText.setTextColor(0xFFB0BEC5);
                btnConnect.setText("Connect");
                return;
            }

            if (selectedDevice == null) {
                Toast.makeText(
                        this,
                        "Pilih perangkat dari dropdown",
                        Toast.LENGTH_SHORT
                ).show();
                return;
            }

            statusText.setText("Menghubungkan...");
            statusText.setTextColor(0xFFFFC107);
            btnConnect.setEnabled(false);

            connectionManager.connectBluetooth(
                    selectedDevice,
                    new ConnectionManager.ConnectionCallback() {
                        @Override
                        public void onSuccess(String deviceName) {
                            runOnUiThread(() -> {
                                statusText.setText(
                                        "Terhubung ke " + deviceName
                                );
                                statusText.setTextColor(0xFF4CAF50);
                                btnConnect.setText("Disconnect");
                                btnConnect.setEnabled(true);
                            });
                        }

                        @Override
                        public void onFailure(String errorMessage) {
                            runOnUiThread(() -> {
                                statusText.setText(
                                        "Gagal: "
                                                + (errorMessage != null
                                                ? errorMessage
                                                : "Koneksi gagal")
                                );
                                statusText.setTextColor(0xFFF44336);
                                btnConnect.setText("Connect");
                                btnConnect.setEnabled(true);
                            });
                        }
                    }
            );
        });

        touchpad.setOnTouchpadListener((dx, dy) -> {
            connectionManager.sendData(
                    dx,
                    dy,
                    mouseButtons,
                    0
            );
        });

        setMouseButtonListener(
                btnLeft,
                LEFT_BUTTON
        );

        setMouseButtonListener(
                btnRight,
                RIGHT_BUTTON
        );

        btnScrollUp.setOnClickListener(v ->
                connectionManager.sendData(
                        0,
                        0,
                        mouseButtons,
                        1
                )
        );

        btnScrollDown.setOnClickListener(v ->
                connectionManager.sendData(
                        0,
                        0,
                        mouseButtons,
                        -1
                )
        );
    }

    private void setMouseButtonListener(
            Button button,
            int buttonMask
    ) {
        button.setOnTouchListener((view, event) -> {
            int action = event.getActionMasked();

            if (action == MotionEvent.ACTION_DOWN) {
                mouseButtons |= buttonMask;
                connectionManager.sendData(
                        0,
                        0,
                        mouseButtons,
                        0
                );
            } else if (
                    action == MotionEvent.ACTION_UP
                            || action == MotionEvent.ACTION_CANCEL
            ) {
                mouseButtons &= ~buttonMask;
                connectionManager.sendData(
                        0,
                        0,
                        mouseButtons,
                        0
                );
            }

            return true;
        });
    }

    private void checkBluetoothPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (
                    ActivityCompat.checkSelfPermission(
                            this,
                            Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                        this,
                        new String[]{
                                Manifest.permission.BLUETOOTH_CONNECT
                        },
                        REQUEST_BLUETOOTH_CONNECT
                );
                return;
            }
        }

        loadPairedDevices();
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults
    ) {
        super.onRequestPermissionsResult(
                requestCode,
                permissions,
                grantResults
        );

        if (
                requestCode == REQUEST_BLUETOOTH_CONNECT
                        && grantResults.length > 0
                        && grantResults[0]
                        == PackageManager.PERMISSION_GRANTED
        ) {
            loadPairedDevices();
        } else if (requestCode == REQUEST_BLUETOOTH_CONNECT) {
            Toast.makeText(
                    this,
                    "Izin Bluetooth diperlukan",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    @SuppressLint("MissingPermission")
    private void loadPairedDevices() {
        deviceList.clear();

        if (bluetoothAdapter == null) {
            Toast.makeText(
                    this,
                    "Perangkat tidak mendukung Bluetooth",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        if (!bluetoothAdapter.isEnabled()) {
            Toast.makeText(
                    this,
                    "Nyalakan Bluetooth terlebih dahulu",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        Set<BluetoothDevice> paired =
                bluetoothAdapter.getBondedDevices();

        List<String> names = new ArrayList<>();

        if (paired != null && !paired.isEmpty()) {
            for (BluetoothDevice device : paired) {
                deviceList.add(device);

                String name = device.getName();

                if (name == null || name.isEmpty()) {
                    name = device.getAddress();
                }

                names.add(name);
            }

            selectedDevice = deviceList.get(0);
        } else {
            names.add("Tidak ada Paired Device");
            selectedDevice = null;
        }

        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(
                        this,
                        android.R.layout.simple_spinner_item,
                        names
                );

        adapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );

        deviceSpinner.setAdapter(adapter);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        connectionManager.disconnect();
    }
}