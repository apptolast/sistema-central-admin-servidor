// platform/knowledge — bounded context Knowledge / RAG (Phase 3).
//
// Implementa la regla anti-alucinación de [[feedback_rag_anti_hallucination]]:
// citas obligatorias `[source: path#section@sha]`, score >= 0.55, sino la
// respuesta es "no encuentro evidencia documentada".

plugins {
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.kotlin.jpa)
    alias(libs.plugins.springDependencyMgmt)
}

// Spring Modulith 2.0.1 fuerza spring-core 7.0.2 (Spring Boot 4.0.1) via su transitive,
// pero el resto del stack (spring-web, spring-jdbc, spring-test) viene de Spring Boot
// 3.5.4 y está compilado contra spring-core 6.2.x. El skew rompe en runtime con
// NoClassDefFoundError: MimeType$SpecificityComparator (clase de 6.2 que 7.0 no tiene).
// Pinneamos spring-core al 6.2.9 que es lo que pide el resto de la cadena hasta que
// migremos a Spring Boot 4.0.x (fase 6 hardening + Java 25).
configurations.all {
    resolutionStrategy {
        force("org.springframework:spring-core:6.2.9")
    }
}

dependencies {
    implementation(libs.bundles.spring.base)
    implementation(libs.bundles.spring.web)
    implementation(libs.bundles.spring.data)
    implementation(libs.bundles.spring.modulith)
    implementation(libs.kotlin.reflect)
    implementation(libs.springAi.pgvector)
    implementation(libs.springAi.rag)
    implementation(libs.pgvector.java)

    runtimeOnly(libs.flyway.postgresql)

    // testing-lite porque MockRestServiceServer no necesita spring-modulith-starter-test
    // y evitamos la skew con KGP 2.3.21 documentada en InventoryControllerTest.
    testImplementation(libs.bundles.testing.lite)
    testRuntimeOnly("com.h2database:h2:2.3.232")
}
