// ============================================================================
// Root project — convención común para todos los subproyectos del monolito.
// Versiones desde gradle/libs.versions.toml (version catalog).
// ============================================================================

plugins {
    alias(libs.plugins.kotlin.jvm)            apply false
    alias(libs.plugins.kotlin.spring)         apply false
    alias(libs.plugins.kotlin.jpa)            apply false
    alias(libs.plugins.springBoot)            apply false
    alias(libs.plugins.springDependencyMgmt)  apply false
    // NOTE: ktlint + detekt están comentados a propósito hasta que publiquen
    // versiones compatibles con Kotlin 2.3.21 (estado a 2026-05-13):
    //   - ktlint-gradle 12.1.1 → KtTokens.HEADER_KEYWORD not found (Kotlin 2.3 lexer cambió)
    //   - detekt 1.23.7       → ABI-locked a Kotlin 2.0.10
    // GreenhouseAdmin (reference visual) tampoco los usa actualmente. Quitar
    // el comentario cuando salga una release compatible (tracked en CI bumps).
    // alias(libs.plugins.ktlint)             apply false
    // alias(libs.plugins.detekt)             apply false
}

allprojects {
    group = "com.apptolast.platform"
    version = providers.gradleProperty("platformVersion").orNull ?: "0.1.0-SNAPSHOT"
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    // ktlint + detekt: disabled — ver nota arriba.

    repositories {
        mavenCentral()
        maven("https://repo.spring.io/milestone")
    }

    // Java toolchain — configurado vía extensión explícita (Gradle 9 prefiere esto a `java {}` dentro de subprojects).
    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
            freeCompilerArgs.addAll(
                "-Xjsr305=strict",
                // -Xcontext-receivers fue removido en Kotlin 2.3; los context parameters
                // (sucesor) están aún en preview y se activarán cuando los usemos en código.
            )
        }
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
        }
        systemProperty("spring.profiles.active", "test")
    }
}
