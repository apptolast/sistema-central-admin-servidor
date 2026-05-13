// platform/automation — bounded context Automation (Phase 5).
//
// Orquesta runbooks ejecutables + integra los 18 cluster-ops cronjobs
// existentes (NO los reemplaza — los expone como acciones de plataforma).

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
    implementation(libs.fabric8.kubernetes.client)

    runtimeOnly(libs.flyway.postgresql)

    testImplementation(libs.bundles.testing)
    testImplementation("io.fabric8:kubernetes-server-mock:7.0.1")
    testRuntimeOnly("com.h2database:h2:2.3.232")
}
