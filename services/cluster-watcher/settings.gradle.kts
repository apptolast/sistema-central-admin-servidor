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

    // Reusa el version catalog del monolito — fuente única de verdad de versiones.
    versionCatalogs {
        create("libs") {
            from(files("../../platform/gradle/libs.versions.toml"))
        }
    }
}
