package com.apptolast.platform.inventory.architecture

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import org.junit.jupiter.api.Test

/**
 * Reglas hexagonales del módulo Inventory.
 *
 * - `domain` no puede importar Spring, JPA, web, infrastructure ni Jackson.
 * - `application` no puede importar JPA, web, infrastructure.
 * - `api` no puede importar nada que esté en domain/application/infrastructure.
 * - `infrastructure` puede importar todo dentro del módulo.
 *
 * Reason: si se rompe alguna de estas reglas, el dominio deja de ser puro y
 * pierde la capacidad de testearse sin Spring Context (lo más caro de runtime).
 */
class InventoryArchitectureTest {

    private val classes = ClassFileImporter()
        .withImportOption(ImportOption.DoNotIncludeTests)
        .importPackages("com.apptolast.platform.inventory")

    @Test
    fun `domain layer must not depend on spring`() {
        noClasses()
            .that().resideInAPackage("..inventory.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "org.springframework..",
                "jakarta.persistence..",
                "io.fabric8..",
                "com.fasterxml.jackson..",
            )
            .check(classes)
    }

    @Test
    fun `domain layer must not depend on infrastructure`() {
        noClasses()
            .that().resideInAPackage("..inventory.domain..")
            .should().dependOnClassesThat().resideInAPackage("..inventory.infrastructure..")
            .check(classes)
    }

    @Test
    fun `domain layer must not depend on application layer`() {
        noClasses()
            .that().resideInAPackage("..inventory.domain..")
            .should().dependOnClassesThat().resideInAPackage("..inventory.application..")
            .check(classes)
    }

    @Test
    fun `application layer must not depend on infrastructure`() {
        noClasses()
            .that().resideInAPackage("..inventory.application..")
            .should().dependOnClassesThat().resideInAPackage("..inventory.infrastructure..")
            .check(classes)
    }

    @Test
    fun `application layer must not depend on JPA, web or fabric8`() {
        noClasses()
            .that().resideInAPackage("..inventory.application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "jakarta.persistence..",
                "org.springframework.web..",
                "io.fabric8..",
            )
            .check(classes)
    }

    @Test
    fun `api events must be pure data — no spring, no jpa`() {
        noClasses()
            .that().resideInAPackage("..inventory.api..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "org.springframework..",
                "jakarta.persistence..",
                "io.fabric8..",
                "com.apptolast.platform.inventory.domain..",
                "com.apptolast.platform.inventory.application..",
                "com.apptolast.platform.inventory.infrastructure..",
            )
            .check(classes)
    }

    @Test
    fun `JPA entities live only in infrastructure persistence`() {
        classes()
            .that().areAnnotatedWith("jakarta.persistence.Entity")
            .should().resideInAPackage("..inventory.infrastructure.persistence.entity..")
            .check(classes)
    }

    @Test
    fun `REST controllers live only in infrastructure web`() {
        classes()
            .that().areAnnotatedWith("org.springframework.web.bind.annotation.RestController")
            .should().resideInAPackage("..inventory.infrastructure.web..")
            .check(classes)
    }
}
