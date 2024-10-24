plugins {
    kotlin("jvm")
    id("com.google.devtools.ksp")
    kotlin("plugin.serialization") version "2.0.20"
}

val kotlinExposedVersion = "0.55.0"
val kotlin_version: String = "2.0.21"

group = "statix.org"
version = "0.0.1"

dependencies {
    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.exposed:exposed-core:$kotlinExposedVersion")
    implementation("org.jetbrains.exposed:exposed-dao:$kotlinExposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$kotlinExposedVersion")
    implementation("com.h2database:h2:2.2.224")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation(project(":annotations"))
    implementation(project(":processor"))
    ksp(project(":processor"))

    testImplementation("org.jetbrains.kotlin:kotlin-test:$kotlin_version")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    sourceSets.main {
        kotlin.srcDir("build/generated/ksp/main/kotlin")
    }
}