mod input_utils;
mod xinput_handler;
mod android_server;

use eframe::egui;
use std::thread;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;

struct DS4App {
    local_status: String,
    server_status: String,
    ip_address: String,
    is_local_running: Arc<AtomicBool>,
    is_server_running: Arc<AtomicBool>,
}

impl Default for DS4App {
    fn default() -> Self {
        Self {
            local_status: "Status: Mati".to_owned(),
            server_status: "Status: Mati".to_owned(),
            ip_address: input_utils::get_local_ip(),
            is_local_running: Arc::new(AtomicBool::new(false)),
            is_server_running: Arc::new(AtomicBool::new(false)),
        }
    }
}

impl eframe::App for DS4App {
    fn update(&mut self, ctx: &egui::Context, _frame: &mut eframe::Frame) {
        egui::CentralPanel::default().show(ctx, |ui| {
            ui.heading("DS4 Unified - Rust Edition");
            ui.separator();

            ui.group(|ui| {
                ui.label("Local Controller (XInput) -> Menggerakkan Mouse/Keyboard");
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

            ui.group(|ui| {
                ui.label("Android Server (Wi-Fi) -> Virtual Xbox Controller");
                ui.horizontal(|ui| {
                    let is_running = self.is_server_running.load(Ordering::Relaxed);
                    if ui.button(if is_running { "Hentikan Server TCP" } else { "Mulai Server TCP" }).clicked() {
                        let new_running = !is_running;
                        self.is_server_running.store(new_running, Ordering::Relaxed);
                        if new_running {
                            self.server_status = "Status: Menunggu Android...".to_string();
                            let r = self.is_server_running.clone();
                            thread::spawn(move || android_server::run_android_server(r));
                        } else {
                            self.server_status = "Status: Mati".to_string();
                        }
                    }
                    ui.label(&self.server_status);
                });
                ui.horizontal(|ui| {
                    ui.label("IP PC:");
                    ui.text_edit_singleline(&mut self.ip_address);
                });
            });
        });
    }
}

fn main() -> Result<(), eframe::Error> {
    let options = eframe::NativeOptions {
        viewport: egui::ViewportBuilder::default().with_inner_size([600.0, 450.0]),
        vsync: true,
        ..Default::default()
    };
    eframe::run_native(
        "DS4 Unified Rust",
        options,
        Box::new(|_cc| Box::<DS4App>::default()),
    )
}