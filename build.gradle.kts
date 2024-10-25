val projectVersion = "1.0.0"

group = "org.statix"
version = projectVersion

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization") apply false
    id("org.jetbrains.dokka") version "1.9.20"
}

subprojects {
    apply(plugin = "org.jetbrains.dokka")
}

allprojects {
    repositories {
        mavenCentral()
        maven {
            setUrl("https://jitpack.io") //IMPORTANT BIT
        }
    }
}
