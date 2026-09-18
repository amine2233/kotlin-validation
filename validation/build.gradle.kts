import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    alias(libs.plugins.kotlin.jvm)
    `maven-publish`
}

group = "io.github.amine2233"
version = "0.1.0"

kotlin {
    explicitApi()
    compilerOptions {
        // Consumers on older Kotlin/JVM versions can still read the artifact.
        apiVersion = KotlinVersion.KOTLIN_2_2
        languageVersion = KotlinVersion.KOTLIN_2_2
        jvmTarget = JvmTarget.JVM_11
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
    withSourcesJar()
}

dependencies {
    testImplementation(libs.kotlin.test)
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "validation"
            from(components["java"])
            pom {
                name = "kotlin-validation"
                description = "Declarative, composable model validation for Kotlin — a port of amine2233/Validation (Swift)"
                url = "https://github.com/amine2233/kotlin-validation"
                licenses { license { name = "MIT" } }
            }
        }
    }
}
