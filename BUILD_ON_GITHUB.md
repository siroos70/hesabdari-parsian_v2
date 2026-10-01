# HesabYaar Iran — APK build

This repository is an Android/Kotlin/Jetpack Compose application with a Rust core.
The GitHub Actions workflow builds and verifies a signed release APK.

- Minimum Android: API 26
- Target Android: API 36
- Compile SDK: 37
- Application ID: `io.github.mojri.hesabyar`
- Release artifact: `HesabYaar-Iran-v1.0.0-release.apk`

The workflow runs automatically on pushes to `main`/`master` and can also be started manually from **Actions → Build HesabYaar Iran APK → Run workflow**.
