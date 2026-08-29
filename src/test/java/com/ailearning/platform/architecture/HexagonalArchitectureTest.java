package com.ailearning.platform.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import jakarta.persistence.Entity;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class HexagonalArchitectureTest {
    private final JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.ailearning.platform");

    @Test
    void domainIsFrameworkFree() {
        noClasses().that().resideInAnyPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..", "jakarta.persistence..", "jakarta.servlet..")
                .check(classes);
        noClasses().that().resideInAnyPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage("..application..", "..adapter..", "..api..")
                .check(classes);
    }

    @Test
    void applicationDoesNotDependOnAdaptersOrFrameworks() {
        noClasses().that().resideInAnyPackage("..application..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..adapter..", "com.ailearning.platform.platform..", "org.springframework..", "jakarta.persistence..",
                        "jakarta.servlet..")
                .check(classes);
    }

    @Test
    void inboundAdaptersDoNotAccessPersistenceAdapters() {
        noClasses().that().resideInAnyPackage("..adapter.in..")
                .should().dependOnClassesThat().resideInAnyPackage("..adapter.out.persistence..")
                .check(classes);
    }

    @Test
    void jpaEntitiesLiveOnlyInPersistenceAdapters() {
        classes().that().areAnnotatedWith(Entity.class)
                .should().resideInAnyPackage("..adapter.out.persistence.jpa.entity..")
                .check(classes);
    }
}
