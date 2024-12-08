rootProject.name = "kotlin-exposed-relationships"

pluginManagement {
    val kotlinVersion = "2.1.0"
    val kspVersion = "2.1.0-1.0.29"
    plugins {
        id("com.google.devtools.ksp") version kspVersion
        kotlin("jvm") version kotlinVersion
        kotlin("plugin.serialization") version "2.1.0"
    }
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

include(":plugin")
include(":annotations")
include(":processor")