package com.apptolast.platform.observability.architecture

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import org.junit.jupiter.api.Test

class ObservabilityArchTest {

    private val classes = ClassFileImporter()
        .withImportOption(ImportOption.DoNotIncludeTests())
        .importPackages("com.apptolast.platform.observability")

    @Test
    fun `domain must not depend on spring or jpa`() {
        noClasses()
            .that().resideInAPackage("..observability.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "org.springframework..",
                "jakarta.persistence..",
                "io.opentelemetry..",
            )
            .check(classes)
    }

    @Test
    fun `application must not depend on infrastructure`() {
        noClasses()
            .that().resideInAPackage("..observability.application..")
            .should().dependOnClassesThat().resideInAPackage("..observability.infrastructure..")
            .check(classes)
    }
}
