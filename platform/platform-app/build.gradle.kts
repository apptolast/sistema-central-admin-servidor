// ============================================================================
// platform-app — bootstrap del monolito modular.
// Empaqueta todos los módulos (cuando estén activos) en un único JAR ejecutable.
// ============================================================================

plugins {
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.kotlin.jpa)
    alias(libs.plugins.springBoot)
    alias(libs.plugins.springDependencyMgmt)
}

dependencies {
    // Core Spring + Kotlin
    implementation(libs.bundles.spring.base)
    implementation(libs.bundles.spring.web)
    implementation(libs.bundles.spring.data)
    implementation(libs.bundles.spring.modulith)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlinx.coroutines.core)

    // Persistence
    runtimeOnly(libs.flyway.postgresql)

    // Observability
    implementation(libs.micrometer.registry.prometheus)
    implementation(libs.logstash.logback.encoder)

    // Bounded context modules — se añaden via project(":<module>") cuando estén activos
    // implementation(project(":shared"))
    implementation(project(":inventory"))
    implementation(project(":observability"))
    implementation(project(":knowledge"))
    implementation(project(":secrets"))
    implementation(project(":automation"))
    implementation(project(":identity"))

    // Test
    testImplementation(libs.bundles.testing)
    testRuntimeOnly("com.h2database:h2:2.3.232")
}

springBoot {
    mainClass.set("com.apptolast.platform.PlatformApplicationKt")
    buildInfo()
}

tasks.bootBuildImage {
    imageName.set("ghcr.io/apptolast/platform:${project.version}")
    builder.set("paketobuildpacks/builder-jammy-base")
    environment.set(
        mapOf(
            "BP_JVM_VERSION" to "21",
            "BP_HEALTH_CHECKER_ENABLED" to "true",
        ),
    )
}
