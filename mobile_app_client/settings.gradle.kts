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
        // RuStore SDK (in-app updates) — репозиторий RuStore (старый artifactory VK отключён с 01.10.2026)
        maven { url = uri("https://nexus-external.rustore.ru/repository/maven-rustore-exposed") }
    }
}

include(":composeApp")
