use rusty_xinput::XInputHandle;
use std::thread;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;
use std::time::Duration;
use crate::input_utils;

pub fn run_xinput_gopher(is_running: Arc<AtomicBool>) {
    let handle = match XInputHandle::load_default() {
        Ok(h) => h,
        Err(_) => {
            println!("[ERROR] Gagal memuat XInput DLL. Pastikan DS4Windows atau driver XInput terinstal.");
            return;
        }
    };

    println!("[DEBUG] XInput berhasil dimuat. Menunggu controller...");

    let mut last_click = false;
    let mut accum_x = 0.0;
    let mut accum_y = 0.0;
    let mut debug_counter = 0;

    while is_running.load(Ordering::Relaxed) {
        match handle.get_state(0) {
            Ok(state) => {
                let lx = (state.raw.Gamepad.sThumbLX as f32) / 32767.0;
                let ly = (state.raw.Gamepad.sThumbLY as f32) / 32767.0;

                debug_counter += 1;
                if debug_counter >= 50 {
                    if lx.abs() > 0.1 || ly.abs() > 0.1 {
                        println!("[DEBUG] Stick: X={:.2} Y={:.2}", lx, ly);
                    }
                    debug_counter = 0;
                }

                let deadzone = 0.15;
                if lx.abs() > deadzone || ly.abs() > deadzone {
                    accum_x += lx * 15.0;
                    // PERBAIKAN: Tambahkan tanda minus di depan ly
                    // karena Y positif di XInput = Atas, tapi Y positif di Mouse = Bawah
                    accum_y += -ly * 15.0;
                    
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

                let current_click = (state.raw.Gamepad.wButtons & 0x1000) != 0;
                if current_click != last_click {
                    input_utils::mouse_click(current_click);
                    last_click = current_click;
                }
            }
            Err(_) => {
                if last_click {
                    input_utils::mouse_click(false);
                    last_click = false;
                }
                thread::sleep(Duration::from_millis(200));
            }
        }
        thread::sleep(Duration::from_millis(20));
    }
}