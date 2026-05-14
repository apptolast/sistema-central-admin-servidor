// services/rag-query — microservicio HTTP que cierra el loop knowledge:
// recibe pregunta → embed (Spring AI) → similarity search en pgvector →
// devuelve top-K chunks con cita formato [source: path#section@sha].
//
// Si confidence (max score cosine) < 0.55 → respuesta "no documented evidence",
// implementando la regla anti-hallucination de feedback_rag_anti_hallucination.

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.springBoot)
    alias(libs.plugins.springDependencyMgmt)
}

dependencies {
    implementation(libs.bundles.spring.base)
    implementation(libs.bundles.spring.web)
    implementation(libs.bundles.spring.data)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlinx.coroutines.core)

    // Spring AI (embeddings + pgvector store).
    implementation(libs.springAi.openai)
    implementation(libs.springAi.pgvector)

    runtimeOnly(libs.pgvector.java)
    runtimeOnly(libs.postgresql.driver)

    // Observability
    implementation(libs.micrometer.registry.prometheus)
    implementation(libs.logstash.logback.encoder)

    testImplementation(libs.bundles.testing)
}

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
}

springBoot {
    mainClass.set("com.apptolast.ragquery.RagQueryApplicationKt")
    buildInfo()
}

tasks.bootBuildImage {
    imageName.set("ghcr.io/apptolast/rag-query:${project.version}")
    builder.set("paketobuildpacks/builder-jammy-base")
    environment.set(
        mapOf(
            "BP_JVM_VERSION" to "21",
            "BP_HEALTH_CHECKER_ENABLED" to "true",
        ),
    )
}
