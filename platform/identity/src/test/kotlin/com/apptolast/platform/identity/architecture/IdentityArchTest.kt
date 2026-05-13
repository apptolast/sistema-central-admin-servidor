package com.apptolast.platform.identity.architecture

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import org.junit.jupiter.api.Test

class IdentityArchTest {
    private val classes = ClassFileImporter()
        .withImportOption(ImportOption.DoNotIncludeTests())
        .importPackages("com.apptolast.platform.identity")

    @Test
    fun `api package must not depend on other identity packages`() {
        noClasses()
            .that().resideInAPackage("..identity.api..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "..identity.domain..",
                "..identity.application..",
                "..identity.infrastructure..",
            )
            .check(classes)
    }

    @Test
    fun `domain must not depend on spring or jpa`() {
        noClasses()
            .that().resideInAPackage("..identity.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "org.springframework..",
                "jakarta.persistence..",
            )
            .check(classes)
    }
}
