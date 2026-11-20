mod input_utils;
mod xinput_handler;
mod android_server;
mod serial_server;
mod bluetooth_mouse; // Modul baru untuk Mouse Android 8

use eframe::egui;
use std::thread;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;

struct DS4App {
    // Status Text
    local_status: String,
    tcp_status: String,
    bt_status: String,
    bt_mouse_status: String,
    
    // Pengaturan
    ip_address: String,
    com_port: String,
    
    // Status Running (AtomicBool untuk Thread)
    is_local_running: Arc<AtomicBool>,
    is_tcp_running: Arc<AtomicBool>,
    is_bt_running: Arc<AtomicBool>,
    is_bt_mouse_running: Arc<AtomicBool>,
}

impl Default for DS4App {
    fn default() -> Self {
        // Mencari Port COM yang tersedia secara otomatis
        let ports = serialport::available_ports().unwrap_or_default();
        let first_com = ports.first().map(|p| p.port_name.clone()).unwrap_or_else(|| "COM1".to_string());

        Self {
            local_status: "Status: Mati".to_owned(),
            tcp_status: "Status: Mati".to_owned(),
            bt_status: "Status: Mati".to_owned(),
            bt_mouse_status: "Status: Mati".to_owned(),
            ip_address: input_utils::get_local_ip(),
            com_port: first_com,
            is_local_running: Arc::new(AtomicBool::new(false)),
            is_tcp_running: Arc::new(AtomicBool::new(false)),
            is_bt_running: Arc::new(AtomicBool::new(false)),
            is_bt_mouse_running: Arc::new(AtomicBool::new(false)),
        }
    }
}

impl eframe::App for DS4App {
    fn update(&mut self, ctx: &egui::Context, _frame: &mut eframe::Frame) {
        egui::CentralPanel::default().show(ctx, |ui| {
            ui.heading("DS4 Unified - PC Server");
            ui.separator();

            // ================= 1. LOCAL CONTROLLER (XINPUT) =================
            ui.group(|ui| {
                ui.label("Local Controller (XInput) -> Mouse/Keyboard");
                ui.horizontal(|ui| {
                    let is_running = self.is_local_running.load(Ordering::Relaxed);
                    if ui.button(if is_running { "Hentikan Gopher Lokal" } else { "Mulai Gopher Lokal" }).clicked() {
                        let new_running = !is_running;
                        self.is_local_running.store(new_running, Ordering::Relaxed);
                        if new_running {
                            self.local_status = "Status: Aktif (XInput)".to_string();
                            let r = self.is_local_running.clone();
                            thread::spawn(move || xinput_handler::run_xinput_gopher(r));
                        } else {
                            self.local_status = "Status: Mati".to_string();
                        }
                    }
                    ui.label(&self.local_status);
                });
            });

            ui.separator();

            // ================= 2. ANDROID GAMEPAD (WI-FI) =================
            ui.group(|ui| {
                ui.label("Android Gamepad (Wi-Fi / TCP)");
                ui.horizontal(|ui| {
                    let is_running = self.is_tcp_running.load(Ordering::Relaxed);
                    if ui.button(if is_running { "Hentikan Server TCP" } else { "Mulai Server TCP" }).clicked() {
                        let new_running = !is_running;
                        
                        if new_running && self.is_bt_running.load(Ordering::Relaxed) {
                            self.is_bt_running.store(false, Ordering::Relaxed);
                            self.bt_status = "Status: Mati (Beralih ke Wi-Fi)".to_string();
                            thread::sleep(std::time::Duration::from_millis(500));
                        }
                        if new_running && self.is_bt_mouse_running.load(Ordering::Relaxed) {
                            self.is_bt_mouse_running.store(false, Ordering::Relaxed);
                            self.bt_mouse_status = "Status: Mati (Beralih ke Wi-Fi)".to_string();
                            thread::sleep(std::time::Duration::from_millis(500));
                        }

                        self.is_tcp_running.store(new_running, Ordering::Relaxed);
                        if new_running {
                            self.tcp_status = "Status: Menunggu Android (TCP)...".to_string();
                            let r = self.is_tcp_running.clone();
                            thread::spawn(move || android_server::run_android_server(r));
                        } else {
                            self.tcp_status = "Status: Mati".to_string();
                        }
                    }
                    ui.label(&self.tcp_status);
                });
                ui.horizontal(|ui| {
                    ui.label("IP PC:");
                    ui.text_edit_singleline(&mut self.ip_address);
                });
            });

            ui.separator();

            // ================= 3. ANDROID GAMEPAD (BLUETOOTH) =================
            ui.group(|ui| {
                ui.label("Android Gamepad (Bluetooth / Serial)");
                ui.horizontal(|ui| {
                    ui.label("COM Port:");
                    ui.text_edit_singleline(&mut self.com_port);
                    if ui.button("Refresh").clicked() {
                        let ports = serialport::available_ports().unwrap_or_default();
                        if let Some(p) = ports.first() {
                            self.com_port = p.port_name.clone();
                        }
                    }
                });
                ui.horizontal(|ui| {
                    let is_running = self.is_bt_running.load(Ordering::Relaxed);
                    if ui.button(if is_running { "Hentikan Server BT" } else { "Mulai Server BT" }).clicked() {
                        let new_running = !is_running;

                        if new_running && self.is_tcp_running.load(Ordering::Relaxed) {
                            self.is_tcp_running.store(false, Ordering::Relaxed);
                            self.tcp_status = "Status: Mati (Beralih ke BT)".to_string();
                            thread::sleep(std::time::Duration::from_millis(500));
                        }
                        if new_running && self.is_bt_mouse_running.load(Ordering::Relaxed) {
                            self.is_bt_mouse_running.store(false, Ordering::Relaxed);
                            self.bt_mouse_status = "Status: Mati (Beralih ke BT Gamepad)".to_string();
                            thread::sleep(std::time::Duration::from_millis(500));
                        }

                        self.is_bt_running.store(new_running, Ordering::Relaxed);
                        if new_running {
                            self.bt_status = format!("Status: Berjalan di {}", self.com_port);
                            let r = self.is_bt_running.clone();
                            let port = self.com_port.clone();
                            thread::spawn(move || serial_server::run_bluetooth_server(port, r));
                        } else {
                            self.bt_status = "Status: Mati".to_string();
                        }
                    }
                    ui.label(&self.bt_status);
                });
            });

            ui.separator();

            // ================= 4. ANDROID MOUSE (BLUETOOTH) =================
            ui.group(|ui| {
                ui.label("Android Mouse (Bluetooth / Untuk Android 8)");
                ui.horizontal(|ui| {
                    ui.label("COM Port:");
                    ui.text_edit_singleline(&mut self.com_port); // Bisa menggunakan port yang sama
                });
                ui.horizontal(|ui| {
                    let is_running = self.is_bt_mouse_running.load(Ordering::Relaxed);
                    if ui.button(if is_running { "Hentikan Mouse BT" } else { "Mulai Mouse BT" }).clicked() {
                        let new_running = !is_running;

                        if new_running && self.is_tcp_running.load(Ordering::Relaxed) {
                            self.is_tcp_running.store(false, Ordering::Relaxed);
                            self.tcp_status = "Status: Mati (Beralih ke Mouse BT)".to_string();
                            thread::sleep(std::time::Duration::from_millis(500));
                        }
                        if new_running && self.is_bt_running.load(Ordering::Relaxed) {
                            self.is_bt_running.store(false, Ordering::Relaxed);
                            self.bt_status = "Status: Mati (Beralih ke Mouse BT)".to_string();
                            thread::sleep(std::time::Duration::from_millis(500));
                        }

                        self.is_bt_mouse_running.store(new_running, Ordering::Relaxed);
                        if new_running {
                            self.bt_mouse_status = format!("Status: Berjalan di {}", self.com_port);
                            let r = self.is_bt_mouse_running.clone();
                            let port = self.com_port.clone();
                            thread::spawn(move || bluetooth_mouse::run_bluetooth_mouse(port, r));
                        } else {
                            self.bt_mouse_status = "Status: Mati".to_string();
                        }
                    }
                    ui.label(&self.bt_mouse_status);
                });
            });
        });
    }
}

fn main() -> Result<(), eframe::Error> {
    let options = eframe::NativeOptions {
        viewport: egui::ViewportBuilder::default().with_inner_size([650.0, 600.0]), // Sedikit lebih tinggi karena ada 4 menu
        vsync: true,
        ..Default::default()
    };
    eframe::run_native(
        "DS4 Unified Rust",
        options,
        Box::new(|_cc| Box::<DS4App>::default()),
    )
}
