rootProject.name = "FinKeeper24"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        // RuStore SDK (in-app updates) — внешний артефактори VK
        maven { url = uri("https://artifactory-external.vkpartner.ru/artifactory/maven") }
    }
}

include(":composeApp")
