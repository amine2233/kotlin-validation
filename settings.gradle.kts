pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "kotlin-validation"

include(":validation", ":samples:cli", ":samples:ktor")
if (System.getenv("ANDROID_HOME")?.let(::file)?.isDirectory == true || file("local.properties").exists()) {
    include(":samples:android")
}
