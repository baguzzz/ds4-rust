use serialport::SerialPort;
use std::io::{BufReader, Read};
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;
use std::thread;
use std::time::Duration;

use crate::input_utils;

const BUTTON_LEFT: u8 = 1;
const BUTTON_RIGHT: u8 = 2;
const BUTTON_ZOOM: u8 = 4;

pub fn run_bluetooth_mouse(port_name: String, is_running: Arc<AtomicBool>) {
    let port = match serialport::new(&port_name, 115200)
        .timeout(Duration::from_millis(100))
        .open()
    {
        Ok(port) => port,
        Err(error) => {
            println!("Gagal membuka port {}: {}", port_name, error);
            return;
        }
    };

    println!(
        "BT Mouse Server (Header 0xAA Sync) berjalan di {}.",
        port_name
    );

    let mut reader = BufReader::new(port);
    let mut header_buf = [0u8; 1];
    let mut payload_buf = [0u8; 4];
    let mut last_buttons = 0u8;
    let mut packet_count = 0u64;

    while is_running.load(Ordering::Relaxed) {
        match reader.read_exact(&mut header_buf) {
            Ok(()) => {
                if header_buf[0] != 0xAA {
                    continue;
                }

                if reader.read_exact(&mut payload_buf).is_err() {
                    continue;
                }

                let dx = payload_buf[0] as i8 as i32;
                let dy = payload_buf[1] as i8 as i32;
                let buttons = payload_buf[2];
                let wheel = payload_buf[3] as i8 as i32;

                packet_count += 1;
                if packet_count % 50 == 0 {
                    println!(
                        "Data diterima: dx={}, dy={}, buttons={}, wheel={}",
                        dx, dy, buttons, wheel
                    );
                }

                if dx != 0 || dy != 0 {
                    input_utils::move_mouse_hybrid(dx, dy);
                }

                if wheel != 0 {
                    if buttons & BUTTON_ZOOM != 0 {
                        // Tekan Ctrl selama pengiriman wheel agar aplikasi PC melakukan zoom.
                        input_utils::key_press(0x11, true);
                        input_utils::scroll_mouse(wheel * 120);
                        input_utils::key_press(0x11, false);
                    } else {
                        input_utils::scroll_mouse(wheel * 120);
                    }
                }

                let left_down = buttons & BUTTON_LEFT != 0;
                let right_down = buttons & BUTTON_RIGHT != 0;
                let left_was_down = last_buttons & BUTTON_LEFT != 0;
                let right_was_down = last_buttons & BUTTON_RIGHT != 0;

                if left_down != left_was_down {
                    input_utils::mouse_click(left_down);
                }

                if right_down != right_was_down {
                    input_utils::mouse_click_right(right_down);
                }

                last_buttons = buttons;
            }

            Err(ref error) if error.kind() == std::io::ErrorKind::TimedOut => {
                thread::sleep(Duration::from_millis(1));
            }

            Err(error) => {
                println!("Port Error: {}", error);
                thread::sleep(Duration::from_millis(50));
            }
        }
    }
}