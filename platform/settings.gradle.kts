rootProject.name = "apptolast-platform"

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
        maven("https://repo.spring.io/snapshot")
    }
}

// ============================================================================
// Monolito modular (Spring Modulith 2.0)
// ============================================================================
include(":platform-app")

// Bounded contexts — se irán activando por fase.
// Fase 1: inventory (ACTIVO)
// Fase 2: observability
// Fase 3: knowledge
// Fase 4: secrets + identity
// Fase 5: automation
include(":inventory")

// Pendientes (próximas fases):
// include(":secrets")
// include(":observability")
// include(":automation")
// include(":knowledge")
// include(":identity")
// include(":shared")
