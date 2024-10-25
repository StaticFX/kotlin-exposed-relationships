val projectVersion = "1.0.0"

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("com.vanniktech.maven.publish") version "0.28.0"
    id("org.jetbrains.dokka") version "1.9.20"
}

allprojects {
    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "org.jetbrains.dokka")
    apply(plugin = "maven-publish")
    apply(plugin = "java")

    java {
        withSourcesJar()
        withJavadocJar()
    }

    publishing {
        publications {
            create<MavenPublication>("maven") {
                groupId = project.group.toString()
                artifactId = project.name
                version = project.version.toString()
                from(components["java"])

                pom {
                    name.set("KSER")
                    description.set("Kotlin exposed serialized relationships")
                    url.set("https://github.com/StaticFX/kotlin-exposed-relationships")

                    licenses {
                        license {
                            name.set("GNU GENERAL PUBLIC LICENSE")
                            url.set("https://www.gnu.org/licenses/gpl-3.0.de.html")
                        }
                    }
                    developers {
                        developer {
                            id.set("staticfx")
                            name.set("StaticFX")
                            email.set("devin-fritz@gmx.de")
                        }
                    }
                }
            }
        }
    }
}