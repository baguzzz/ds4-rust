use eframe::egui;
use gilrs::{Gilrs, Button, Axis};
use std::net::TcpListener;
use std::io::Read;
use std::thread;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;
use vigem_client::{Client, TargetId, Xbox360Wired, XGamepad, XButtons};
use windows::Win32::UI::Input::KeyboardAndMouse::*;

// =====================================================================
//  STATE GLOBAL UNTUK UI
// =====================================================================
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
            ip_address: get_local_ip(),
            is_local_running: Arc::new(AtomicBool::new(false)),
            is_server_running: Arc::new(AtomicBool::new(false)),
        }
    }
}

// =====================================================================
//  IMPLEMENTASI UI (EGUI)
// =====================================================================
impl eframe::App for DS4App {
    fn update(&mut self, ctx: &egui::Context, _frame: &mut eframe::Frame) {
        egui::CentralPanel::default().show(ctx, |ui| {
            ui.heading("DS4 Unified - Rust Edition");
            ui.separator();

            // --- TAB 1: LOCAL CONTROLLER ---
            ui.group(|ui| {
                ui.label("Local Controller (USB/BT)");
                ui.horizontal(|ui| {
                    let is_running = self.is_local_running.load(Ordering::Relaxed);
                    if ui.button(if is_running { "Hentikan Gopher Lokal" } else { "Mulai Gopher Lokal" }).clicked() {
                        let new_running = !is_running;
                        self.is_local_running.store(new_running, Ordering::Relaxed);
                        if new_running {
                            self.local_status = "Status: Aktif (Membaca USB)".to_string();
                            let r = self.is_local_running.clone();
                            thread::spawn(move || run_local_gopher(r));
                        } else {
                            self.local_status = "Status: Mati".to_string();
                        }
                    }
                    ui.label(&self.local_status);
                });
            });

            ui.separator();

            // --- TAB 2: ANDROID SERVER ---
            ui.group(|ui| {
                ui.label("Android Server (Wi-Fi)");
                ui.horizontal(|ui| {
                    let is_running = self.is_server_running.load(Ordering::Relaxed);
                    if ui.button(if is_running { "Hentikan Server TCP" } else { "Mulai Server TCP" }).clicked() {
                        let new_running = !is_running;
                        self.is_server_running.store(new_running, Ordering::Relaxed);
                        if new_running {
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
            });

            ui.separator();

            // --- TAB 3: CONFIG INFO ---
            ui.group(|ui| {
                ui.label("Config Editor:");
                ui.label("Silakan buka file 'config-editor.html' di browser Anda.");
                ui.label("Atur tombol sesuka hati, lalu simpan 'config.ini' di folder yang sama.");
            });
        });
    }
}

// =====================================================================
//  ENTRY POINT
// =====================================================================
fn main() -> Result<(), eframe::Error> {
    let options = eframe::NativeOptions {
        viewport: egui::ViewportBuilder::default().with_inner_size([600.0, 450.0]),
        ..Default::default()
    };
    eframe::run_native(
        "DS4 Unified Rust",
        options,
        Box::new(|_cc| Box::<DS4App>::default()),
    )
}

// =====================================================================
//  LOGIKA LOCAL GOPHER (gilrs)
// =====================================================================
fn run_local_gopher(is_running: Arc<AtomicBool>) {
    let mut gilrs = match Gilrs::new() {
        Ok(g) => g,
        Err(_) => return,
    };

    while is_running.load(Ordering::Relaxed) {
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

// =====================================================================
//  LOGIKA ANDROID SERVER (TCP & ViGEmBus - FULL FIX)
// =====================================================================
fn run_android_server(is_running: Arc<AtomicBool>) {
    let client = match Client::connect() {
        Ok(c) => c,
        Err(_) => {
            println!("ViGEmBus tidak terinstal!");
            return;
        }
    };
    
    let id = TargetId::XBOX360_WIRED;
    let mut target = Xbox360Wired::new(client, id);
    
    if target.plugin().is_err() || target.wait_ready().is_err() {
        println!("Gagal memasang virtual controller");
        return;
    }

    let listener = match TcpListener::bind("0.0.0.0:27020") {
        Ok(l) => l,
        Err(e) => { println!("Bind error: {}", e); return; },
    };
    
    if let Err(e) = listener.set_nonblocking(true) {
        println!("Set nonblocking error: {}", e);
        return;
    }

    println!("Server TCP berjalan di port 27020. Menunggu HP...");

    while is_running.load(Ordering::Relaxed) {
        match listener.accept() {
            Ok((mut stream, _)) => {
                println!("Android Terhubung!");
                
                let _ = stream.set_nodelay(true);
                let _ = stream.set_read_timeout(Some(std::time::Duration::from_millis(100)));
                
                let mut buf = [0u8; 12];
                
                while is_running.load(Ordering::Relaxed) {
                    match stream.read(&mut buf) {
                        Ok(0) => {
                            println!("Android memutus koneksi.");
                            break;
                        }
                        Ok(bytes) if bytes == 12 && buf[0] == 0xAA => {
                            let buttons_mask = u16::from_le_bytes([buf[1], buf[2]]);
                            let mut raw_buttons = 0u16;
                            
                            // --- TOMBOL WAJAH (A, B, X, Y) ---
                            if (buttons_mask & 1) != 0 { raw_buttons |= XButtons::A; }       // Cross
                            if (buttons_mask & 2) != 0 { raw_buttons |= XButtons::B; }       // Circle
                            if (buttons_mask & 4) != 0 { raw_buttons |= XButtons::X; }       // Square
                            if (buttons_mask & 8) != 0 { raw_buttons |= XButtons::Y; }       // Triangle
                            
                            // --- BUMPER (L1, R1) ---
                            if (buttons_mask & 16) != 0 { raw_buttons |= XButtons::LB; }
                            if (buttons_mask & 32) != 0 { raw_buttons |= XButtons::RB; }

                            // --- START & BACK (Options & Share) ---
                            if (buttons_mask & 64) != 0 { raw_buttons |= XButtons::BACK; }   // Share
                            if (buttons_mask & 128) != 0 { raw_buttons |= XButtons::START; } // Options

                            // --- STICK CLICKS (L3, R3) ---
                            if (buttons_mask & 256) != 0 { raw_buttons |= XButtons::LTHUMB; }
                            if (buttons_mask & 512) != 0 { raw_buttons |= XButtons::RTHUMB; }

                            // --- D-PAD (buf[9]) ---
                            let dpad = buf[9];
                            if dpad == 0 || dpad == 1 || dpad == 7 { raw_buttons |= XButtons::UP; }
                            if dpad == 2 || dpad == 1 || dpad == 3 { raw_buttons |= XButtons::RIGHT; }
                            if dpad == 4 || dpad == 3 || dpad == 5 { raw_buttons |= XButtons::DOWN; }
                            if dpad == 6 || dpad == 5 || dpad == 7 { raw_buttons |= XButtons::LEFT; }

                            let buttons = XButtons { raw: raw_buttons };
                            
                            // --- ANALOG & TRIGGER ---
                            let gamepad = XGamepad {
                                buttons,
                                left_trigger: buf[7],   // L2
                                right_trigger: buf[8],  // R2
                                thumb_lx: ((buf[3] as i16 - 128) * 256) as i16,
                                thumb_ly: ((buf[4] as i16 - 128) * 256) as i16,  // DIHAPUS MINUSNYA
                                thumb_rx: ((buf[5] as i16 - 128) * 256) as i16,
                                thumb_ry: ((buf[6] as i16 - 128) * 256) as i16,  // DIHAPUS MINUSNYA
                            };
                            
                            // [DEBUG] Cetak data untuk melihat apakah Android mengirim data dengan benar
                            // Jika L2, R2, dpad bernilai 0 terus, berarti APK Android Anda versi lama.
                            if (buttons_mask != 0) || (dpad != 8) || (buf[7] != 0) || (buf[8] != 0) {
                                println!("Data -> Btn: {:#06X} | L2: {} | R2: {} | DPad: {} | X:{} Y:{}", 
                                    buttons_mask, buf[7], buf[8], dpad, buf[3], buf[4]);
                            }

                            if let Err(e) = target.update(&gamepad) {
                                println!("ViGEm Update Error: {:?}", e);
                            }
                        }
                        Ok(_) => continue,
                        Err(ref e) if e.kind() == std::io::ErrorKind::WouldBlock || e.kind() == std::io::ErrorKind::TimedOut => {
                            continue;
                        }
                        Err(e) => {
                            println!("Read Error: {}", e);
                            break;
                        }
                    }
                }
            }
            Err(ref e) if e.kind() == std::io::ErrorKind::WouldBlock => {
                thread::sleep(std::time::Duration::from_millis(50));
            }
            Err(e) => {
                println!("Accept Error: {}", e);
                break;
            }
        }
    }
}

// =====================================================================
//  UTILS: WINDOWS API (Mouse & IP)
// =====================================================================
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
    let socket = match UdpSocket::bind("0.0.0.0:0") {
        Ok(s) => s,
        Err(_) => return "127.0.0.1".to_string(),
    };
    if socket.connect("8.8.8.8:80").is_err() {
        return "127.0.0.1".to_string();
    }
    socket.local_addr().map(|a| a.ip().to_string()).unwrap_or_else(|_| "127.0.0.1".to_string())
}
