package com.lorevault.api.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Tag;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RestController;

@Tag("architecture")
@AnalyzeClasses(packages = "com.lorevault.api", importOptions = ImportOption.DoNotIncludeTests.class)
class WebLayerArchitectureTest {

    /**
     * Controllers (REST and UI) must not directly inject or depend on
     * Neo4j / Spring Data repository interfaces. Existence checks and
     * persistence go through the service layer — the web boundary is
     * a pure delegation facade.
     */
    @ArchTest
    static final ArchRule controllers_must_not_inject_repositories = noClasses()
            .that().areAnnotatedWith(RestController.class)
            .or().areAnnotatedWith(Controller.class)
            .should()
            .dependOnClassesThat()
            .haveNameMatching(".*Repository")
            .because("Controllers must delegate to services, not inject repositories directly. "
                    + "Repository injection in controllers is a layering violation "
                    + "and creates TOCTOU race conditions.");

    /**
     * Verbatim: no controller field or method parameter whose type extends
     * any {@code org.springframework.data.repository.Repository} interface.
     * Catches classes that happen not to follow the *Repository naming convention.
     */
    @ArchTest
    static final ArchRule controllers_must_not_depend_on_spring_data_repository_types = noClasses()
            .that().areAnnotatedWith(RestController.class)
            .or().areAnnotatedWith(Controller.class)
            .should()
            .dependOnClassesThat()
            .areAssignableTo(org.springframework.data.repository.Repository.class)
            .because("Controllers must not depend on any Spring Data Repository subtype. "
                    + "All data access belongs in the service layer.");
}
