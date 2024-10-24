rootProject.name = "kotlin-exposed-relationships"

pluginManagement {
    val kotlinVersion = "2.0.21"
    val kspVersion = "2.0.21-1.0.25"
    plugins {
        id("com.google.devtools.ksp") version kspVersion
        kotlin("jvm") version kotlinVersion
        kotlin("plugin.serialization") version "2.0.20"
    }
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

include(":plugin")
include(":annotations")
include(":processor")