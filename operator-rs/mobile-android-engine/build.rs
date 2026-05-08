use std::env;

fn main() {
    let target_os = env::var("CARGO_CFG_TARGET_OS").unwrap_or_default();
    let target_arch = env::var("CARGO_CFG_TARGET_ARCH").unwrap_or_default();

    println!("cargo:rerun-if-changed=src/android_clear_cache.c");

    if target_os == "android" && matches!(target_arch.as_str(), "aarch64" | "x86_64") {
        cc::Build::new()
            .file("src/android_clear_cache.c")
            .flag_if_supported("-Wno-unused-parameter")
            .compile("operator_android_clear_cache");
    }
}
