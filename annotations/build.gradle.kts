plugins {
    kotlin("jvm")
}

val kotlinExposedVersion = "0.55.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.exposed:exposed-core:$kotlinExposedVersion")
    implementation("org.jetbrains.exposed:exposed-dao:$kotlinExposedVersion")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}