// ============================================================================
// services/cluster-watcher — microservicio extraído.
//
// Responsabilidad: observa el cluster K8s via fabric8 SharedInformer y publica
// eventos de cambio. En Fase 1 publica vía HTTP al platform (bus in-memory en
// el monolito). En Fase 2 publica a NATS JetStream (ver ADR-0005).
//
// Build standalone, NO comparte build con platform-app pero usa el mismo
// version catalog vía settings.gradle.kts.
// ============================================================================

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.springBoot)
    alias(libs.plugins.springDependencyMgmt)
}

dependencies {
    implementation(libs.bundles.spring.base)
    implementation(libs.bundles.spring.web)
    implementation(libs.springBoot.starter.webflux)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.reactor)

    // Kubernetes API client
    implementation(libs.fabric8.kubernetes.client)
    implementation(libs.fabric8.kubernetes.httpclient.jdk)

    // Observability
    implementation(libs.micrometer.registry.prometheus)
    implementation(libs.logstash.logback.encoder)

    // Test
    testImplementation(libs.bundles.testing)
}

// Toolchain JDK 21.
extensions.configure<JavaPluginExtension> {
    toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging { events("passed", "skipped", "failed") }
}

springBoot {
    mainClass.set("com.apptolast.clusterwatcher.ClusterWatcherApplicationKt")
    buildInfo()
}

tasks.bootBuildImage {
    imageName.set("ghcr.io/apptolast/cluster-watcher:${project.version}")
    builder.set("paketobuildpacks/builder-jammy-base")
    environment.set(
        mapOf(
            "BP_JVM_VERSION" to "21",
            "BP_HEALTH_CHECKER_ENABLED" to "true",
        ),
    )
}
