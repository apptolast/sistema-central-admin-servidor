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

    versionCatalogs {
        create("libs") {
            from(files("../../platform/gradle/libs.versions.toml"))
        }
    }
}
