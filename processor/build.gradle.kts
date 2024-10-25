plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    `maven-publish`
}

repositories {
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

sourceSets.main {
    java.srcDirs("src/main/kotlin")
}