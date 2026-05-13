rootProject.name = "cluster-watcher"

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

    // El version catalog se resuelve por auto-descubrimiento de Gradle:
    //   - Local dev: ejecutar `cp -r ../../platform/gradle .` antes de `./gradlew`
    //   - CI: el job ya hace el copy (.github/workflows/ci.yml)
    // Auto-discovery de `gradle/libs.versions.toml` registra el catalog "libs".
}
