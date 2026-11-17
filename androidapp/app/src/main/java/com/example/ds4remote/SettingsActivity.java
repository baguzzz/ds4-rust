package com.example.ds4remote;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SettingsActivity extends AppCompatActivity {

    private EditText ipInput;
    private Button connectBtn;
    private SharedPreferences prefs;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    // UI Bluetooth
    private RadioGroup connectionTypeGroup;
    private LinearLayout wifiLayout, btLayout;
    private Spinner btDeviceSpinner;
    private List<BluetoothDevice> pairedDevices = new ArrayList<>();

    // Permissions
    private static final int REQ_BT_PERM = 101;

    private final ActivityResultLauncher<Intent> exportLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    exportLayout(result.getData().getData());
                }
            }
    );

    private final ActivityResultLauncher<String[]> importLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            uri -> { if (uri != null) importLayout(uri); }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        prefs = getSharedPreferences("GamepadLayout", MODE_PRIVATE);
        ipInput = findViewById(R.id.ipInput);
        connectBtn = findViewById(R.id.connectBtn);

        // Inisialisasi UI Bluetooth
        connectionTypeGroup = findViewById(R.id.connectionTypeGroup);
        wifiLayout = findViewById(R.id.wifiLayout);
        btLayout = findViewById(R.id.btLayout);
        btDeviceSpinner = findViewById(R.id.btDeviceSpinner);
        Button btnRefreshBt = findViewById(R.id.btnRefreshBt);

        ipInput.setText(prefs.getString("last_ip", "192.168.1.10"));
        updateConnectionUI();

        // Toggle UI Wi-Fi / Bluetooth
        connectionTypeGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rbWifi) {
                wifiLayout.setVisibility(View.VISIBLE);
                btLayout.setVisibility(View.GONE);
            } else {
                wifiLayout.setVisibility(View.GONE);
                btLayout.setVisibility(View.VISIBLE);
                checkBluetoothPermissionsAndLoadDevices();
            }
        });

        btnRefreshBt.setOnClickListener(v -> checkBluetoothPermissionsAndLoadDevices());

        // LOGIKA CONNECT / DISCONNECT
        connectBtn.setOnClickListener(v -> {
            if (ConnectionManager.getInstance().isConnected()) {
                ConnectionManager.getInstance().disconnect();
                updateConnectionUI();
            } else {
                if (connectionTypeGroup.getCheckedRadioButtonId() == R.id.rbWifi) {
                    connectWifi();
                } else {
                    connectBluetooth();
                }
            }
        });

        // --- Logika Edit, Reset, Export, Import ---
        findViewById(R.id.btnToggleEdit).setOnClickListener(v -> {
            boolean isCurrentlyEdit = prefs.getBoolean("isEditMode", false);
            prefs.edit().putBoolean("isEditMode", !isCurrentlyEdit).apply();
            finish();
        });

        findViewById(R.id.btnResetLayout).setOnClickListener(v -> {
            prefs.edit().clear().apply();
            Toast.makeText(this, "Tata letak di-reset!", Toast.LENGTH_SHORT).show();
            finish();
        });

        findViewById(R.id.btnExportLayout).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            intent.putExtra(Intent.EXTRA_TITLE, "layout_default.json");
            exportLauncher.launch(intent);
        });

        findViewById(R.id.btnImportLayout).setOnClickListener(v -> {
            importLauncher.launch(new String[]{"application/json", "text/plain", "*/*"});
        });
    }

    private void connectWifi() {
        String ip = ipInput.getText().toString().trim();
        prefs.edit().putString("last_ip", ip).apply();
        new Thread(() -> {
            try {
                ConnectionManager.getInstance().connectWifi(ip);
                uiHandler.post(() -> { Toast.makeText(this, "✅ Terhubung Wi-Fi!", Toast.LENGTH_SHORT).show(); updateConnectionUI(); });
            } catch (Exception e) {
                uiHandler.post(() -> { Toast.makeText(this, "❌ Gagal Wi-Fi: " + e.getMessage(), Toast.LENGTH_LONG).show(); updateConnectionUI(); });
            }
        }).start();
    }

    private void connectBluetooth() {
        int pos = btDeviceSpinner.getSelectedItemPosition();
        if (pairedDevices.isEmpty() || pos < 0) {
            Toast.makeText(this, "Pairing perangkat Bluetooth di pengaturan Android terlebih dahulu!", Toast.LENGTH_LONG).show();
            return;
        }
        BluetoothDevice device = pairedDevices.get(pos);
        new Thread(() -> {
            try {
                ConnectionManager.getInstance().connectBluetooth(device);
                uiHandler.post(() -> { Toast.makeText(this, "✅ Terhubung Bluetooth!", Toast.LENGTH_SHORT).show(); updateConnectionUI(); });
            } catch (Exception e) {
                uiHandler.post(() -> { Toast.makeText(this, "❌ Gagal BT: " + e.getMessage(), Toast.LENGTH_LONG).show(); updateConnectionUI(); });
            }
        }).start();
    }

    // ================= LOGIKA IZIN & DAFTAR BLUETOOTH =================
    private void checkBluetoothPermissionsAndLoadDevices() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH_CONNECT}, REQ_BT_PERM);
                return;
            }
        }
        loadPairedDevices();
    }

    private void loadPairedDevices() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) {
            Toast.makeText(this, "HP ini tidak mendukung Bluetooth", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!adapter.isEnabled()) {
            Toast.makeText(this, "Aktifkan Bluetooth HP terlebih dahulu", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            Set<BluetoothDevice> devices = adapter.getBondedDevices();
            pairedDevices.clear();
            List<String> deviceNames = new ArrayList<>();

            for (BluetoothDevice device : devices) {
                pairedDevices.add(device);
                deviceNames.add(device.getName() + "\n" + device.getAddress());
            }

            if (deviceNames.isEmpty()) {
                deviceNames.add("Tidak ada perangkat ter-pairing");
            }

            ArrayAdapter<String> adapterSpinner = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, deviceNames);
            btDeviceSpinner.setAdapter(adapterSpinner);
        } catch (SecurityException e) {
            Toast.makeText(this, "Izin Bluetooth ditolak", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_BT_PERM && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            loadPairedDevices();
        } else {
            Toast.makeText(this, "Izin Bluetooth dibutuhkan untuk fitur ini", Toast.LENGTH_SHORT).show();
        }
    }

    // ================= FUNGSI EXPORT / IMPORT =================
    private void updateConnectionUI() {
        if (ConnectionManager.getInstance().isConnected()) {
            connectBtn.setText("DISCONNECT");
            connectBtn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFF44336));
        } else {
            connectBtn.setText("CONNECT");
            connectBtn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF4CAF50));
        }
    }

    private void exportLayout(Uri uri) {
        try {
            JSONObject obj = new JSONObject();
            float density = getResources().getDisplayMetrics().density;
            Map<String, ?> allEntries = prefs.getAll();
            for (Map.Entry<String, ?> entry : allEntries.entrySet()) {
                String key = entry.getKey();
                if (key.endsWith("_x")) {
                    String idName = key.substring(0, key.length() - 2);
                    if (allEntries.containsKey(idName + "_y") && allEntries.containsKey(idName + "_w") && allEntries.containsKey(idName + "_h")) {
                        JSONObject props = new JSONObject();
                        props.put("x", Math.round((Float)entry.getValue() / density));
                        props.put("y", Math.round((Float)allEntries.get(idName + "_y") / density));
                        props.put("w", Math.round((Integer)allEntries.get(idName + "_w") / density));
                        props.put("h", Math.round((Integer)allEntries.get(idName + "_h") / density));
                        obj.put(idName, props);
                    }
                }
            }
            OutputStream os = getContentResolver().openOutputStream(uri);
            if (os != null) { os.write(obj.toString(4).getBytes(StandardCharsets.UTF_8)); os.close(); Toast.makeText(this, "Export berhasil!", Toast.LENGTH_LONG).show(); }
        } catch (Exception e) { Toast.makeText(this, "Gagal export: " + e.getMessage(), Toast.LENGTH_LONG).show(); }
    }

    private void importLayout(Uri uri) {
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            byte[] buffer = new byte[is.available()]; is.read(buffer); is.close();
            JSONObject obj = new JSONObject(new String(buffer, StandardCharsets.UTF_8));
            float density = getResources().getDisplayMetrics().density;
            SharedPreferences.Editor editor = prefs.edit();
            for (java.util.Iterator<String> it = obj.keys(); it.hasNext(); ) {
                String idName = it.next();
                JSONObject props = obj.getJSONObject(idName);
                editor.putFloat(idName + "_x", (float) props.getDouble("x") * density);
                editor.putFloat(idName + "_y", (float) props.getDouble("y") * density);
                editor.putInt(idName + "_w", (int) (props.getInt("w") * density));
                editor.putInt(idName + "_h", (int) (props.getInt("h") * density));
            }
            editor.putBoolean("isEditMode", false);
            editor.putBoolean("forceLoadLayout", true);
            editor.apply();
            Toast.makeText(this, "Import berhasil!", Toast.LENGTH_SHORT).show();
            finish();
        } catch (Exception e) { Toast.makeText(this, "Gagal import: " + e.getMessage(), Toast.LENGTH_SHORT).show(); }
    }
}