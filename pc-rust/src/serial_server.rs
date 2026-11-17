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

    // Buka port COM dengan timeout pendek
    let mut port = match serialport::new(&port_name, 115200)
        .timeout(Duration::from_millis(10)) // Timeout sangat pendek agar tidak memblokir
        .open() 
    {
        Ok(p) => p,
        Err(e) => {
            println!("Gagal membuka port {}: {}", port_name, e);
            return;
        }
    };

    // Buffer besar untuk menampung data yang masuk
    let mut rolling_buf = [0u8; 1024];
    let mut rolling_len = 0;

    println!("Bluetooth Server berjalan di {}. Menunggu HP...", port_name);

    while is_running.load(Ordering::Relaxed) {
        // Baca data apa pun yang masuk ke buffer
        match port.read(&mut rolling_buf[rolling_len..]) {
            Ok(n) => {
                if n > 0 {
                    rolling_len += n;
                    let mut processed = 0;

                    // Proses buffer: cari pola 0xAA dan pastikan ada 12 byte
                    while rolling_len - processed >= 12 {
                        if rolling_buf[processed] == 0xAA {
                            // Cek Checksum (XOR dari byte 1 sampai 10)
                            let mut ck = 0;
                            for i in 1..11 {
                                ck ^= rolling_buf[processed + i];
                            }

                            // Jika Checksum valid, eksekusi ke ViGEm
                            if ck == rolling_buf[processed + 11] {
                                let buttons_mask = u16::from_le_bytes([rolling_buf[processed + 1], rolling_buf[processed + 2]]);
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

                                let dpad = rolling_buf[processed + 9];
                                if dpad == 0 || dpad == 1 || dpad == 7 { raw_buttons |= XButtons::UP; }
                                if dpad == 2 || dpad == 1 || dpad == 3 { raw_buttons |= XButtons::RIGHT; }
                                if dpad == 4 || dpad == 3 || dpad == 5 { raw_buttons |= XButtons::DOWN; }
                                if dpad == 6 || dpad == 5 || dpad == 7 { raw_buttons |= XButtons::LEFT; }

                                let buttons = XButtons { raw: raw_buttons };
                                
                                let lx_i32 = (rolling_buf[processed + 3] as i32 - 128) * 256;
                                let rx_i32 = (rolling_buf[processed + 5] as i32 - 128) * 256;
                                let ly_i32 = (128 - rolling_buf[processed + 4] as i32) * 256;
                                let ry_i32 = (128 - rolling_buf[processed + 6] as i32) * 256;

                                let gamepad = XGamepad {
                                    buttons,
                                    left_trigger: rolling_buf[processed + 7],
                                    right_trigger: rolling_buf[processed + 8],
                                    thumb_lx: lx_i32.clamp(-32768, 32767) as i16,
                                    thumb_ly: ly_i32.clamp(-32768, 32767) as i16,
                                    thumb_rx: rx_i32.clamp(-32768, 32767) as i16,
                                    thumb_ry: ry_i32.clamp(-32768, 32767) as i16,
                                };
                                
                                let _ = target.update(&gamepad);
                                
                                // Geser pointer sebanyak 12 byte karena 1 paket sudah diproses
                                processed += 12;
                                continue;
                            }
                        }
                        // Jika bukan 0xAA atau checksum gagal, geser 1 byte
                        processed += 1;
                    }

                    // Pindahkan sisa byte yang belum diproses ke awal buffer
                    if processed > 0 {
                        rolling_buf.copy_within(processed..rolling_len, 0);
                        rolling_len -= processed;
                    }
                }
            }
            Err(ref e) if e.kind() == std::io::ErrorKind::TimedOut => {
                continue; // Abaikan timeout, jangan matikan server
            }
            Err(e) => {
                println!("Port Error: {}", e);
                // Jangan break, coba bersihkan buffer dan lanjutkan
                let _ = port.clear(serialport::ClearBuffer::Input);
                rolling_len = 0; 
            }
        }
    }
    println!("Bluetooth Server berhenti.");
}