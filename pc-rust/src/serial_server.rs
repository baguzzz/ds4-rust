use serialport::SerialPort;
use std::io::Read;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;
use std::time::Duration;
use vigem_client::{Client, TargetId, Xbox360Wired, XGamepad, XButtons};

pub fn run_bluetooth_server(port_name: String, is_running: Arc<AtomicBool>) {
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

    // Buka port COM
    let mut port = match serialport::new(&port_name, 115200)
        .timeout(Duration::from_millis(50))
        .open() 
    {
        Ok(p) => p,
        Err(e) => {
            println!("Gagal membuka port {}: {}", port_name, e);
            return;
        }
    };

    let mut buf = [0u8; 12];
    let mut byte_buf = [0u8; 1];

    println!("Bluetooth Server berjalan di {}. Menunggu HP...", port_name);

    while is_running.load(Ordering::Relaxed) {
        // Membaca 1 byte untuk mencari header 0xAA
        match port.read_exact(&mut byte_buf) {
            Ok(_) => {
                if byte_buf[0] == 0xAA {
                    buf[0] = 0xAA;
                    
                    // Baca 11 byte sisanya
                    if port.read_exact(&mut buf[1..]).is_ok() {
                        // Validasi Checksum (XOR dari byte 1 sampai 10)
                        let mut ck = 0;
                        for i in 1..11 {
                            ck ^= buf[i];
                        }
                        
                        // Hanya proses jika checksum cocok
                        if ck == buf[11] {
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
                            
                            // Update controller
                            if let Err(e) = target.update(&gamepad) {
                                println!("ViGEm Error: {:?}", e);
                            }
                        }
                    }
                }
            }
            Err(ref e) if e.kind() == std::io::ErrorKind::TimedOut => {
                continue; // Tidak ada data dalam 50ms, lanjutkan loop
            }
            Err(e) => {
                println!("Bluetooth Terputus: {}", e);
                break;
            }
        }
    }
    println!("Bluetooth Server berhenti.");
}