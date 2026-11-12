use eframe::egui;
use gilrs::{Gilrs, Button, Axis};
use std::net::TcpListener;
use std::io::Read;
use std::thread;
use std::sync::{Arc, Mutex};
use vigem_client::{Client, TargetId, Xbox360Wired, XGamepad, XButtons};
use windows::Win32::UI::Input::KeyboardAndMouse::*;

// State global untuk UI
struct DS4App {
    local_status: String,
    server_status: String,
    ip_address: String,
    is_local_running: Arc<Mutex<bool>>,
    is_server_running: Arc<Mutex<bool>>,
}

impl Default for DS4App {
    fn default() -> Self {
        Self {
            local_status: "Status: Mati".to_owned(),
            server_status: "Status: Mati".to_owned(),
            ip_address: get_local_ip(),
            is_local_running: Arc::new(Mutex::new(false)),
            is_server_running: Arc::new(Mutex::new(false)),
        }
    }
}

impl eframe::App for DS4App {
    fn update(&mut self, ctx: &egui::Context, _frame: &mut eframe::Frame) {
        egui::CentralPanel::default().show(ctx, |ui| {
            ui.heading("DS4 Unified - Rust Edition");
            ui.separator();

            // --- TAB 1: LOCAL CONTROLLER ---
            ui.horizontal(|ui| {
                if ui.button(if *self.is_local_running.lock().unwrap() { "Hentikan Gopher Lokal" } else { "Mulai Gopher Lokal" }).clicked() {
                    let mut running = self.is_local_running.lock().unwrap();
                    *running = !*running;
                    if *running {
                        self.local_status = "Status: Aktif (Membaca USB)".to_string();
                        let r = self.is_local_running.clone();
                        thread::spawn(move || run_local_gopher(r));
                    } else {
                        self.local_status = "Status: Mati".to_string();
                    }
                }
                ui.label(&self.local_status);
            });

            ui.separator();

            // --- TAB 2: ANDROID SERVER ---
            ui.horizontal(|ui| {
                if ui.button(if *self.is_server_running.lock().unwrap() { "Hentikan Server TCP" } else { "Mulai Server TCP" }).clicked() {
                    let mut running = self.is_server_running.lock().unwrap();
                    *running = !*running;
                    if *running {
                        self.server_status = "Status: Menunggu Android...".to_string();
                        let r = self.is_server_running.clone();
                        thread::spawn(move || run_android_server(r));
                    } else {
                        self.server_status = "Status: Mati".to_string();
                    }
                }
                ui.label(&self.server_status);
            });

            ui.horizontal(|ui| {
                ui.label("IP PC (Masukkan ke Android):");
                ui.text_edit_singleline(&mut self.ip_address.clone());
            });

            ui.separator();

            // --- TAB 3: CONFIG INFO ---
            ui.label("Config Editor: Silakan buka file 'config-editor.html' di browser Anda.");
            ui.label("Atur tombol sesuka hati, lalu simpan 'config.ini' di folder yang sama.");
        });
    }
}

fn main() -> Result<(), eframe::Error> {
    let options = eframe::NativeOptions {
        viewport: egui::ViewportBuilder::default().with_inner_size([600.0, 400.0]),
        ..Default::default()
    };
    eframe::run_native(
        "DS4 Unified Rust",
        options,
        Box::new(|_cc| Box::<DS4App>::default()),
    )
}

// ================= LOGIKA LOCAL GOPHER =================
fn run_local_gopher(is_running: Arc<Mutex<bool>>) {
    let mut gilrs = Gilrs::new().unwrap();

    while *is_running.lock().unwrap() {
        while let Some(_event) = gilrs.next_event() {}
        
        let mut gamepad = None;
        for (_id, gp) in gilrs.gamepads() {
            if gp.is_connected() {
                gamepad = Some(gp);
                break;
            }
        }

        if let Some(gp) = gamepad {
            let lx = gp.value(Axis::LeftStickX);
            let ly = gp.value(Axis::LeftStickY);
            
            if lx.abs() > 0.1 || ly.abs() > 0.1 {
                move_mouse((lx * 15.0) as i32, (ly * 15.0) as i32);
            }

            if gp.is_pressed(Button::South) {
                mouse_click(true);
            } else {
                mouse_click(false);
            }
        }
        
        thread::sleep(std::time::Duration::from_millis(16));
    }
}

// ================= LOGIKA ANDROID SERVER =================
fn run_android_server(is_running: Arc<Mutex<bool>>) {
    let client = Client::connect().expect("ViGEmBus tidak terinstal!");
    
    // Beralih ke Xbox360Wired yang jauh lebih stabil
    let id = TargetId::XBOX360_WIRED;
    let mut target = Xbox360Wired::new(client, id);
    
    target.plugin().expect("Gagal memasang virtual controller");
    target.wait_ready().expect("Virtual controller belum siap");

    let listener = TcpListener::bind("0.0.0.0:27020").expect("Gagal bind port");
    listener.set_nonblocking(true).unwrap();

    println!("Server TCP berjalan di port 27020...");

    while *is_running.lock().unwrap() {
        match listener.accept() {
            Ok((mut stream, _)) => {
                println!("Android Terhubung!");
                let mut buf = [0u8; 12];
                
                while *is_running.lock().unwrap() {
                    if let Ok(bytes) = stream.read(&mut buf) {
                        if bytes == 12 && buf[0] == 0xAA {
                            let buttons_mask = u16::from_le_bytes([buf[1], buf[2]]);
                            
                            // Konversi bitmask dari Android ke XGamepad
                            let mut buttons = XButtons::default();
                            if (buttons_mask & 1) != 0 { buttons = buttons | XButtons::A; }
                            if (buttons_mask & 2) != 0 { buttons = buttons | XButtons::B; }
                            if (buttons_mask & 4) != 0 { buttons = buttons | XButtons::X; }
                            if (buttons_mask & 8) != 0 { buttons = buttons | XButtons::Y; }
                            if (buttons_mask & 16) != 0 { buttons = buttons | XButtons::LB; }
                            if (buttons_mask & 32) != 0 { buttons = buttons | XButtons::RB; }

                            let gamepad = XGamepad {
                                buttons,
                                thumb_lx: ((buf[3] as i16 - 128) * 256) as i16,
                                thumb_ly: ((buf[4] as i16 - 128) * 256) as i16,
                                thumb_rx: ((buf[5] as i16 - 128) * 256) as i16,
                                thumb_ry: ((buf[6] as i16 - 128) * 256) as i16,
                                ..Default::default()
                            };
                            
                            let _ = target.update(&gamepad);
                        }
                    } else {
                        break; // Disconnect
                    }
                }
            }
            Err(ref e) if e.kind() == std::io::ErrorKind::WouldBlock => {
                thread::sleep(std::time::Duration::from_millis(100));
            }
            Err(_) => break,
        }
    }
}

// ================= UTILS WINDOWS API =================
fn move_mouse(dx: i32, dy: i32) {
    let input = INPUT {
        r#type: INPUT_MOUSE,
        Anonymous: INPUT_0 {
            mi: MOUSEINPUT {
                dx, dy,
                mouseData: 0,
                dwFlags: MOUSEEVENTF_MOVE,
                time: 0,
                dwExtraInfo: 0,
            },
        },
    };
    unsafe { SendInput(&[input], std::mem::size_of::<INPUT>() as i32); }
}

fn mouse_click(down: bool) {
    let input = INPUT {
        r#type: INPUT_MOUSE,
        Anonymous: INPUT_0 {
            mi: MOUSEINPUT {
                dx: 0, dy: 0, mouseData: 0,
                dwFlags: if down { MOUSEEVENTF_LEFTDOWN } else { MOUSEEVENTF_LEFTUP },
                time: 0, dwExtraInfo: 0,
            },
        },
    };
    unsafe { SendInput(&[input], std::mem::size_of::<INPUT>() as i32); }
}

fn get_local_ip() -> String {
    use std::net::UdpSocket;
    let socket = UdpSocket::bind("0.0.0.0:0").unwrap();
    socket.connect("8.8.8.8:80").unwrap();
    socket.local_addr().unwrap().ip().to_string()
}
