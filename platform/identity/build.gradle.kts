// platform/identity — bounded context Identity (Phase 4 first half).
// Frontend para Keycloak OIDC + RBAC interno.

plugins {
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.springDependencyMgmt)
}

dependencies {
    implementation(libs.bundles.spring.base)
    implementation(libs.bundles.spring.web)
    implementation(libs.springBoot.starter.security)
    implementation(libs.springBoot.starter.oauth2.resource.server)
    implementation(libs.bundles.spring.modulith)
    implementation(libs.kotlin.reflect)
    implementation(libs.keycloak.admin.client)
    implementation(libs.nimbus.jose.jwt)

    testImplementation(libs.bundles.testing)
}
