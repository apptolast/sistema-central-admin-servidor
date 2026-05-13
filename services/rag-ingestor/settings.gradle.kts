rootProject.name = "rag-ingestor"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://repo.spring.io/milestone")
    }

    // Catalog via auto-discovery (copy `gradle/` from platform/ in CI).
}
