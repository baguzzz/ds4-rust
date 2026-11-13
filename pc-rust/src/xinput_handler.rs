use rusty_xinput::XInputHandle;
use std::thread;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;
use std::time::Duration;
use std::collections::HashMap;
use std::fs;
use crate::input_utils;

#[derive(Clone)]
struct Action {
    vk: u16,
    is_mouse_left: bool,
    is_mouse_right: bool,
}

// PERBAIKAN: Fungsi ini sekarang membuang awalan "0x" sebelum membaca Hex
fn parse_key(val: &str) -> Action {
    if val == "MouseLeft" { return Action { vk: 0, is_mouse_left: true, is_mouse_right: false }; }
    if val == "MouseRight" { return Action { vk: 0, is_mouse_left: false, is_mouse_right: true }; }
    if val.starts_with("Key:") {
        let hex_str = &val[4..];
        // Bersihkan awalan "0x" atau "0X"
        let clean_hex = hex_str.trim_start_matches("0x").trim_start_matches("0X");
        if let Ok(vk) = u16::from_str_radix(clean_hex, 16) {
            return Action { vk, is_mouse_left: false, is_mouse_right: false };
        }
    }
    Action { vk: 0, is_mouse_left: false, is_mouse_right: false }
}

fn load_bindings() -> HashMap<String, Action> {
    let mut map = HashMap::new();
    
    if let Ok(content) = fs::read_to_string("config.ini") {
        let mut section = String::new();
        for line in content.lines() {
            let line = line.trim();
            if line.is_empty() || line.starts_with('#') || line.starts_with(';') { continue; }
            if line.starts_with('[') && line.ends_with(']') {
                section = line[1..line.len()-1].to_string();
                continue;
            }
            if let Some((key, val)) = line.split_once('=') {
                let key = key.trim();
                let val = val.trim();
                if section == "Bindings" {
                    map.insert(key.to_string(), parse_key(val));
                }
            }
        }
        println!("[DEBUG] config.ini berhasil dibaca.");
    } else {
        println!("[WARN] config.ini tidak ditemukan! Menggunakan default.");
    }
    map
}

pub fn run_xinput_gopher(is_running: Arc<AtomicBool>) {
    let handle = match XInputHandle::load_default() {
        Ok(h) => h,
        Err(_) => {
            println!("[ERROR] Gagal memuat XInput DLL. Pastikan DS4Windows terinstal.");
            return;
        }
    };
    
    let bindings = load_bindings();
    
    let mut last_states: HashMap<String, bool> = HashMap::new();
    let mut accum_x = 0.0;
    let mut accum_y = 0.0;
    let mut accum_ry = 0.0;

    println!("[DEBUG] XInput berhasil dimuat. Menunggu controller...");

    while is_running.load(Ordering::Relaxed) {
        if let Ok(state) = handle.get_state(0) {
            let lx = (state.raw.Gamepad.sThumbLX as f32) / 32767.0;
            let ly = (state.raw.Gamepad.sThumbLY as f32) / 32767.0;
            let ry = (state.raw.Gamepad.sThumbRY as f32) / 32767.0;
            
            let deadzone = 0.15;

            // --- LEFT STICK -> MOUSE MOVE ---
            if lx.abs() > deadzone || ly.abs() > deadzone {
                accum_x += lx * 15.0;
                accum_y += -ly * 15.0; // Dibalik (Atas = positif XInput, Atas = negatif Mouse)
                let dx = accum_x as i32;
                let dy = accum_y as i32;
                if dx != 0 || dy != 0 {
                    input_utils::move_mouse_hybrid(dx, dy);
                    accum_x -= dx as f32;
                    accum_y -= dy as f32;
                }
            } else {
                accum_x = 0.0;
                accum_y = 0.0;
            }

            // --- RIGHT STICK -> SCROLL ---
            if ry.abs() > deadzone {
                accum_ry += ry * 40.0;
                let scroll = accum_ry as i32;
                if scroll != 0 {
                    input_utils::scroll_mouse(scroll);
                    accum_ry -= scroll as f32;
                }
            } else {
                accum_ry = 0.0;
            }

            // --- BUTTONS (LENGKAP) ---
            let buttons = state.raw.Gamepad.wButtons;
            
            let btn_map = vec![
                // Face Buttons
                ("Cross",     (buttons & 0x1000) != 0),
                ("Circle",    (buttons & 0x2000) != 0),
                ("Square",    (buttons & 0x4000) != 0),
                ("Triangle",  (buttons & 0x8000) != 0),
                
                // Bumpers
                ("L1",        (buttons & 0x0100) != 0),
                ("R1",        (buttons & 0x0200) != 0),
                
                // Start/Back
                ("Share",     (buttons & 0x0020) != 0), // Back / Share
                ("Options",   (buttons & 0x0010) != 0), // Start / Options
                
                // Stick Clicks (PERBAIKAN: L3 Ditambahkan)
                ("L3",        (buttons & 0x0040) != 0),
                ("R3",        (buttons & 0x0080) != 0),
                
                // D-Pad
                ("DpadUp",    (buttons & 0x0001) != 0),
                ("DpadDown",  (buttons & 0x0002) != 0),
                ("DpadLeft",  (buttons & 0x0004) != 0),
                ("DpadRight", (buttons & 0x0008) != 0),
                
                // Triggers (Analog, dianggap ditekan jika > 30)
                ("L2",        state.raw.Gamepad.bLeftTrigger > 30),
                ("R2",        state.raw.Gamepad.bRightTrigger > 30),
            ];

            // Eksekusi aksi berdasarkan config.ini
            for (name, use rusty_xinput::XInputHandle;
use std::thread;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;
use std::time::Duration;
use std::collections::HashMap;
use std::fs;
use crate::input_utils;

#[derive(Clone)]
struct Action {
    vk: u16,
    is_mouse_left: bool,
    is_mouse_right: bool,
}

// PERBAIKAN: Fungsi ini sekarang membuang awalan "0x" sebelum membaca Hex
fn parse_key(val: &str) -> Action {
    if val == "MouseLeft" { return Action { vk: 0, is_mouse_left: true, is_mouse_right: false }; }
    if val == "MouseRight" { return Action { vk: 0, is_mouse_left: false, is_mouse_right: true }; }
    if val.starts_with("Key:") {
        let hex_str = &val[4..];
        // Bersihkan awalan "0x" atau "0X"
        let clean_hex = hex_str.trim_start_matches("0x").trim_start_matches("0X");
        if let Ok(vk) = u16::from_str_radix(clean_hex, 16) {
            return Action { vk, is_mouse_left: false, is_mouse_right: false };
        }
    }
    Action { vk: 0, is_mouse_left: false, is_mouse_right: false }
}

fn load_bindings() -> HashMap<String, Action> {
    let mut map = HashMap::new();
    
    if let Ok(content) = fs::read_to_string("config.ini") {
        let mut section = String::new();
        for line in content.lines() {
            let line = line.trim();
            if line.is_empty() || line.starts_with('#') || line.starts_with(';') { continue; }
            if line.starts_with('[') && line.ends_with(']') {
                section = line[1..line.len()-1].to_string();
                continue;
            }
            if let Some((key, val)) = line.split_once('=') {
                let key = key.trim();
                let val = val.trim();
                if section == "Bindings" {
                    map.insert(key.to_string(), parse_key(val));
                }
            }
        }
        println!("[DEBUG] config.ini berhasil dibaca.");
    } else {
        println!("[WARN] config.ini tidak ditemukan! Menggunakan default.");
    }
    map
}

pub fn run_xinput_gopher(is_running: Arc<AtomicBool>) {
    let handle = match XInputHandle::load_default() {
        Ok(h) => h,
        Err(_) => {
            println!("[ERROR] Gagal memuat XInput DLL. Pastikan DS4Windows terinstal.");
            return;
        }
    };
    
    let bindings = load_bindings();
    
    let mut last_states: HashMap<String, bool> = HashMap::new();
    let mut accum_x = 0.0;
    let mut accum_y = 0.0;
    let mut accum_ry = 0.0;

    println!("[DEBUG] XInput berhasil dimuat. Menunggu controller...");

    while is_running.load(Ordering::Relaxed) {
        if let Ok(state) = handle.get_state(0) {
            let lx = (state.raw.Gamepad.sThumbLX as f32) / 32767.0;
            let ly = (state.raw.Gamepad.sThumbLY as f32) / 32767.0;
            let ry = (state.raw.Gamepad.sThumbRY as f32) / 32767.0;
            
            let deadzone = 0.15;

            // --- LEFT STICK -> MOUSE MOVE ---
            if lx.abs() > deadzone || ly.abs() > deadzone {
                accum_x += lx * 15.0;
                accum_y += -ly * 15.0; // Dibalik (Atas = positif XInput, Atas = negatif Mouse)
                let dx = accum_x as i32;
                let dy = accum_y as i32;
                if dx != 0 || dy != 0 {
                    input_utils::move_mouse_hybrid(dx, dy);
                    accum_x -= dx as f32;
                    accum_y -= dy as f32;
                }
            } else {
                accum_x = 0.0;
                accum_y = 0.0;
            }

            // --- RIGHT STICK -> SCROLL ---
            if ry.abs() > deadzone {
                accum_ry += ry * 40.0;
                let scroll = accum_ry as i32;
                if scroll != 0 {
                    input_utils::scroll_mouse(scroll);
                    accum_ry -= scroll as f32;
                }
            } else {
                accum_ry = 0.0;
            }

            // --- BUTTONS (LENGKAP) ---
            let buttons = state.raw.Gamepad.wButtons;
            
            let btn_map = vec![
                // Face Buttons
                ("Cross",     (buttons & 0x1000) != 0),
                ("Circle",    (buttons & 0x2000) != 0),
                ("Square",    (buttons & 0x4000) != 0),
                ("Triangle",  (buttons & 0x8000) != 0),
                
                // Bumpers
                ("L1",        (buttons & 0x0100) != 0),
                ("R1",        (buttons & 0x0200) != 0),
                
                // Start/Back
                ("Share",     (buttons & 0x0020) != 0), // Back / Share
                ("Options",   (buttons & 0x0010) != 0), // Start / Options
                
                // Stick Clicks (PERBAIKAN: L3 Ditambahkan)
                ("L3",        (buttons & 0x0040) != 0),
                ("R3",        (buttons & 0x0080) != 0),
                
                // D-Pad
                ("DpadUp",    (buttons & 0x0001) != 0),
                ("DpadDown",  (buttons & 0x0002) != 0),
                ("DpadLeft",  (buttons & 0x0004) != 0),
                ("DpadRight", (buttons & 0x0008) != 0),
                
                // Triggers (Analog, dianggap ditekan jika > 30)
                ("L2",        state.raw.Gamepad.bLeftTrigger > 30),
                ("R2",        state.raw.Gamepad.bRightTrigger > 30),
            ];

            // Eksekusi aksi berdasarkan config.ini
            for (name, is_pressed) in btn_map {
                let last_pressed = *last_states.get(name).unwrap_or(&false);
                if is_pressed != last_pressed {
                    if let Some(action) = bindings.get(name) {
                        if action.is_mouse_left {
                            input_utils::mouse_click(is_pressed);
                        } else if action.is_mouse_right {
                            input_utils::mouse_click_right(is_pressed);
                        } else if action.vk != 0 {
                            input_utils::key_press(action.vk, is_pressed);
                        }
                    }
                    last_states.insert(name.to_string(), is_pressed);
                }
            }
        }
        thread::sleep(Duration::from_millis(20));
    }
}is_pressed) in btn_map {
                let last_pressed = *last_states.get(name).unwrap_or(&false);
                if is_pressed != last_pressed {
                    if let Some(action) = bindings.get(name) {
                        if action.is_mouse_left {
                            input_utils::mouse_click(is_pressed);
                        } else if action.is_mouse_right {
                            input_utils::mouse_click_right(is_pressed);
                        } else if action.vk != 0 {
                            input_utils::key_press(action.vk, is_pressed);
                        }
                    }
                    last_states.insert(name.to_string(), is_pressed);
                }
            }
        }
        thread::sleep(Duration::from_millis(20));
    }
}