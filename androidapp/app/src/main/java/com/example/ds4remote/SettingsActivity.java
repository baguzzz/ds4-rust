package com.example.ds4remote;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;

public class SettingsActivity extends AppCompatActivity {

    private EditText ipInput;
    private Button connectBtn;
    private SharedPreferences prefs;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        prefs = getSharedPreferences("GamepadLayout", MODE_PRIVATE);
        ipInput = findViewById(R.id.ipInput);
        connectBtn = findViewById(R.id.connectBtn);

        ipInput.setText(prefs.getString("last_ip", "192.168.1.10"));
        updateConnectionUI();

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

        Button btnToggleEdit = findViewById(R.id.btnToggleEdit);
        btnToggleEdit.setOnClickListener(v -> {
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
            Toast.makeText(this, "Tekan Edit di halaman utama, lalu Save", Toast.LENGTH_SHORT).show();
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
}