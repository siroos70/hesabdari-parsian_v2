plugins {
    kotlin("jvm") version "2.2.10"
    id("me.champeau.jmh") version "0.7.2"
}

group = "io.github.mojri.hesabyar"
version = "1.0"

kotlin {
    jvmToolchain(21)
}

jmh {
    // Version of the CodSpeed JMH fork provided by the composite build.
    jmhVersion.set("0.2.0")

    benchmarkMode.set(listOf("avgt"))
    timeUnit.set("ns")
    warmupIterations.set(2)
    warmup.set("1s")
    iterations.set(3)
    timeOnIteration.set("1s")
    fork.set(3)
    resultFormat.set("JSON")

    // Force a System.gc() between iterations so heap state is reset before each
    // measurement starts. Without this, a GC pause can land inside the
    // measurement window and show up as a spurious regression in CI.
    forceGC = true
}

// The benchmarks exercise the real production code. Rather than duplicating it,
// we compile a curated set of self-contained, pure-JVM source files straight
// from the app module so there is a single source of truth.
kotlin.sourceSets.named("main") {
    kotlin.setSrcDirs(listOf("../app/src/main/java"))
    kotlin.include(
        "io/github/mojri/hesabyar/ui/JalaliCalendarHelper.kt",
        "io/github/mojri/hesabyar/core/MathUtils.kt",
    )
}
