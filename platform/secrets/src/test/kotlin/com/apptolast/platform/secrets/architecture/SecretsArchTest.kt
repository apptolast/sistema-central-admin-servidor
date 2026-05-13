package com.apptolast.platform.secrets.architecture

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import org.junit.jupiter.api.Test

class SecretsArchTest {

    private val source = ClassFileImporter()
        .withImportOption(ImportOption.DoNotIncludeTests())
        .importPackages("com.apptolast.platform.secrets")

    @Test
    fun `domain must not depend on infrastructure`() {
        noClasses()
            .that().resideInAPackage("..secrets.domain..")
            .should().dependOnClassesThat().resideInAPackage("..secrets.infrastructure..")
            .check(source)
    }

    @Test
    fun `application must not depend on infrastructure`() {
        noClasses()
            .that().resideInAPackage("..secrets.application..")
            .should().dependOnClassesThat().resideInAPackage("..secrets.infrastructure..")
            .check(source)
    }

    @Test
    fun `domain must not depend on spring`() {
        noClasses()
            .that().resideInAPackage("..secrets.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "org.springframework..",
                "jakarta.persistence..",
            )
            .check(source)
    }

    @Test
    fun `no class exposes a plaintext field — defense in depth`() {
        // Anti-leak guard: ninguna clase pública debe tener un campo llamado
        // 'plaintext', 'password', 'secret', 'apiKey' como String.
        // Si alguien quiere ese dato, debe ir al Passbolt deeplink.
        noClasses()
            .that().resideInAPackage("..secrets.api..")
            .should().haveSimpleNameContaining("Plaintext")
            .check(source)
    }
}
