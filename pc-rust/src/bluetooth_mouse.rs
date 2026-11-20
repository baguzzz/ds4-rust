use serialport::SerialPort;
use std::io::Read;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;
use std::thread;
use std::time::Duration;
use crate::input_utils;

pub fn run_bluetooth_mouse(port_name: String, is_running: Arc<AtomicBool>) {
    let mut port = match serialport::new(&port_name, 115200)
        .timeout(Duration::from_millis(100))
        .open()
    {
        Ok(p) => p,
        Err(e) => {
            println!("Gagal membuka port {}: {}", port_name, e);
            return;
        }
    };

    println!("BT Mouse Server berjalan di {}.", port_name);

    let mut buf = [0u8; 4];
    let mut last_buttons = 0;
    let mut packet_count = 0;

    while is_running.load(Ordering::Relaxed) {
        match port.read_exact(&mut buf) {
            Ok(()) => {
                let dx = buf[0] as i8 as i32;
                let dy = buf[1] as i8 as i32;
                let buttons = buf[2];
                let wheel = buf[3] as i8 as i32;
                
                packet_count += 1;
                if packet_count % 50 == 0 {
                    println!("Data Diterima: dx={}, dy={}, btn={}, whl={}", dx, dy, buttons, wheel);
                }

                // 1. Gerakkan Mouse
                if dx != 0 || dy != 0 {
                    input_utils::move_mouse_hybrid(dx, dy);
                }

                // 2. Scroll
                if wheel != 0 {
                    input_utils::scroll_mouse(wheel * 120); 
                }

                // 3. Klik Mouse (Edge Detection)
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
            Err(ref e) if e.kind() == std::io::ErrorKind::TimedOut => {
                thread::sleep(Duration::from_millis(1));
            }
            Err(e) => {
                println!("Port Error: {}", e);
                let _ = port.clear(serialport::ClearBuffer::Input);
                thread::sleep(Duration::from_millis(50));
            }
        }
    }
}
