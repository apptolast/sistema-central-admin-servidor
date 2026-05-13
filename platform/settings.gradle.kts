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
// Fase 2: observability (SKELETON)
// Fase 3: knowledge (SKELETON)
// Fase 4: secrets + identity (secrets SKELETON, identity pendiente)
// Fase 5: automation (SKELETON)
include(":inventory")
include(":observability")
include(":knowledge")
include(":secrets")
include(":automation")
include(":identity")

// Pendientes (próximas fases):
// include(":shared")
