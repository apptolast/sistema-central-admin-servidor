// platform/observability — bounded context Observability (Phase 2).
//
// Responsabilidad: SLOs, alertas, snapshots de métricas/logs/traces.
// Adapter outbound principal: OTEL Collector + VictoriaMetrics (ver ADR-0006).

plugins {
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.kotlin.jpa)
    alias(libs.plugins.springDependencyMgmt)
}

dependencies {
    implementation(libs.bundles.spring.base)
    implementation(libs.bundles.spring.web)
    implementation(libs.bundles.spring.data)
    implementation(libs.bundles.spring.modulith)
    implementation(libs.kotlin.reflect)
    implementation(libs.opentelemetry.sdk)
    implementation(libs.micrometer.registry.prometheus)

    runtimeOnly(libs.flyway.postgresql)

    testImplementation(libs.bundles.testing)
    testRuntimeOnly("com.h2database:h2:2.3.232")
}
