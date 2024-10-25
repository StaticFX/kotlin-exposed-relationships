plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(project(":annotations"))
    implementation("com.google.devtools.ksp:symbol-processing-api:2.0.21-1.0.25")
    implementation("com.squareup:kotlinpoet-ksp:1.18.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
}

sourceSets.main {
    java.srcDirs("src/main/kotlin")
}