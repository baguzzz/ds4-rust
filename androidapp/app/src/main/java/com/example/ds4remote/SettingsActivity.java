package com.example.ds4remote;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;

public class SettingsActivity extends AppCompatActivity {

    private EditText ipInput;
    private Button connectBtn, btnToggleEdit;
    private SharedPreferences prefs;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    // Launcher untuk Export Layout
    private final ActivityResultLauncher<Intent> exportLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    // Karena fungsi writeLayoutToUri ada di MainActivity, kita bisa panggil via Intent atau static helper.
                    // Untuk kesederhanaan, kita asumsikan user menggunakan mode Edit dan menyimpannya langsung dari MainActivity.
                    Toast.makeText(this, "Fitur export dipindah ke mode Edit", Toast.LENGTH_SHORT).show();
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
        btnToggleEdit = findViewById(R.id.btnToggleEdit);

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
                            Toast.makeText(this, "Terhubung!", Toast.LENGTH_SHORT).show();
                            updateConnectionUI();
                        });
                    } catch (IOException e) {
                        uiHandler.post(() -> {
                            Toast.makeText(this, "Gagal terhubung: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            updateConnectionUI();
                        });
                    }
                }).start();
            }
        });

        btnToggleEdit.setOnClickListener(v -> {
            // Kita hanya perlu mengirim sinyal ke MainActivity untuk masuk ke mode Edit
            // Di sini kita hanya menampilkan pesan
            Toast.makeText(this, "Silakan kembali ke halaman Controller untuk menggeser", Toast.LENGTH_SHORT).show();
            finish(); // Kembali ke MainActivity
        });

        findViewById(R.id.btnResetLayout).setOnClickListener(v -> {
            prefs.edit().clear().apply();
            Toast.makeText(this, "Tata letak di-reset!", Toast.LENGTH_SHORT).show();
            finish(); // Restart MainActivity untuk memuat default
        });

        findViewById(R.id.btnExportLayout).setOnClickListener(v -> {
            Toast.makeText(this, "Buka halaman utama, aktifkan Edit, lalu klik SAVE", Toast.LENGTH_LONG).show();
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