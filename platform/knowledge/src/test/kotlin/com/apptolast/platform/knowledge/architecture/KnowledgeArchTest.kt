package com.apptolast.platform.knowledge.architecture

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import org.junit.jupiter.api.Test

class KnowledgeArchTest {

    private val classes = ClassFileImporter()
        .withImportOption(ImportOption.DoNotIncludeTests())
        .importPackages("com.apptolast.platform.knowledge")

    @Test
    fun `domain must not depend on spring`() {
        noClasses()
            .that().resideInAPackage("..knowledge.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "org.springframework..",
                "jakarta.persistence..",
            )
            .check(classes)
    }

    @Test
    fun `application port must not depend on spring or infrastructure`() {
        noClasses()
            .that().resideInAPackage("..knowledge.application.port..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "org.springframework..",
                "..knowledge.infrastructure..",
            )
            .check(classes)
    }

    @Test
    fun `infrastructure must not be referenced from application port or domain`() {
        noClasses()
            .that().resideInAnyPackage("..knowledge.domain..", "..knowledge.application.port..")
            .should().dependOnClassesThat().resideInAPackage("..knowledge.infrastructure..")
            .check(classes)
    }
}
