use serialport::SerialPort;
use std::io::{Read, BufReader};
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;
use std::thread;
use std::time::Duration;
use crate::input_utils;

pub fn run_bluetooth_mouse(port_name: String, is_running: Arc<AtomicBool>) {
    let port = match serialport::new(&port_name, 115200)
        .timeout(Duration::from_millis(100))
        .open()
    {
        Ok(p) => p,
        Err(e) => {
            println!("Gagal membuka port {}: {}", port_name, e);
            return;
        }
    };

    println!("BT Mouse Server (Header 0xAA Sync) berjalan di {}.", port_name);

    let mut reader = BufReader::new(port);
    let mut header_buf = [0u8; 1];
    let mut payload_buf = [0u8; 4];
    let mut last_buttons = 0;
    let mut packet_count = 0;

    while is_running.load(Ordering::Relaxed) {
        // 1. Membaca byte per byte untuk mencari Header Sync Byte (0xAA)
        // Persis seperti mekanisme serial_server.rs pada androidapp Gamepad!
        match reader.read_exact(&mut header_buf) {
            Ok(()) => {
                if header_buf[0] == 0xAA {
                    // 2. Jika Header 0xAA terdeteksi, baca 4 byte sisanya (dx, dy, buttons, wheel)
                    if reader.read_exact(&mut payload_buf).is_ok() {
                        let dx = payload_buf[0] as i8 as i32;
                        let dy = payload_buf[1] as i8 as i32;
                        let buttons = payload_buf[2];
                        let wheel = payload_buf[3] as i8 as i32;

                        packet_count += 1;
                        if packet_count % 50 == 0 {
                            println!("Data Diterima: dx={}, dy={}, btn={}, whl={}", dx, dy, buttons, wheel);
                        }

                        // Gerakkan Mouse
                        if dx != 0 || dy != 0 {
                            input_utils::move_mouse_hybrid(dx, dy);
                        }

                        // Scroll Mouse
                        if wheel != 0 {
                            input_utils::scroll_mouse(wheel * 120);
                        }

                        // Klik Mouse (Edge Detection)
                        if buttons != last_buttons {
                            let left_down = (buttons & 1) != 0;
                            let right_down = (buttons & 2) != 0;

                            let left_last = (last_buttons & 1) != 0;
                            let right_last = (last_buttons & 2) != 0;

                            if left_down != left_last {
                                input_utils::mouse_click(left_down);
                            }
                            if right_down != right_last {
                                input_utils::mouse_click_right(right_down);
                            }
                            last_buttons = buttons;
                        }
                    }
                }
            }
            Err(ref e) if e.kind() == std::io::ErrorKind::TimedOut => {
                thread::sleep(Duration::from_millis(1));
            }
            Err(e) => {
                println!("Port Error: {}", e);
                thread::sleep(Duration::from_millis(50));
            }
        }
    }
}
