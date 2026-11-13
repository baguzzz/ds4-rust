use rusty_xinput::*;
use std::thread;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;
use std::time::Duration;
use crate::input_utils;

pub fn run_xinput_gopher(is_running: Arc<AtomicBool>) {
    // 1. Muat DLL XInput secara dinamis
    if dynamic_load_xinput().is_err() {
        println!("[ERROR] Gagal memuat XInput DLL. Pastikan driver XInput terinstal (misal: DS4Windows).");
        return;
    }

    let mut last_click = false;
    let mut accum_x = 0.0;
    let mut accum_y = 0.0;

    while is_running.load(Ordering::Relaxed) {
        // 2. Baca state dari controller slot 0
        match xinput_get_state(0) {
            Ok(state) => {
                // Normalisasi analog (XInput: -32768..32767)
                let lx = (state.raw.sThumbLX as f32) / 32767.0;
                let ly = (state.raw.sThumbLY as f32) / 32767.0;

                let deadzone = 0.15;
                if lx.abs() > deadzone || ly.abs() > deadzone {
                    accum_x += lx * 15.0;
                    accum_y += ly * 15.0;
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

                // Cek tombol A (Cross)
                let current_click = (state.raw.wButtons & 0x1000) != 0;
                if current_click != last_click {
                    input_utils::mouse_click(current_click);
                    last_click = current_click;
                }
            }
            Err(_) => {
                // Controller tidak terdeteksi, tidur sebentar
                thread::sleep(Duration::from_millis(100));
            }
        }
        
        thread::sleep(Duration::from_millis(20));
    }
}