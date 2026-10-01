package io.github.mojri.hesabyar

/**
 * Marker interface for tests that touch the Rust native library (hesabyar_core).
 *
 * Used with JUnit 4 category to separate Rust-bridge tests into a dedicated
 * Gradle test task that runs with forkevery1 and maxparallelforks1 —
 * preventing JNI global-state leakage between test classes. All other tests
 * run with normal parallelism for faster execution.
 *
 * @see RustIsolationRule
 */
interface RustTest
