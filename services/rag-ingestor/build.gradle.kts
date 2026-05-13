// services/rag-ingestor — microservicio que polls el repo cada 5 min, detecta
// docs nuevos/cambiados, los chunkea, computa embeddings y los inserta en
// pgvector (postgres-vector ya existe en el cluster, ns n8n).

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

    // Spring AI + embeddings
    implementation(libs.springAi.openai)
    implementation(libs.springAi.pgvector)

    runtimeOnly(libs.flyway.postgresql)
    runtimeOnly(libs.pgvector.java)
    runtimeOnly(libs.postgresql.driver)

    // Observability
    implementation(libs.micrometer.registry.prometheus)
    implementation(libs.logstash.logback.encoder)

    testImplementation(libs.bundles.testing)
    testRuntimeOnly("com.h2database:h2:2.3.232")
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
    mainClass.set("com.apptolast.ragingestor.RagIngestorApplicationKt")
    buildInfo()
}

tasks.bootBuildImage {
    imageName.set("ghcr.io/apptolast/rag-ingestor:${project.version}")
    builder.set("paketobuildpacks/builder-jammy-base")
    environment.set(
        mapOf(
            "BP_JVM_VERSION" to "21",
            "BP_HEALTH_CHECKER_ENABLED" to "true",
        ),
    )
}
