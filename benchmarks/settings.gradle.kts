rootProject.name = "hesabyar-benchmarks"

// Use the CodSpeed fork of JMH (vendored as a git submodule) as a composite
// build, substituting it for the upstream JMH artifacts. The fork emits
// walltime measurements that the CodSpeed runner collects in CI.
includeBuild("../third-party/codspeed-jvm/jmh-fork") {
    dependencySubstitution {
        substitute(module("org.openjdk.jmh:jmh-core")).using(project(":jmh-core"))
        substitute(module("org.openjdk.jmh:jmh-generator-annprocess")).using(project(":jmh-generator-annprocess"))
        substitute(module("org.openjdk.jmh:jmh-generator-bytecode")).using(project(":jmh-generator-bytecode"))
        substitute(module("org.openjdk.jmh:jmh-generator-reflection")).using(project(":jmh-generator-reflection"))
        substitute(module("org.openjdk.jmh:jmh-generator-asm")).using(project(":jmh-generator-asm"))
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}
