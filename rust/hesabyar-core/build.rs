use std::path::PathBuf;

const GENERATED_VERSION_REL: &str = "src/generated/core_version.rs";

fn main() {
    let generated = PathBuf::from(env!("CARGO_MANIFEST_DIR")).join(GENERATED_VERSION_REL);
    let version =
        read_generated_version(&generated).unwrap_or_else(|| env!("CARGO_PKG_VERSION").to_string());
    println!("cargo:rustc-env=CORE_VERSION={version}");
    println!("cargo:rerun-if-changed={}", generated.display());
    // Base version lives in Cargo.toml; re-run when it changes so manual bumps
    // propagate into CORE_VERSION.
    println!(
        "cargo:rerun-if-changed={}",
        PathBuf::from(env!("CARGO_MANIFEST_DIR"))
            .join("Cargo.toml")
            .display()
    );
}

/// Reads `pub const CORE_VERSION: &str = "...";` from the Gradle-generated file.
/// Falls back to the Cargo package version when the file is absent (e.g. plain
/// `cargo build`/`cargo test` outside the Gradle binding pipeline).
fn read_generated_version(path: &std::path::Path) -> Option<String> {
    let content = std::fs::read_to_string(path).ok()?;
    let line = content.lines().find(|l| l.contains("CORE_VERSION"))?;
    let start = line.find('"')?;
    let end = line[start + 1..].find('"').map(|i| start + 1 + i)?;
    Some(line[start + 1..end].to_string())
}
