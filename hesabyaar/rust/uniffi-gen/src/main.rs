use camino::Utf8PathBuf;
use uniffi_bindgen::bindings::{generate, GenerateOptions, TargetLanguage};

fn main() -> anyhow::Result<()> {
    let args: Vec<String> = std::env::args().collect();
    if args.len() < 3 {
        eprintln!("Usage: uniffi-gen <library-path> <out-dir>");
        std::process::exit(1);
    }

    let library_path = Utf8PathBuf::from(&args[1]);
    let out_dir = Utf8PathBuf::from(&args[2]);

    std::fs::create_dir_all(&out_dir)?;

    generate(GenerateOptions {
        languages: vec![TargetLanguage::Kotlin],
        source: library_path,
        out_dir,
        config_override: None,
        format: true,
        crate_filter: None,
        metadata_no_deps: false,
    })?;

    println!("Kotlin bindings written");
    Ok(())
}
