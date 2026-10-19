//! Launch background tools without a Windows console, while retaining redirected logs.
use std::ffi::OsStr;

pub fn command(program: impl AsRef<OsStr>) -> std::process::Command {
    let mut command = std::process::Command::new(program);
    #[cfg(target_os = "windows")]
    {
        use std::os::windows::process::CommandExt;
        command.creation_flags(0x08000000); // CREATE_NO_WINDOW
    }
    command.stdin(std::process::Stdio::null());
    command
}

pub fn async_command(program: impl AsRef<OsStr>) -> tokio::process::Command {
    let mut command = tokio::process::Command::new(program);
    #[cfg(target_os = "windows")]
    command.creation_flags(0x08000000); // CREATE_NO_WINDOW
    command.stdin(std::process::Stdio::null());
    command
}

#[cfg(all(test, target_os = "windows"))]
mod tests {
    const PROBE: &str = "process::tests::console_probe";

    // Run in a separate process, so this checks the actual Win32 console attachment.
    #[test]
    #[ignore = "invoked by the two background-process tests"]
    fn console_probe() {
        #[link(name = "kernel32")]
        unsafe extern "system" {
            fn GetConsoleWindow() -> *mut std::ffi::c_void;
        }
        assert!(unsafe { GetConsoleWindow() }.is_null(), "Background child acquired a console window");
        println!("unbox-probe-stdout");
        eprintln!("unbox-probe-stderr");
    }

    fn check(output: std::process::Output) {
        assert!(output.status.success(), "{}", String::from_utf8_lossy(&output.stderr));
        assert!(String::from_utf8_lossy(&output.stdout).contains("unbox-probe-stdout"));
        assert!(String::from_utf8_lossy(&output.stderr).contains("unbox-probe-stderr"));
    }

    #[test]
    fn background_process_has_no_console_and_keeps_output() {
        check(super::command(std::env::current_exe().unwrap())
            .args(["--exact", PROBE, "--ignored", "--nocapture"]).output().unwrap());
    }

    #[tokio::test]
    async fn async_background_process_has_no_console_and_keeps_output() {
        check(super::async_command(std::env::current_exe().unwrap())
            .args(["--exact", PROBE, "--ignored", "--nocapture"]).output().await.unwrap());
    }
}
