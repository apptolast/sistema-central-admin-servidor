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
    alias(libs.plugins.ktlint)                apply false
    alias(libs.plugins.detekt)                apply false
}

allprojects {
    group = "com.apptolast.platform"
    version = providers.gradleProperty("platformVersion").orNull ?: "0.1.0-SNAPSHOT"
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    apply(plugin = "io.gitlab.arturbosch.detekt")

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
                "-Xcontext-receivers",
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
