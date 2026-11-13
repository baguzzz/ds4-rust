use windows::Win32::UI::Input::KeyboardAndMouse::*;
use windows::Win32::Foundation::POINT;
use windows::Win32::UI::WindowsAndMessaging::{GetCursorPos, SetCursorPos};

pub fn move_mouse_hybrid(dx: i32, dy: i32) {
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
    
    // Fallback ke SetCursorPos jika SendInput gagal (misal karena UIPI)
    if result == 0 {
        unsafe {
            let mut pos = POINT { x: 0, y: 0 };
            if GetCursorPos(&mut pos).is_ok() {
                let _ = SetCursorPos(pos.x + dx, pos.y + dy);
            }
        }
    }
}

pub fn mouse_click(down: bool) {
    let input = INPUT {
        r#type: INPUT_MOUSE,
        Anonymous: INPUT_0 {
            mi: MOUSEINPUT {
                dx: 0, dy: 0, mouseData: 0,
                dwFlags: if down { MOUSEEVENTF_LEFTDOWN } else { MOUSEEVENTF_LEFTUP },
                time: 0,
                dwExtraInfo: 0,
            },
        },
    };
    unsafe { SendInput(&[input], std::mem::size_of::<INPUT>() as i32); }
}