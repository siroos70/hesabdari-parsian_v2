import com.android.build.api.artifact.SingleArtifact
import dev.detekt.gradle.Detekt
import java.security.MessageDigest
import javax.xml.parsers.DocumentBuilderFactory

val appId = "io.github.mojri.hesabyar"
val storePwdKey = "KEYSTORE_PASSWORD"
val keyAliasKey = "KEY_ALIAS"
val keyPasswordKey = "KEY_PASSWORD"

fun resolveCredential(key: String) =
  providers.gradleProperty(key).orNull
    ?: providers.environmentVariable(key).orNull
    ?: ""

fun runUniffiGen(
  libPath: File,
  outDir: File
): Int {
  val cmd =
    listOf(
      "cargo",
      "run",
      "--manifest-path",
      file("$rustDir/Cargo.toml").absolutePath,
      "--package",
      "uniffi-gen",
      "--",
      libPath.absolutePath,
      outDir.absolutePath,
    )
  val pb = ProcessBuilder(cmd)
  pb.directory(rustDir)
  // Redirect to temp files to avoid pipe-buffer deadlock on Windows
  val stdoutTmp = File.createTempFile("uniffi-gen-stdout-", ".log")
  val stderrTmp = File.createTempFile("uniffi-gen-stderr-", ".log")
  stdoutTmp.deleteOnExit()
  stderrTmp.deleteOnExit()
  pb.redirectOutput(stdoutTmp)
  pb.redirectError(stderrTmp)
  val proc = pb.start()
  val exitCode = proc.waitFor()
  if (exitCode != 0) {
    val stderrText = stderrTmp.readText().takeLast(4000)
    throw GradleException("uniffi-gen failed (exit $exitCode)\n$stderrText")
  }
  return exitCode
}

fun buildHostLibrary() {
  logger.lifecycle("Step 1/4: Building host-native Rust library...")
  val cargoBuild = ProcessBuilder("cargo", "build", "--release")
  cargoBuild.directory(rustDir)
  // Redirect to temp files to avoid pipe-buffer deadlock on Windows
  val stdoutTmp = File.createTempFile("cargo-build-stdout-", ".log")
  val stderrTmp = File.createTempFile("cargo-build-stderr-", ".log")
  stdoutTmp.deleteOnExit()
  stderrTmp.deleteOnExit()
  cargoBuild.redirectOutput(stdoutTmp)
  cargoBuild.redirectError(stderrTmp)
  val proc = cargoBuild.start()
  val exitCode = proc.waitFor()
  if (exitCode != 0) {
    val stderrText = stderrTmp.readText().takeLast(4000)
    throw GradleException("cargo build --release failed (exit $exitCode)\n$stderrText")
  }
}

/**
 * OS-specific host-native library file for JNA-backed unit tests.
 * Shared between [resolveHostArtifact] and the [buildHostRustLib] task to
 * guarantee both compute the same path without drift.
 */
fun hostLibFile(): File =
  rustTargetDir.resolve(
    when {
      System.getProperty("os.name").lowercase().contains("win") -> "release/hesabyar_core.dll"
      System.getProperty("os.name").lowercase().contains("mac") -> "release/libhesabyar_core.dylib"
      else -> "release/libhesabyar_core.so"
    }
  )

fun resolveHostArtifact(): File =
  hostLibFile().also { hostLib ->
    if (!hostLib.exists()) {
      throw GradleException("Host library not found at: ${hostLib.absolutePath}")
    }
    logger.lifecycle("Step 2/4: Host library at ${hostLib.name}")
  }

fun generateBindings(
  hostLib: File,
  outDir: File,
) {
  logger.lifecycle("Step 3/4: Generating UniFFI Kotlin bindings...")
  if (outDir.exists()) outDir.deleteRecursively()
  outDir.mkdirs()
  val exitCode = runUniffiGen(hostLib, outDir)
  if (exitCode != 0) {
    throw GradleException("UniFFI binding generation failed")
  }
}

fun patchAndInstallOutput(
  tempDir: File,
  dest: File,
) {
  logger.lifecycle("Step 4/4: Patching package and installing bindings...")
  val generatedKt =
    tempDir
      .walkTopDown()
      .firstOrNull { it.isFile && it.extension == "kt" }
      ?: throw GradleException("No .kt file found in ${tempDir.absolutePath}")

  val content = generatedKt.readText(Charsets.UTF_8)
  val patched =
    content.replace(
      Regex("^package uniffi\\.hesabyar_core$", RegexOption.MULTILINE),
      "package $appId.rust"
    )

  val templateFile = file("buildSrc/template/HesabyarCore.template.kt")
  val pkg = "$appId.rust"
  val compatObject = "\n" + templateFile.readText(Charsets.UTF_8).replace("__PKG__", pkg)

  dest.parentFile.mkdirs()
  dest.writeText(patched + compatObject, Charsets.UTF_8)
}

val versionMaxSegment = 99
val versionMajorMultiplier = 10000
val versionMinorMultiplier = 100

val versionText =
  providers
    .fileContents(
      rootProject.layout.projectDirectory.file("VERSION")
    ).asText
    .get()
    .trim()
val versionContent = versionText.split(".")
require(versionContent.size == 3) {
  "VERSION file must contain exactly 3 dot-separated segments (MAJOR.MINOR.PATCH), got: '$versionText'"
}
val versionMajor =
  versionContent[0].toIntOrNull()
    ?: throw GradleException("VERSION major segment is not a number: '${versionContent[0]}'")
val versionMinor =
  versionContent[1].toIntOrNull()
    ?: throw GradleException("VERSION minor segment is not a number: '${versionContent[1]}'")
val versionPatch =
  versionContent[2].toIntOrNull()
    ?: throw GradleException("VERSION patch segment is not a number: '${versionContent[2]}'")
require(versionMajor >= 0) { "VERSION major must be >= 0, got: $versionMajor" }
require(versionMinor in 0..versionMaxSegment) {
  "VERSION minor must be 0-$versionMaxSegment, got: $versionMinor"
}
require(versionPatch in 0..versionMaxSegment) {
  "VERSION patch must be 0-$versionMaxSegment, got: $versionPatch"
}
val appVersionName = "$versionMajor.$versionMinor.$versionPatch"
val appVersionCode =
  versionMajor * versionMajorMultiplier + versionMinor * versionMinorMultiplier + versionPatch

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
  alias(libs.plugins.ktlint)
  alias(libs.plugins.hilt)
  alias(libs.plugins.detekt)
  jacoco
}

// Rust cross-compilation target table. `abi` matches Android's ABI name used in
// split configs and jniLibs paths; `triple` is the cargo target triple.
data class RustTarget(
  val abi: String,
  val triple: String,
  val jniLib: String,
)

val rustTargets =
  listOf(
    RustTarget("arm64-v8a", "aarch64-linux-android", "libhesabyar_core.so"),
    RustTarget("armeabi-v7a", "armv7-linux-androideabi", "libhesabyar_core.so"),
    RustTarget("x86_64", "x86_64-linux-android", "libhesabyar_core.so"),
    RustTarget("x86", "i686-linux-android", "libhesabyar_core.so"),
  )

// Subset of rustTargets actually cross-compiled. Defaults to all; override with
// -PrustAbis=arm64-v8a to skip the 32-bit / x86 native builds on a local
// 64-bit-only device (speeds up installDebug dramatically). Ignored for
// release/bundle builds so shipped artifacts stay complete.
val rustAbisProp = providers.gradleProperty("rustAbis").getOrNull()
val activeRustTargets =
  if (gradle.startParameter.taskNames.any {
      it.contains("bundle", ignoreCase = true) || it.contains("Release", ignoreCase = true)
    } ||
    rustAbisProp.isNullOrBlank()
  ) {
    rustTargets
  } else {
    rustTargets.filter { it.abi in rustAbisProp.split(",").map { a -> a.trim() } }
  }

android {
  namespace = appId
  compileSdk = 37

  defaultConfig {
    applicationId = appId
    minSdk = 26
    targetSdk = 36
    this.versionCode = appVersionCode
    this.versionName = appVersionName

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    // BACKUP_SCHEMA_VERSION is the single source of truth in the Rust core
    // (rust/hesabyar-core/src/models/mod.rs). Derive it here so the Kotlin side
    // can never drift from the Rust side.
    val modRs = file("${rootProject.projectDir}/rust/hesabyar-core/src/models/mod.rs").readText()
    val schemaMatch = Regex("""pub const BACKUP_SCHEMA_VERSION:\s*i32\s*=\s*(\d+)""").find(modRs)
    val backupSchemaVersion =
      schemaMatch?.groupValues?.get(1)
        ?: error("Could not find BACKUP_SCHEMA_VERSION in rust/hesabyar-core/src/models/mod.rs")
    buildConfigField("int", "BACKUP_SCHEMA_VERSION", backupSchemaVersion)
  }

  signingConfigs {
    create("release") {
      storeFile = file("$rootDir/my-upload-key.jks")
      storePassword = resolveCredential(storePwdKey)
      keyAlias = resolveCredential(keyAliasKey)
      keyPassword = resolveCredential(keyPasswordKey)
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = true
      isShrinkResources = true
      isDebuggable = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      isDebuggable = true
    }
  }

  // Per-ABI APKs (arm-v7a, arm-v8a, x86_64) + a universal APK are shipped for
  // direct (GitHub release) distribution so users grab the smallest build.
  // The AAB, by contrast, must NOT use ABI splits: with them enabled AGP emits
  // one shrunk-resources file per ABI and `buildReleasePreBundle` then fails
  // with "Multiple shrunk-resources files found" (issuetracker.google.com/402800800).
  // The AAB already carries every ABI, so Play splits it on delivery anyway.
  //
  // `enableAbiSplits` lets CI build the APKs with splits and the AAB without:
  //   ./gradlew assembleRelease -PenableAbiSplits=true
  //   ./gradlew bundleRelease    -PenableAbiSplits=false
  // An explicit, non-blank property always wins. A blank/empty value (e.g.
  // `-PenableAbiSplits=`) is ignored and falls back to the default rather than
  // silently disabling splits. The default otherwise is NDK presence, BUT a
  // bundle task (e.g. `bundleRelease`) forces splits OFF regardless of the NDK:
  // building an AAB with ABI splits enabled makes AGP emit multiple
  // shrunk-resources files and `buildReleasePreBundle` fails (issuetracker
  // 402800800). This keeps `./gradlew bundleRelease` safe on an NDK-equipped
  // machine too, while a local `assembleRelease` (no NDK) still produces a
  // single fat APK instead of several broken per-ABI builds missing the lib.
  val buildingBundle =
    gradle.startParameter.taskNames.any { it.contains("bundle", ignoreCase = true) }
  val ndkPresent = providers.environmentVariable("ANDROID_NDK_HOME").isPresent
  val defaultAbiSplits = ndkPresent && !buildingBundle
  val explicitAbiSplits = providers.gradleProperty("enableAbiSplits").getOrNull()
  val enableAbiSplits =
    if (explicitAbiSplits.isNullOrBlank()) defaultAbiSplits else explicitAbiSplits.toBoolean()
  // rustAbis narrows the ABIs for a lighter LOCAL debug build (e.g.
  // -PrustAbis=arm64-v8a for a 64-bit-only phone). It is intentionally ignored
  // for release/bundle builds so shipped artifacts always contain every ABI.
  val rustAbisProp = providers.gradleProperty("rustAbis").getOrNull()
  val abiIncludeList =
    if (buildingBundle ||
      gradle.startParameter.taskNames.any { it.contains("Release", ignoreCase = true) } ||
      rustAbisProp.isNullOrBlank()
    ) {
      listOf("armeabi-v7a", "arm64-v8a", "x86_64")
    } else {
      // Derive from activeRustTargets so a typo'd/unbuilt ABI in -PrustAbis can
      // never create a split APK with no matching native .so (UnsatisfiedLinkError).
      activeRustTargets.map { it.abi }
    }
  splits {
    abi {
      isEnable = enableAbiSplits
      reset()
      include(*abiIncludeList.toTypedArray())
      isUniversalApk = true
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions {
    unitTests {
      isIncludeAndroidResources = true
      isReturnDefaultValues = true
    }
  }
  lint {
    baseline = file("lint-baseline.xml")
  }
}

android.sourceSets.named("main") {
  java.srcDir("src/main/java/${appId.replace(".", "/")}/rust")
  jniLibs.srcDir("src/main/jniLibs")
}

// ── Test configuration ──────────────────────────────────────────────────
// Tests are split into two groups to balance speed vs. Rust JNI isolation:
//
// 1. Non-Rust tests (default task): Run with normal Gradle parallelism.
//    No fork-per-class overhead. Covers ~40 pure-Kotlin test classes.
//
// 2. Rust-bridge tests (testDebugUnitTestRust): Tagged with
//    @Category(RustTest::class). Run with forkEvery=1 and maxParallelForks=1
//    to prevent JNI global-state leakage across test classes.
//    Covers ~13 test classes that call into hesabyar_core.
//
// Run both groups:  ./gradlew test
// Run non-Rust only: ./gradlew testDebugUnitTest  (fast)
// Run Rust only:     ./gradlew testDebugUnitTestRust  (slow, isolated)

// Rust library path for JNA — only needed by the Rust test task.
val rustReleaseDir = file("${rootProject.projectDir}/rust/target/release")
val rustJvmArgs =
  listOf(
    "-Djna.library.path=${rustReleaseDir.absolutePath}",
    "-Djava.library.path=${rustReleaseDir.absolutePath}"
  )

// Configure all unit test tasks: JVM args for JNA/Rust library discovery and
// disable Conscrypt in Robolectric for compatibility across Java 17, 21, and 24+.
tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
  jvmArgs(rustJvmArgs)
  systemProperty("robolectric.conscryptMode", "OFF")
}

// After Android plugin configures the test variant, set up category filtering
// and create the Rust-only test task with JNI isolation.
afterEvaluate {
  // Main task: exclude Rust-tagged tests (fast, parallel).
  tasks.named<org.gradle.api.tasks.testing.Test>("testDebugUnitTest") {
    useJUnit {
      excludeCategories("io.github.mojri.hesabyar.RustTest")
    }
  }

  // Rust-bridge test task: clone config from testDebugUnitTest, then restrict
  // to Rust-tagged classes with fork-per-class isolation.
  val debugUnitTest = tasks.named<org.gradle.api.tasks.testing.Test>("testDebugUnitTest")
  val rustTest =
    tasks.register<org.gradle.api.tasks.testing.Test>("testDebugUnitTestRust") {
      description = "Runs Rust-bridge unit tests with JNI global-state isolation"
      group = "verification"

      // Inherit classpath and test classes from the debug unit test task.
      val sourceTask = debugUnitTest.get()
      classpath = sourceTask.classpath
      testClassesDirs = sourceTask.testClassesDirs
      jvmArgs(rustJvmArgs)

      // Ensure test classes are compiled AND the Rust host library exists before
      // JNA-backed tests attempt to load it. buildHostRustLib produces the host
      // library at rust/target/release that JNA loads; compileRustCore only
      // builds Android .so files via cargo-ndk and would require the NDK.
      dependsOn("compileDebugUnitTestKotlin")
      dependsOn("buildHostRustLib")

      useJUnit {
        includeCategories("io.github.mojri.hesabyar.RustTest")
      }
      // The Rust native library (hesabyar_core) uses global mutable state that is
      // not thread-safe and not resettable between test classes. forkEvery=1 creates
      // a fresh JVM per class so each gets a clean native library state.
      maxParallelForks = 1
      forkEvery = 1
    }

  // Ensure ./gradlew test runs BOTH non-Rust and Rust test groups.
  tasks.named("test") {
    dependsOn(rustTest)
  }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
}

tasks.register("checkSigningConfig") {
  group = "signing"
  description = "Validates that release signing credentials are configured"
  doFirst {
    val storePassword = resolveCredential(storePwdKey)
    val keyAlias = resolveCredential(keyAliasKey)
    val keyPassword = resolveCredential(keyPasswordKey)
    val keystoreFile = file("$rootDir/my-upload-key.jks")

    val issues = mutableListOf<String>()
    if (storePassword.isBlank()) issues.add("$storePwdKey is not set")
    if (keyAlias.isBlank()) issues.add("$keyAliasKey is not set")
    if (keyPassword.isBlank()) issues.add("$keyPasswordKey is not set")
    if (!keystoreFile.exists()) issues.add("Keystore file not found: my-upload-key.jks")

    if (issues.isNotEmpty()) {
      logger.warn("⚠ Signing configuration issues:")
      issues.forEach { logger.warn("  - $it") }
      logger.warn("Add signing credentials to your local .env file. See .env.example for reference.")
    } else {
      logger.lifecycle("✓ Signing configuration is valid.")
    }
  }
}

// ---------------------------------------------------------------------------
// verifyReleaseManifestVersion — regression guard for the versionCode/versionName
// manifest-injection bug. If defaultConfig.versionCode/versionName are not applied
// (e.g. Kotlin DSL name shadowing), the generated release manifest is missing
// android:versionCode / android:versionName, which makes `packageReleaseBundle`
// fail late with "Version code not found in manifest". This task fails fast,
// before bundleRelease, with a clear message.
//
// The manifest is obtained via the AGP Variant API (SingleArtifact.MERGED_MANIFEST)
// instead of a hardcoded intermediates path, so it stays stable across AGP versions.
// ---------------------------------------------------------------------------
androidComponents {
  // Select every variant whose build type is "release" (independent of product
  // flavors or future variant naming), so the guard works even once flavors are added.
  onVariants(selector().withBuildType("release")) { variant ->
    val capitalized = variant.name.replaceFirstChar { it.uppercase() }
    val verifyTaskName = "verify${capitalized}ManifestVersion"

    val mergedManifest = variant.artifacts.get(SingleArtifact.MERGED_MANIFEST)

    tasks.register(verifyTaskName) {
      group = "verification"
      description =
        "Fails if the generated $capitalized manifest lacks android:versionCode or android:versionName"

      // The artifact provider also wires the dependency on the task that
      // produces the merged manifest (process${capitalized}Manifest).
      inputs.file(mergedManifest)

      // Declare a marker output so Gradle can skip the task when the merged
      // manifest is unchanged. A task with no outputs is always out-of-date,
      // so without this the XML parse would re-run on every bundle build.
      val markerFile =
        layout.buildDirectory.file("intermediates/verification/verified_${variant.name}.txt")
      outputs.file(markerFile)

      doLast {
        val manifestFile = mergedManifest.get().asFile
        require(manifestFile.exists()) {
          "Release manifest not found at ${manifestFile.absolutePath}"
        }
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        val doc = factory.newDocumentBuilder().parse(manifestFile)
        val manifest =
          doc
            .getElementsByTagName("manifest")
            .item(0) as? org.w3c.dom.Element
            ?: throw GradleException(
              "FAIL: <manifest> element missing from generated $capitalized manifest " +
                "(${manifestFile.absolutePath}). The merged manifest is malformed or " +
                "missing its root <manifest> element."
            )
        val androidNs = "http://schemas.android.com/apk/res/android"
        val versionCode = manifest.getAttributeNS(androidNs, "versionCode")
        val versionName = manifest.getAttributeNS(androidNs, "versionName")

        if (versionCode.isBlank()) {
          throw GradleException(
            "FAIL: android:versionCode missing from generated $capitalized manifest " +
              "(${manifestFile.absolutePath}). defaultConfig.versionCode was likely not applied."
          )
        }
        if (versionCode.toLongOrNull() != appVersionCode.toLong()) {
          throw GradleException(
            "FAIL: android:versionCode mismatch in generated $capitalized manifest " +
              "(${manifestFile.absolutePath}). Expected $appVersionCode but found '$versionCode'. " +
              "defaultConfig.versionCode was likely overridden or not applied."
          )
        }
        if (versionName.isBlank()) {
          throw GradleException(
            "FAIL: android:versionName missing from generated $capitalized manifest " +
              "(${manifestFile.absolutePath}). defaultConfig.versionName was likely not applied."
          )
        }
        if (versionName != appVersionName) {
          throw GradleException(
            "FAIL: android:versionName mismatch in generated $capitalized manifest " +
              "(${manifestFile.absolutePath}). Expected '$appVersionName' but found '$versionName'. " +
              "defaultConfig.versionName was likely overridden or not applied."
          )
        }
        logger.lifecycle(
          "✓ $verifyTaskName: versionCode=$versionCode versionName=$versionName"
        )
        markerFile.get().asFile.writeText(
          "verified: versionCode=$versionCode versionName=$versionName"
        )
      }
    }

    // Guarantee the guard runs before the App Bundle for this variant is built,
    // so CI fails early. AGP registers `bundle${capitalized}` lazily, so use a
    // live matching collection — this also covers flavored bundle tasks such as
    // `bundleFreeRelease`.
    tasks.matching { it.name == "bundle$capitalized" }.configureEach {
      dependsOn(verifyTaskName)
    }
  }
}

tasks.register<JacocoReport>("jacocoTestReport") {
  // Coverage must include both the fast non-Rust tests and the isolated
  // Rust-bridge tests (testDebugUnitTestRust) — executionData below globs
  // every build/jacoco/*.exec, so both tasks must run before the report.
  dependsOn("testDebugUnitTest", "testDebugUnitTestRust")
  executionData.setFrom(fileTree("build/jacoco") { include("*.exec") })
  sourceDirectories.setFrom("src/main/java", "src/main/kotlin")
  classDirectories.setFrom(
    fileTree("build/intermediates/javac/debug/compileDebugJavaWithJavac/classes") { include("**/*.class") },
    fileTree("build/intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes") { include("**/*.class") }
  )
  reports {
    xml.required = true
    html.required = false
    csv.required = false
  }
}

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.text.google.fonts)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  // implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.security.crypto)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  // implementation(libs.coil.compose)
  implementation(libs.converter.moshi)
  implementation(libs.firebase.ai)
  implementation(libs.androidx.work.runtime.ktx)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  implementation(libs.retrofit)
  implementation(libs.hilt.android)
  ksp(libs.hilt.compiler)
  implementation(libs.hilt.navigation.compose)
  implementation(libs.sqlcipher)
  implementation(libs.biometric)
  implementation("net.java.dev.jna:jna:5.19.1@aar")
  // Plain JAR needed for JVM unit tests — the @aar only contains Android natives
  testImplementation("net.java.dev.jna:jna:5.19.1")
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  testImplementation(libs.mockwebserver)
  testImplementation("org.json:json:20260814")
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}

// ---------------------------------------------------------------------------
// detekt — static analysis for Kotlin
//
// Configuration lives in config/detekt/detekt.yml and excludes auto-generated
// UniFFI bindings (rust/uniffi/** and **/generated/**) so only our handwritten
// code is linted.
// ---------------------------------------------------------------------------
detekt {
  config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
  baseline = file("$rootDir/config/detekt/detekt-baseline.xml")
  buildUponDefaultConfig = true
  allRules = false
  autoCorrect = false
  ignoredBuildTypes = listOf("release")
}

// ktlint must not inspect auto-generated UniFFI bindings, only our handwritten
// code. The generateAndFixBindings task installs the generated binding into
// `hesabyar_core.kt`, and generateRustBindings emits into a generated/ or
// rust/uniffi/ subdirectory. Handwritten bridge/mapper files (RustBridge.kt,
// RustMappers.kt) stay in lint scope.
ktlint {
  filter {
    exclude("**/rust/uniffi/**", "**/generated/**", "**/rust/hesabyar_core.kt")
  }
}

tasks.withType<dev.detekt.gradle.Detekt>().configureEach {
  jvmTarget = "17"
  exclude("**/rust/uniffi/**", "**/generated/**", "**/rust/hesabyar_core.kt")
}
tasks.withType<dev.detekt.gradle.DetektCreateBaselineTask>().configureEach {
  jvmTarget = "17"
  exclude("**/rust/uniffi/**", "**/generated/**", "**/rust/hesabyar_core.kt")
}

// ---------------------------------------------------------------------------
// Rust shared-core cross-compilation
//
// Builds libhesabyar_core.so for each Android ABI via cargo-ndk and copies
// the binaries into the appropriate jniLibs directory.
//
// Prerequisites:
//   1. Install cargo-ndk:  cargo install cargo-ndk
//   2. Set ANDROID_NDK_HOME to your NDK installation (e.g. ~/Android/Sdk/ndk/27.0.12077973)
//   3. rustup target add aarch64-linux-android armv7-linux-androideabi i686-linux-android x86_64-linux-android
//
// Usage:
//   ./gradlew :app:assembleRust     — build all Rust .so files
//   ./gradlew :app:generateRustBindings — generate UniFFI Kotlin bindings
// ---------------------------------------------------------------------------
val rustDir = file("${rootProject.projectDir}/rust")
val rustTargetDir = file("${rootProject.projectDir}/rust/target")

// ---------------------------------------------------------------------------
// forceRustRegen — when true, the Rust → Kotlin binding pipeline always runs
// even if Gradle considers it UP-TO-DATE. We force this for CI and for any
// release/bundle build so a stale cached native library or binding can never
// slip into a shipped artifact. On a normal LOCAL debug build (not CI, not a
// release task) we keep Gradle's incremental up-to-date checks so an unchanged
// Rust core is not needlessly recompiled (cargo/uniffi are slow in this repo).
//
//   - CI:             GitHub Actions sets CI=true; honor it.
//   - release/bundle: any requested task name containing "Release" or "bundle".
// ---------------------------------------------------------------------------
val isCI = System.getenv("CI")?.equals("true", ignoreCase = true) == true
val isReleaseOrBundleTask =
  gradle.startParameter.taskNames.any {
    it.contains("Release", ignoreCase = true) || it.contains("bundle", ignoreCase = true)
  }
val forceRustRegen = isCI || isReleaseOrBundleTask
if (forceRustRegen) {
  logger.lifecycle(
    "Rust pipeline forced to run (CI=$isCI, release/bundle task=$isReleaseOrBundleTask)."
  )
}

// ---------------------------------------------------------------------------
// syncCoreVersion — auto-derive the Rust core version during binding builds
//
// The core is bundled (not published) and versioned independently from the
// Android app (see the root VERSION file). The semantic base (MAJOR.MINOR.PATCH)
// lives in rust/Cargo.toml [workspace.package].version and is bumped manually
// per SemVer. We append a deterministic build-metadata component (+) derived
// from the core source tree, so the effective core version changes whenever
// the core source changes (committed or not) without publishing.
// ---------------------------------------------------------------------------
fun readCoreBaseVersion(): String {
  val cargo = file("$rustDir/Cargo.toml").readText()
  val base =
    Regex("""\[workspace\.package\](?:\r?\n(?!\[)[^\r\n]*)*?\r?\n\s*version\s*=\s*"([^"]+)"""")
      .find(cargo)
      ?.groupValues
      ?.get(1)
      ?: error("Could not find [workspace.package].version in rust/Cargo.toml")
  return base.substringBefore('+') // strip any prior build metadata
}

fun computeCoreSourceMeta(): String {
  val md = MessageDigest.getInstance("SHA-256")
  // Include Cargo.toml so a manual base-version bump changes the metadata too.
  val cargoToml = file("$rustDir/Cargo.toml")
  if (cargoToml.exists()) {
    md.update("Cargo.toml".toByteArray())
    md.update(cargoToml.readBytes())
  }
  val srcDir = file("$rustDir/hesabyar-core/src")
  if (srcDir.exists()) {
    srcDir
      .walkTopDown()
      .filter {
        it.isFile &&
          it.extension == "rs" &&
          !it.relativeTo(srcDir).invariantSeparatorsPath.startsWith("generated/")
      }.sortedBy { it.relativeTo(srcDir).invariantSeparatorsPath }
      .forEach { f ->
        md.update(f.relativeTo(srcDir).invariantSeparatorsPath.toByteArray())
        val content =
          f.readText(charset = Charsets.UTF_8).replace("\r\n", "\n").replace("\r", "\n")
        md.update(content.toByteArray())
      }
  }
  return md.digest().joinToString("") { b -> "%02x".format(b.toInt() and 0xFF) }.take(7)
}

tasks.register("syncCoreVersion") {
  group = "rust"
  description =
    "Derive the Rust core version (base + source hash) and write it for the build"
  val genDir = file("$rustDir/hesabyar-core/src/generated")
  val genFile = file("$genDir/core_version.rs")
  inputs.files(fileTree(rustDir.resolve("hesabyar-core/src")) { exclude("**/generated/**") })
  inputs.file(rustDir.resolve("Cargo.toml"))
  outputs.file(genFile)
  // Local debug keeps incremental caching; CI/release always regenerates.
  outputs.upToDateWhen { !forceRustRegen }
  doLast {
    val base = readCoreBaseVersion()
    val meta = computeCoreSourceMeta()
    genDir.mkdirs()
    genFile.writeText(
      "// AUTO-GENERATED by Gradle task :app:syncCoreVersion — do not edit by hand.\n" +
        "pub const CORE_VERSION: &str = \"$base+$meta\";\n"
    )
    logger.lifecycle("Core version: $base+$meta")
  }
}

// Remove stale native libs for ABIs excluded by -PrustAbis so a prior full
// build's .so files are not packaged alongside the freshly built ones.
val excludedRustAbis = rustTargets.map { it.abi } - activeRustTargets.map { it.abi }
val cleanExcludedRustJniLibs by tasks.registering {
  group = "rust"
  description = "Delete jniLibs output for ABIs excluded by -PrustAbis"
  doLast {
    excludedRustAbis.forEach { abi ->
      val dir = file("$projectDir/src/main/jniLibs/$abi")
      if (dir.exists()) dir.deleteRecursively()
    }
  }
}

activeRustTargets.forEach { target ->
  val taskName = "assembleRust_${target.abi.replace("-", "_").replace(".", "_")}"
  val outDir = file("$projectDir/src/main/jniLibs/${target.abi}")
  val outputLib = file("$outDir/${target.jniLib}")
  tasks.register(taskName) {
    group = "rust"
    description = "Build Rust .so for ${target.abi}"
    dependsOn("syncCoreVersion", cleanExcludedRustJniLibs)
    inputs.dir(rustDir.resolve("hesabyar-core/src"))
    inputs.file(rustDir.resolve("hesabyar-core/Cargo.toml"))
    inputs.file(rustDir.resolve("Cargo.toml"))
    inputs.file(rustDir.resolve("Cargo.lock"))
    inputs.file(rustDir.resolve("hesabyar-core/build.rs"))
    outputs.file(outputLib)
    // Local debug keeps incremental caching; CI/release always recompiles.
    outputs.upToDateWhen { !forceRustRegen }
    doLast {
      val ndkHome = System.getenv("ANDROID_NDK_HOME")
      if (ndkHome.isNullOrBlank()) {
        throw GradleException(
          "ANDROID_NDK_HOME is not set.\n" +
            "Install the Android NDK and set ANDROID_NDK_HOME to its root directory.\n" +
            "Example: export ANDROID_NDK_HOME=~/Android/Sdk/ndk/27.0.12077973"
        )
      }
      // Always start from a clean ABI output dir. cargo-ndk writes the library
      // into a nested <abi>/ subfolder, so a stale top-level .so from a previous
      // build would otherwise shadow the freshly compiled one and get packaged
      // instead (causing UniFFI checksum mismatches at runtime).
      if (outDir.exists()) outDir.deleteRecursively()
      outDir.mkdirs()
      val cmd =
        listOf(
          "cargo",
          "ndk",
          "-t",
          target.triple,
          "-o",
          outDir.absolutePath,
          "build",
          "--release",
        )
      val pb = ProcessBuilder(cmd)
      pb.directory(rustDir)
      // Redirect stdout/stderr to temp files instead of inheritIO().
      // On Windows, cargo-ndk writes heavily to both streams; with inheritIO()
      // the pipe buffers fill up and deadlock the child process — the .so files
      // are produced but waitFor() never returns.
      val stdoutTmp = File.createTempFile("cargo-ndk-stdout-", ".log")
      val stderrTmp = File.createTempFile("cargo-ndk-stderr-", ".log")
      stdoutTmp.deleteOnExit()
      stderrTmp.deleteOnExit()
      pb.redirectOutput(stdoutTmp)
      pb.redirectError(stderrTmp)
      val proc = pb.start()
      val exitCode = proc.waitFor()
      // Surface cargo-ndk output on failure so the error is diagnosable
      if (exitCode != 0) {
        val stderrText = stderrTmp.readText().takeLast(4000)
        val stdoutText = stdoutTmp.readText().takeLast(2000)
        throw GradleException(
          "cargo ndk failed for ${target.abi} (exit $exitCode)\n" +
            "--- stderr (last 4000 chars) ---\n$stderrText\n" +
            "--- stdout (last 2000 chars) ---\n$stdoutText"
        )
      }
      val foundLib =
        outDir.walkTopDown().firstOrNull { it.name == target.jniLib }
          ?: throw GradleException(
            "Expected native library ${target.jniLib} not found for ${target.abi} at ${outDir.absolutePath}"
          )
      // Ensure the library is copied to the expected output location
      if (foundLib != outputLib) {
        foundLib.copyTo(outputLib, overwrite = true)
        // cargo-ndk emits into a nested <abi>/ subfolder; remove that leftover
        // so only the flat top-level .so is packaged (avoids duplicate/conflicting
        // native libraries and UniFFI checksum mismatches).
        val nestedDir = File(outDir, target.abi)
        if (nestedDir.exists() && nestedDir != outputLib.parentFile) {
          nestedDir.deleteRecursively()
        }
      }
    }
  }
}

tasks.register("assembleRust") {
  group = "rust"
  description = "Cross-compile Rust shared core for all Android ABIs via cargo-ndk"
  activeRustTargets.forEach { target ->
    val taskName = "assembleRust_${target.abi.replace("-", "_").replace(".", "_")}"
    dependsOn(taskName)
  }
}

// ---------------------------------------------------------------------------
// compileRustCore — auto-trigger Rust cross-compile before Kotlin compilation
//
// Lifecycle task that reuses the existing `assembleRust` task. Its inputs/outputs
// let Gradle skip the work when no Rust source changed, so it only runs when the
// native core actually needs rebuilding. Wired into the standard build so the
// native .so files are refreshed before Kotlin code is compiled.
//
// Requires ANDROID_NDK_HOME; the wiring below is skipped when the NDK is not
// configured, so NDK-less environments (e.g. unit-test CI) build as before.
// ---------------------------------------------------------------------------
tasks.register("compileRustCore") {
  group = "rust"
  description = "Recompile Rust core before Kotlin compilation when sources change"
  dependsOn("assembleRust", "syncCoreVersion")
  inputs.dir(rustDir.resolve("hesabyar-core/src"))
  inputs.file(rustDir.resolve("hesabyar-core/Cargo.toml"))
  inputs.file(rustDir.resolve("Cargo.toml"))
  inputs.file(rustDir.resolve("Cargo.lock"))
  inputs.file(rustDir.resolve("hesabyar-core/build.rs"))
  outputs.dir(file("$projectDir/src/main/jniLibs"))
  // Local debug keeps incremental caching; CI/release always recompiles.
  outputs.upToDateWhen { !forceRustRegen }
}

// ---------------------------------------------------------------------------
// buildHostRustLib — host-native Rust library for JNA-backed unit tests
//
// JNA-backed Rust tests (testDebugUnitTestRust) load the HOST library from
// rust/target/release (see rustJvmArgs), NOT the Android .so files produced by
// compileRustCore via cargo-ndk. Wiring the test task to this task instead of
// compileRustCore means `./gradlew test` no longer requires the Android NDK.
// ---------------------------------------------------------------------------
tasks.register("buildHostRustLib") {
  group = "rust"
  description = "Build the host-native Rust library loaded by JNA-backed unit tests"
  dependsOn("syncCoreVersion")
  inputs.dir(rustDir.resolve("hesabyar-core/src"))
  inputs.file(rustDir.resolve("hesabyar-core/Cargo.toml"))
  inputs.file(rustDir.resolve("Cargo.toml"))
  inputs.file(rustDir.resolve("Cargo.lock"))
  inputs.file(rustDir.resolve("hesabyar-core/build.rs"))
  val hostLib = hostLibFile()
  outputs.file(hostLib)
  // Local debug keeps incremental caching; CI/release always recompiles.
  outputs.upToDateWhen { !forceRustRegen }
  doLast {
    buildHostLibrary()
    if (!hostLib.exists()) {
      throw GradleException("Host library not found after build: ${hostLib.absolutePath}")
    }
  }
}

if (System.getenv("ANDROID_NDK_HOME").isNullOrBlank()) {
  logger.lifecycle(
    "ANDROID_NDK_HOME not set — skipping automatic Rust cross-compile before Kotlin compilation."
  )
} else {
  tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    dependsOn("compileRustCore")
  }

  // Gradle 9+ validates that a task consuming another task's outputs declares an
  // explicit dependency. The merge*JniLibFolders tasks read src/main/jniLibs,
  // which compileRustCore (via assembleRust) produces, so wire that dependency
  // explicitly to avoid implicit-dependency validation failures.
  tasks.configureEach {
    if (name.contains("merge", ignoreCase = true) && name.contains("JniLibFolders", ignoreCase = true)) {
      dependsOn("compileRustCore")
    }
  }
}

tasks.register("generateRustBindings") {
  group = "rust"
  description = "Generate UniFFI Kotlin bindings from a host-native build"
  dependsOn("syncCoreVersion")
  inputs.dir(rustDir.resolve("hesabyar-core/src"))
  inputs.file(rustDir.resolve("hesabyar-core/Cargo.toml"))
  inputs.file(rustDir.resolve("Cargo.toml"))
  inputs.file(rustDir.resolve("hesabyar-core/build.rs"))
  inputs.file(file("buildSrc/template/HesabyarCore.template.kt"))
  val dest = file("src/main/java/${appId.replace(".", "/")}/rust/hesabyar_core.kt")
  outputs.file(dest)
  // Local debug keeps incremental caching; CI/release always regenerates.
  outputs.upToDateWhen { !forceRustRegen }
  doLast {
    buildHostLibrary()
    val hostLib = resolveHostArtifact()
    val tempDir = file("${rootProject.buildDir}/tmp/uniffi-bindings")
    generateBindings(hostLib, tempDir)
    patchAndInstallOutput(tempDir, dest)
    tempDir.deleteRecursively()
    logger.lifecycle("Kotlin bindings installed at: ${dest.absolutePath}")
  }
}

// ---------------------------------------------------------------------------
// generateAndFixBindings — One-click UniFFI binding generation
//
// Builds a host-native Rust library, runs uniffi-gen on it, patches the
// package declaration, and installs the result into the Kotlin source tree.
//
// Usage:
//   ./gradlew :app:generateAndFixBindings
//
// No manual edits required after running this task.
// ---------------------------------------------------------------------------
tasks.register("generateAndFixBindings") {
  group = "rust"
  description = "Generate UniFFI Kotlin bindings, fix package, and install to source tree"
  dependsOn("syncCoreVersion")
  inputs.dir(rustDir.resolve("hesabyar-core/src"))
  inputs.file(rustDir.resolve("hesabyar-core/Cargo.toml"))
  inputs.file(rustDir.resolve("Cargo.toml"))
  inputs.file(rustDir.resolve("hesabyar-core/build.rs"))
  inputs.file(file("buildSrc/template/HesabyarCore.template.kt"))
  val dest = file("src/main/java/${appId.replace(".", "/")}/rust/hesabyar_core.kt")
  outputs.file(dest)
  // Local debug keeps incremental caching; CI/release always regenerates.
  outputs.upToDateWhen { !forceRustRegen }
  doLast {
    buildHostLibrary()
    val hostLib = resolveHostArtifact()
    val tempDir = file("${rootProject.buildDir}/tmp/uniffi-bindings")
    generateBindings(hostLib, tempDir)
    patchAndInstallOutput(tempDir, dest)

    tempDir.deleteRecursively()

    logger.lifecycle("Bindings installed at: ${dest.absolutePath}")
    logger.lifecycle("generateAndFixBindings completed successfully.")
  }
}

tasks.register("generateKeystore") {
  group = "signing"
  description = "Generates a release keystore for signing. Run manually: ./gradlew generateKeystore"
  doFirst {
    val storePassword = resolveCredential(storePwdKey)
    val keyPassword = resolveCredential(keyPasswordKey)
    val keyAlias =
      providers.gradleProperty(keyAliasKey).orNull
        ?: providers.environmentVariable(keyAliasKey).orNull
        ?: "mojrico"
    if (storePassword.isBlank() || keyPassword.isBlank()) {
      throw GradleException(
        "$storePwdKey and $keyPasswordKey must be set.\n" +
          "Add them to your local .env file or set as environment variables.\n" +
          "See .env.example for reference."
      )
    }
    val keystoreFile = File(rootDir, "my-upload-key.jks")
    if (!keystoreFile.exists()) {
      println("Generating release keystore...")
      val pb =
        ProcessBuilder(
          "keytool",
          "-genkey",
          "-noprompt",
          "-alias",
          keyAlias,
          "-dname",
          "CN=Hesabyar, OU=None, O=None, L=None, S=None, C=IR",
          "-keystore",
          keystoreFile.absolutePath,
          "-storepass",
          storePassword,
          "-keypass",
          keyPassword,
          "-keyalg",
          "RSA",
          "-keysize",
          "2048",
          "-validity",
          "10000"
        )
      val proc = pb.start()
      proc.waitFor()
      println("Keystore generated successfully at: ${keystoreFile.absolutePath}")
    } else {
      println("Keystore already exists, skipping generation.")
    }
  }
}
