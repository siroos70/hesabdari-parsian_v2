// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.google.devtools.ksp) apply false
  alias(libs.plugins.roborazzi) apply false
  alias(libs.plugins.secrets) apply false
  alias(libs.plugins.ktlint) apply false
  alias(libs.plugins.hilt) apply false
  alias(libs.plugins.detekt) apply false
}

tasks.register("copyGitHooks") {
  description = "Copies pre-commit hook from scripts/ to .git/hooks/"
  doLast {
    val hook = file("${rootDir}/scripts/pre-commit")
    val hooksDir = file("${rootDir}/.git/hooks")
    if (hook.exists()) {
      copy {
        from(hook)
        into(hooksDir)
      }
      file("${hooksDir}/pre-commit").setExecutable(true)
      logger.lifecycle("✓ pre-commit hook installed")
    } else {
      logger.warn("⚠ scripts/pre-commit not found, skipping hook installation")
    }
  }
}

afterEvaluate {
  tasks.matching {
    it.name.contains("prepare", ignoreCase = true) || it.name == "preBuild"
  }.configureEach {
    dependsOn("copyGitHooks")
  }
}
