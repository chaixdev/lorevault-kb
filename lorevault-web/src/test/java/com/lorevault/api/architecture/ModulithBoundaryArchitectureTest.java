package com.lorevault.api.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Tag;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RestController;

@Tag("architecture")
@AnalyzeClasses(packages = {"com.lorevault.api", "com.lorevault.catalog"}, importOptions = ImportOption.DoNotIncludeTests.class)
class ModulithBoundaryArchitectureTest {

    @ArchTest
    static final ArchRule no_new_top_level_api_packages_outside_allowed_set = noClasses()
            .that().resideInAPackage("com.lorevault.api..")
            .and().resideOutsideOfPackages(
                    "com.lorevault.api.ai..",
                    "com.lorevault.api.config..",
                    "com.lorevault.api.content..",
                    "com.lorevault.api.health..",
                    "com.lorevault.api.ingestion..",
                    "com.lorevault.api.library..",
                    "com.lorevault.api.search..",
                    "com.lorevault.api.web..",
                    "com.lorevault.api.architecture..",
                    "com.lorevault.api")
            .should().haveFullyQualifiedName("com.lorevault.api.__forbidden_top_level_package_marker__")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule core_packages_must_not_depend_on_web = noClasses()
            .that().resideInAnyPackage(
                    "com.lorevault.api.ai..",
                    "com.lorevault.api.config..",
                    "com.lorevault.api.content..",
                    "com.lorevault.api.health..",
                    "com.lorevault.api.ingestion..",
                    "com.lorevault.api.library..",
                    "com.lorevault.api.search..")
            .should().dependOnClassesThat().resideInAnyPackage("com.lorevault.api.web..");

    @ArchTest
    static final ArchRule catalog_must_not_depend_on_ingestion_or_web = noClasses()
            .that().resideInAnyPackage("com.lorevault.catalog..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.lorevault.api.ingestion..",
                    "com.lorevault.api.web..",
                    "com.lorevault.api.search..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule catalog_internal_must_not_be_accessed_from_outside = noClasses()
            .that().resideOutsideOfPackage("com.lorevault.catalog..")
            .should().dependOnClassesThat().resideInAnyPackage("com.lorevault.catalog.internal..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule web_controllers_must_stay_inside_web_package = classes()
            .that().areAnnotatedWith(RestController.class)
            .or().areAnnotatedWith(Controller.class)
            .should().resideInAnyPackage("com.lorevault.api.web..");
}
