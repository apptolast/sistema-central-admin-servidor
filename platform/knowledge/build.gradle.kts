// platform/knowledge — bounded context Knowledge / RAG (Phase 3).
//
// Implementa la regla anti-alucinación de [[feedback_rag_anti_hallucination]]:
// citas obligatorias `[source: path#section@sha]`, score >= 0.6, sino la
// respuesta es "no encuentro evidencia documentada".

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
    implementation(libs.springAi.pgvector)
    implementation(libs.springAi.rag)
    implementation(libs.pgvector.java)

    runtimeOnly(libs.flyway.postgresql)

    testImplementation(libs.bundles.testing)
    testRuntimeOnly("com.h2database:h2:2.3.232")
}
