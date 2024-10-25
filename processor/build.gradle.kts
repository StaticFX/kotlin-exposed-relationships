plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    `maven-publish`
    id("com.gradleup.shadow") version "8.3.0"
}

repositories {
}

java {
    withSourcesJar()
    withJavadocJar()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(project(":annotations"))
    implementation("com.google.devtools.ksp:symbol-processing-api:2.0.21-1.0.25")
    implementation("com.squareup:kotlinpoet-ksp:1.18.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
}

kotlin {
    jvmToolchain(21)
}


publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            groupId = "io.github.staticfx.kser"
            artifactId = "processor"
            version = rootProject.version.toString()
        }
    }
}

tasks {
    shadowJar {
        minimize()
    }

    build {
        dependsOn(shadowJar)
    }
}