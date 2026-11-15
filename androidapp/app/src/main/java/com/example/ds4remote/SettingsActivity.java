package com.example.ds4remote;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class SettingsActivity extends AppCompatActivity {

    private EditText ipInput;
    private Button connectBtn;
    private SharedPreferences prefs;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    // Launcher untuk Export
    private final ActivityResultLauncher<Intent> exportLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    exportLayout(result.getData().getData());
                }
            }
    );

    // Launcher untuk Import
    private final ActivityResultLauncher<String[]> importLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            uri -> {
                if (uri != null) {
                    importLayout(uri);
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        prefs = getSharedPreferences("GamepadLayout", MODE_PRIVATE);
        ipInput = findViewById(R.id.ipInput);
        connectBtn = findViewById(R.id.connectBtn);

        ipInput.setText(prefs.getString("last_ip", "192.168.1.10"));
        updateConnectionUI();

        // --- LOGIKA CONNECT ---
        connectBtn.setOnClickListener(v -> {
            if (ConnectionManager.getInstance().isConnected()) {
                ConnectionManager.getInstance().disconnect();
                updateConnectionUI();
            } else {
                String ip = ipInput.getText().toString().trim();
                prefs.edit().putString("last_ip", ip).apply();

                new Thread(() -> {
                    try {
                        ConnectionManager.getInstance().connect(ip);
                        uiHandler.post(() -> {
                            Toast.makeText(this, "✅ Terhubung!", Toast.LENGTH_SHORT).show();
                            updateConnectionUI();
                        });
                    } catch (IOException e) {
                        uiHandler.post(() -> {
                            Toast.makeText(this, "❌ Gagal: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            updateConnectionUI();
                        });
                    }
                }).start();
            }
        });

        // --- LOGIKA EDIT MODE ---
        Button btnToggleEdit = findViewById(R.id.btnToggleEdit);
        btnToggleEdit.setOnClickListener(v -> {
            boolean isCurrentlyEdit = prefs.getBoolean("isEditMode", false);
            prefs.edit().putBoolean("isEditMode", !isCurrentlyEdit).apply();
            finish(); // Kembali ke controller
        });

        // --- LOGIKA RESET ---
        findViewById(R.id.btnResetLayout).setOnClickListener(v -> {
            prefs.edit().clear().apply();
            Toast.makeText(this, "Tata letak di-reset ke default!", Toast.LENGTH_SHORT).show();
            finish();
        });

        // --- LOGIKA EXPORT (SAVE) ---
        findViewById(R.id.btnExportLayout).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            intent.putExtra(Intent.EXTRA_TITLE, "layout_default.json");
            exportLauncher.launch(intent);
        });

        // --- LOGIKA IMPORT (LOAD) ---
        findViewById(R.id.btnImportLayout).setOnClickListener(v -> {
            importLauncher.launch(new String[]{"application/json", "text/plain", "*/*"});
        });
    }

    private void updateConnectionUI() {
        if (ConnectionManager.getInstance().isConnected()) {
            connectBtn.setText("DISCONNECT");
            connectBtn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFF44336));
        } else {
            connectBtn.setText("CONNECT");
            connectBtn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF4CAF50));
        }
    }

    // ================= FUNGSI EXPORT & IMPORT =================

    private void exportLayout(Uri uri) {
        try {
            JSONObject obj = new JSONObject();
            float density = getResources().getDisplayMetrics().density;
            Map<String, ?> allEntries = prefs.getAll();

            // Membaca semua koordinat dari SharedPreferences
            for (Map.Entry<String, ?> entry : allEntries.entrySet()) {
                String key = entry.getKey();
                if (key.endsWith("_x")) {
                    String idName = key.substring(0, key.length() - 2);
                    if (allEntries.containsKey(idName + "_y") &&
                        allEntries.containsKey(idName + "_w") &&
                        allEntries.containsKey(idName + "_h")) {

                        JSONObject props = new JSONObject();
                        // Konversi pixel kembali ke dp
                        props.put("x", Math.round((Float)entry.getValue() / density));
                        props.put("y", Math.round((Float)allEntries.get(idName + "_y") / density));
                        props.put("w", Math.round((Integer)allEntries.get(idName + "_w") / density));
                        props.put("h", Math.round((Integer)allEntries.get(idName + "_h") / density));
                        obj.put(idName, props);
                    }
                }
            }

            OutputStream os = getContentResolver().openOutputStream(uri);
            if (os != null) {
                os.write(obj.toString(4).getBytes(StandardCharsets.UTF_8));
                os.close();
                Toast.makeText(this, "Export berhasil!", Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Gagal export: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void importLayout(Uri uri) {
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            String json = new String(buffer, StandardCharsets.UTF_8);

            JSONObject obj = new JSONObject(json);
            float density = getResources().getDisplayMetrics().density;
            SharedPreferences.Editor editor = prefs.edit();

            // Simpan koordinat baru ke SharedPreferences
            for (java.util.Iterator<String> it = obj.keys(); it.hasNext(); ) {
                String idName = it.next();
                JSONObject props = obj.getJSONObject(idName);
                float x = (float) props.getDouble("x") * density;
                float y = (float) props.getDouble("y") * density;
                int w = (int) (props.getInt("w") * density);
                int h = (int) (props.getInt("h") * density);

                editor.putFloat(idName + "_x", x);
                editor.putFloat(idName + "_y", y);
                editor.putInt(idName + "_w", w);
                editor.putInt(idName + "_h", h);
            }
            editor.apply();

            Toast.makeText(this, "Import berhasil! Kembali ke controller.", Toast.LENGTH_SHORT).show();
            finish(); // Kembali ke MainActivity untuk menerapkan layout
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Gagal import: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}