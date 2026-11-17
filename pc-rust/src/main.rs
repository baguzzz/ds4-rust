mod input_utils;
mod xinput_handler;
mod android_server;
mod serial_server; // Modul baru

use eframe::egui;
use std::thread;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;

struct DS4App {
    local_status: String,
    tcp_status: String,
    bt_status: String,
    ip_address: String,
    com_port: String,
    is_local_running: Arc<AtomicBool>,
    is_tcp_running: Arc<AtomicBool>,
    is_bt_running: Arc<AtomicBool>,
}

impl Default for DS4App {
    fn default() -> Self {
        // Otomatis mendeteksi port COM pertama yang tersedia
        let ports = serialport::available_ports().unwrap_or_default();
        let first_com = ports.first().map(|p| p.port_name.clone()).unwrap_or_else(|| "COM1".to_string());

        Self {
            local_status: "Status: Mati".to_owned(),
            tcp_status: "Status: Mati".to_owned(),
            bt_status: "Status: Mati".to_owned(),
            ip_address: input_utils::get_local_ip(),
            com_port: first_com,
            is_local_running: Arc::new(AtomicBool::new(false)),
            is_tcp_running: Arc::new(AtomicBool::new(false)),
            is_bt_running: Arc::new(AtomicBool::new(false)),
        }
    }
}

impl eframe::App for DS4App {
    fn update(&mut self, ctx: &egui::Context, _frame: &mut eframe::Frame) {
        egui::CentralPanel::default().show(ctx, |ui| {
            ui.heading("DS4 Unified - Rust Edition");
            ui.separator();

            // --- LOCAL CONTROLLER ---
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

            // --- ANDROID SERVER (Wi-Fi) ---
            ui.group(|ui| {
                ui.label("Android Server (Wi-Fi / TCP)");
                ui.horizontal(|ui| {
                    let is_running = self.is_tcp_running.load(Ordering::Relaxed);
                    if ui.button(if is_running { "Hentikan Server TCP" } else { "Mulai Server TCP" }).clicked() {
                        let new_running = !is_running;
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

            // --- ANDROID SERVER (Bluetooth) ---
            ui.group(|ui| {
                ui.label("Android Server (Bluetooth / Serial)");
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
        });
    }
}

fn main() -> Result<(), eframe::Error> {
    let options = eframe::NativeOptions {
        viewport: egui::ViewportBuilder::default().with_inner_size([650.0, 500.0]),
        vsync: true,
        ..Default::default()
    };
    eframe::run_native(
        "DS4 Unified Rust",
        options,
        Box::new(|_cc| Box::<DS4App>::default()),
    )
}