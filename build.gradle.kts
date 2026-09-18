plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.android.application) apply false
}

allprojects {
    group = "io.github.amine2233"
    version = (findProperty("version") as String?)?.takeIf { it.isNotBlank() } ?: "0.1.0-SNAPSHOT"
}
