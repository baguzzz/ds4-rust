use eframe::egui;
use std::net::TcpListener;
use std::io::Read;
use std::thread;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;
use std::time::Duration;
use std::ffi::c_void;
use std::ptr;
use vigem_client::{Client, TargetId, Xbox360Wired, XGamepad, XButtons};
use windows::core::{Interface, GUID};
use windows::Win32::Devices::HumanInterfaceDevice::{
    DirectInput8Create, IDirectInput8W, IDirectInputDevice8W, DI8DEVCLASS_GAMECTRL,
    DIDEVICEINSTANCEW, DIEDFL_ATTACHEDONLY, DIRECTINPUT_VERSION, DIJOYSTATE2,
    DIPROPRANGE, DIPROPHEADER, DIPH_BYOFFSET, DISCL_BACKGROUND, DISCL_NONEXCLUSIVE,
    DIPROP_RANGE,
};
use windows::Win32::Foundation::{BOOL, FALSE, HINSTANCE, HWND, POINT};
use windows::Win32::System::Com::{CoInitializeEx, COINIT_APARTMENTTHREADED};
use windows::Win32::System::LibraryLoader::GetModuleHandleW;
use windows::Win32::UI::Input::KeyboardAndMouse::*;
use windows::Win32::UI::WindowsAndMessaging::{GetCursorPos, SetCursorPos};

// =====================================================================
//  KONSTANTA YANG HILANG DARI BINDINGS
// =====================================================================
const DIJOFS_X: u32 = 0;
const DIJOFS_Y: u32 = 4;
const DIJOFS_RX: u32 = 16;
const DIJOFS_RY: u32 = 20;

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
            ui.heading("DS4 Unified - Rust Edition (DirectInput)");
            ui.separator();

            ui.group(|ui| {
                ui.label("Local Controller (USB/BT) -> Menggerakkan Mouse PC");
                ui.horizontal(|ui| {
                    let is_running = self.is_local_running.load(Ordering::Relaxed);
                    if ui.button(if is_running { "Hentikan Gopher Lokal" } else { "Mulai Gopher Lokal" }).clicked() {
                        let new_running = !is_running;
                        self.is_local_running.store(new_running, Ordering::Relaxed);
                        if new_running {
                            self.local_status = "Status: Aktif (DirectInput)".to_string();
                            let r = self.is_local_running.clone();
                            let ctx_clone = ctx.clone();
                            thread::spawn(move || run_local_directinput(r, ctx_clone));
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
                            let ctx_clone = ctx.clone();
                            thread::spawn(move || run_android_server(r, ctx_clone));
                        } else {
                            self.server_status = "Status: Mati".to_string();
                        }
                    }
                    ui.label(&self.server_status);
                });
                ui.horizontal(|ui| {
                    ui.label("IP PC:");
                    ui.text_edit_singleline(&mut self.ip_address.clone());
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

// =====================================================================
//  DIRECTINPUT SETUP (Final Fixed)
// =====================================================================
struct DiContext {
    found_guid: Option<GUID>,
}

unsafe extern "system" fn enum_callback(
    pdidi: *mut DIDEVICEINSTANCEW,
    pvref: *mut c_void,
) -> BOOL {
    let ctx = &mut *(pvref as *mut DiContext);
    let dev = &*pdidi;
    ctx.found_guid = Some(dev.guidInstance);
    FALSE // Stop enumeration setelah dapat device pertama
}

fn init_directinput() -> Option<(IDirectInput8W, IDirectInputDevice8W)> {
    unsafe {
        // FIX 1: Gunakan let _ = untuk mengabaikan hasil Result
        let _ = CoInitializeEx(None, COINIT_APARTMENTTHREADED);
        
        let hinst: HINSTANCE = GetModuleHandleW(None).ok()?.into();
        
        let mut di_ptr: *mut c_void = ptr::null_mut();
        DirectInput8Create(
            hinst,
            DIRECTINPUT_VERSION,
            &IDirectInput8W::IID,
            &mut di_ptr as *mut *mut c_void,
            None,
        ).ok()?;
        
        if di_ptr.is_null() { return None; }
        let di = IDirectInput8W::from_raw(di_ptr);
        
        let mut ctx = DiContext { found_guid: None };
        di.EnumDevices(
            DI8DEVCLASS_GAMECTRL,
            Some(enum_callback),
            &mut ctx as *mut _ as *mut c_void,
            DIEDFL_ATTACHEDONLY,
        ).ok()?;
        
        let guid = ctx.found_guid?;
        
        let mut device_opt: Option<IDirectInputDevice8W> = None;
        // FIX 2: Tambahkan None sebagai argumen ketiga
        di.CreateDevice(&guid, &mut device_opt, None).ok()?;
        
        let device = device_opt?;
        
        // SetCooperativeLevel dengan DISCL_BACKGROUND agar bisa baca tanpa fokus
        device.SetCooperativeLevel(HWND(0), DISCL_BACKGROUND | DISCL_NONEXCLUSIVE).ok()?;
        
        let mut range = DIPROPRANGE {
            diph: DIPROPHEADER {
                dwSize: std::mem::size_of::<DIPROPRANGE>() as u32,
                dwHeaderSize: std::mem::size_of::<DIPROPHEADER>() as u32,
                dwObj: DIJOFS_X,
                dwHow: DIPH_BYOFFSET,
            },
            lMin: -32768,
            lMax: 32767,
        };
        
        let prop_range_guid = &DIPROP_RANGE as *const GUID;
        let _ = device.SetProperty(prop_range_guid, &mut range.diph);
        
        range.diph.dwObj = DIJOFS_Y;
        let _ = device.SetProperty(prop_range_guid, &mut range.diph);
        range.diph.dwObj = DIJOFS_RX;
        let _ = device.SetProperty(prop_range_guid, &mut range.diph);
        range.diph.dwObj = DIJOFS_RY;
        let _ = device.SetProperty(prop_range_guid, &mut range.diph);
        
        device.Acquire().ok()?;
        
        Some((di, device))
    }
}

// =====================================================================
//  LOCAL GOPHER (DirectInput - Anti Background Freeze)
// =====================================================================
fn run_local_directinput(is_running: Arc<AtomicBool>, ctx: egui::Context) {
    let (_di, device) = match init_directinput() {
        Some(d) => {
            println!("[DEBUG] DirectInput berhasil diinisialisasi");
            d
        }
        None => {
            println!("[ERROR] Gagal inisialisasi DirectInput");
            ctx.request_repaint();
            return;
        }
    };

    let mut last_click = false;
    let mut accum_x = 0.0;
    let mut accum_y = 0.0;

    while is_running.load(Ordering::Relaxed) {
        unsafe {
            if device.Poll().is_err() {
                let _ = device.Acquire();
                thread::sleep(Duration::from_millis(100));
                continue;
            }
            
            let mut state = DIJOYSTATE2::default();
            if device.GetDeviceState(
                std::mem::size_of::<DIJOYSTATE2>() as u32,
                &mut state as *mut _ as *mut c_void,
            ).is_err() {
                let _ = device.Acquire();
                continue;
            }

            let lx = (state.lX as f32) / 32767.0;
            let ly = (state.lY as f32) / 32767.0;
            
            let deadzone = 0.15;
            if lx.abs() > deadzone || ly.abs() > deadzone {
                accum_x += lx * 15.0;
                accum_y += ly * 15.0;
                let dx = accum_x as i32;
                let dy = accum_y as i32;
                if dx != 0 || dy != 0 {
                    move_mouse_hybrid(dx, dy);
                    accum_x -= dx as f32;
                    accum_y -= dy as f32;
                }
            } else {
                accum_x = 0.0;
                accum_y = 0.0;
            }

            let cross_pressed = (state.rgbButtons[1] & 0x80) != 0;
            if cross_pressed != last_click {
                mouse_click(cross_pressed);
                last_click = cross_pressed;
            }
        }
        
        thread::sleep(Duration::from_millis(20));
    }
    
    unsafe {
        let _ = device.Unacquire();
    }
    ctx.request_repaint();
}

// =====================================================================
//  ANDROID SERVER (TCP & ViGEmBus)
// =====================================================================
fn run_android_server(is_running: Arc<AtomicBool>, ctx: egui::Context) {
    unsafe {
        let _ = windows::Win32::System::Com::CoInitializeEx(
            None,
            windows::Win32::System::Com::COINIT_APARTMENTTHREADED,
        );
    }

    let client = match Client::connect() {
        Ok(c) => c,
        Err(_) => { println!("ViGEmBus tidak terinstal!"); return; }
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
                ctx.request_repaint();
                
                let _ = stream.set_nodelay(true);
                let _ = stream.set_nonblocking(false);
                
                let mut buf = [0u8; 12];
                
                while is_running.load(Ordering::Relaxed) {
                    match stream.read_exact(&mut buf) {
                        Ok(()) => {
                            if buf[0] == 0xAA {
                                let buttons_mask = u16::from_le_bytes([buf[1], buf[2]]);
                                let mut raw_buttons = 0u16;
                                
                                if (buttons_mask & 1) != 0 { raw_buttons |= XButtons::A; }
                                if (buttons_mask & 2) != 0 { raw_buttons |= XButtons::B; }
                                if (buttons_mask & 4) != 0 { raw_buttons |= XButtons::X; }
                                if (buttons_mask & 8) != 0 { raw_buttons |= XButtons::Y; }
                                if (buttons_mask & 16) != 0 { raw_buttons |= XButtons::LB; }
                                if (buttons_mask & 32) != 0 { raw_buttons |= XButtons::RB; }
                                if (buttons_mask & 64) != 0 { raw_buttons |= XButtons::BACK; }
                                if (buttons_mask & 128) != 0 { raw_buttons |= XButtons::START; }
                                if (buttons_mask & 256) != 0 { raw_buttons |= XButtons::LTHUMB; }
                                if (buttons_mask & 512) != 0 { raw_buttons |= XButtons::RTHUMB; }

                                let dpad = buf[9];
                                if dpad == 0 || dpad == 1 || dpad == 7 { raw_buttons |= XButtons::UP; }
                                if dpad == 2 || dpad == 1 || dpad == 3 { raw_buttons |= XButtons::RIGHT; }
                                if dpad == 4 || dpad == 3 || dpad == 5 { raw_buttons |= XButtons::DOWN; }
                                if dpad == 6 || dpad == 5 || dpad == 7 { raw_buttons |= XButtons::LEFT; }

                                let buttons = XButtons { raw: raw_buttons };
                                
                                let lx_i32 = (buf[3] as i32 - 128) * 256;
                                let rx_i32 = (buf[5] as i32 - 128) * 256;
                                let ly_i32 = (128 - buf[4] as i32) * 256;
                                let ry_i32 = (128 - buf[6] as i32) * 256;

                                let gamepad = XGamepad {
                                    buttons,
                                    left_trigger: buf[7],
                                    right_trigger: buf[8],
                                    thumb_lx: lx_i32.clamp(-32768, 32767) as i16,
                                    thumb_ly: ly_i32.clamp(-32768, 32767) as i16,
                                    thumb_rx: rx_i32.clamp(-32768, 32767) as i16,
                                    thumb_ry: ry_i32.clamp(-32768, 32767) as i16,
                                };
                                
                                let _ = target.update(&gamepad);
                            }
                        }
                        Err(e) => { println!("Android memutus: {}", e); break; }
                    }
                }
                ctx.request_repaint();
            }
            Err(ref e) if e.kind() == std::io::ErrorKind::WouldBlock => {
                thread::sleep(Duration::from_millis(50));
            }
            Err(e) => { println!("Accept Error: {}", e); break; }
        }
    }
    ctx.request_repaint();
}

// =====================================================================
//  UTILS
// =====================================================================
fn move_mouse_hybrid(dx: i32, dy: i32) {
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
    let result = unsafe { SendInput(&[input], std::mem::size_of::<INPUT>() as i32) };
    
    if result == 0 {
        unsafe {
            let mut pos = POINT { x: 0, y: 0 };
            if GetCursorPos(&mut pos).is_ok() {
                let _ = SetCursorPos(pos.x + dx, pos.y + dy);
            }
        }
    }
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