plugins {
    kotlin("jvm")
    `maven-publish`
}

val kotlinExposedVersion = "0.55.0"

repositories {
    mavenCentral()
}

java {
    withSourcesJar()
    withJavadocJar()
}

dependencies {
    implementation("org.jetbrains.exposed:exposed-core:$kotlinExposedVersion")
    implementation("org.jetbrains.exposed:exposed-dao:$kotlinExposedVersion")

    testImplementation(kotlin("test"))
}

subprojects {
    apply(plugin = "org.jetbrains.dokka")
}

kotlin {
    jvmToolchain(21)
}


publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            groupId = "io.github.staticfx.kser"
            artifactId = "annotations"
            version = rootProject.version.toString()
        }
    }
}

sourceSets.main {
    java.srcDirs("src/main/kotlin")
}

tasks.test {
    useJUnitPlatform()
}