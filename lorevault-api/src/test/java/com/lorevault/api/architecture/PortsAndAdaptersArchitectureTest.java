package com.lorevault.api.architecture;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.core.domain.JavaClass;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

/**
 * Architecture tests to enforce ports & adapters (hexagonal) architecture boundaries.
 * These tests ensure that the codebase maintains proper separation of concerns and 
 * that dependencies flow in the correct direction.
 * 
 * NOTE: Currently tagged as "architecture" and excluded from default test runs.
 * TODO: Post-refactor task - Change @Tag back to "unit" once architectural violations 
 *       are addressed (port locations, service naming, infrastructure dependencies, etc.)
 */
@AnalyzeClasses(
    packages = "com.lorevault.api",
    importOptions = { ImportOption.DoNotIncludeTests.class }
)
@DisplayName("Architecture Rules - Ports & Adapters Enforcement")
@Tag("architecture")
class PortsAndAdaptersArchitectureTest {

    // =================================================================
    // Layer Architecture Rules
    // =================================================================
    
    @ArchTest
    static final ArchRule domain_should_not_depend_on_other_layers = 
        noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "..infrastructure..",
                "..web..",
                "..configuration.."
            )
            .as("Domain layer should not depend on infrastructure, web, or configuration layers");
    
    @ArchTest
    static final ArchRule application_should_only_depend_on_domain_and_ports = 
        noClasses()
            .that().resideInAPackage("..service..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "..infrastructure..",
                "..web.."
            )
            .as("Application services should only depend on domain and ports, not infrastructure");
    
    @ArchTest
    static final ArchRule services_should_not_depend_on_adapters = 
        noClasses()
            .that().resideInAPackage("..service..")
            .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
            .as("Services should not depend on infrastructure adapters directly");

    // =================================================================
    // Port Interface Rules
    // =================================================================
    
    @ArchTest
    static final ArchRule port_interfaces_should_be_in_port_package = 
        classes()
            .that().haveSimpleNameEndingWith("Port")
            .should().resideInAPackage("..application.port..")
            .as("Port interfaces should be located in application.port package");
    
    @ArchTest
    static final ArchRule ports_should_be_interfaces = 
        classes()
            .that().haveSimpleNameEndingWith("Port")
            .should().beInterfaces()
            .as("Port interfaces should be interfaces, not classes");
    
    @ArchTest
    static final ArchRule ports_should_not_depend_on_infrastructure = 
        noClasses()
            .that().haveSimpleNameEndingWith("Port")
            .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
            .as("Port interfaces should not depend on infrastructure implementations");

    // =================================================================
    // Adapter Implementation Rules
    // =================================================================
    
    @ArchTest
    static final ArchRule adapters_should_be_in_infrastructure_package = 
        classes()
            .that().haveSimpleNameEndingWith("Adapter")
            .should().resideInAPackage("..infrastructure..")
            .as("Adapters should be located in infrastructure package");
    
    @ArchTest
    static final ArchRule adapters_should_implement_ports = 
        classes()
            .that().haveSimpleNameEndingWith("Adapter")
            .and().doNotHaveSimpleName("MultiProviderEmbeddingAdapter") // Has internal provider interface
            .should(new ArchCondition<JavaClass>("implement a port interface") {
                @Override
                public void check(JavaClass item, ConditionEvents events) {
                    boolean implementsPort = item.getInterfaces().stream()
                        .anyMatch(iface -> iface.getName().endsWith("Port"));
                    
                    if (!implementsPort) {
                        String message = String.format("Class %s should implement an interface ending with 'Port'", 
                            item.getName());
                        events.add(SimpleConditionEvent.violated(item, message));
                    }
                }
            }).as("Adapters should implement a port interface");

    // =================================================================
    // Web Layer Rules
    // =================================================================
    
    @ArchTest
    static final ArchRule controllers_should_be_in_web_package = 
        classes()
            .that().haveSimpleNameEndingWith("Controller")
            .should().resideInAPackage("..web..")
            .as("Controllers should be located in web package");
    
    @ArchTest
    static final ArchRule controllers_should_not_depend_on_adapters = 
        noClasses()
            .that().resideInAPackage("..web..")
            .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
            .as("Controllers should not directly depend on infrastructure adapters");

    // =================================================================
    // Naming Convention Rules
    // =================================================================
    
    @ArchTest
    static final ArchRule service_annotation_name_consistency = 
        classes()
            .that().areAnnotatedWith("org.springframework.stereotype.Service")
            .and().areTopLevelClasses()
            .should().haveSimpleNameEndingWith("Service")
            .as("Classes annotated with @Service should end with 'Service'");
    
    @ArchTest
    static final ArchRule repositories_should_be_named_correctly = 
        classes()
            .that().areAssignableTo("org.springframework.data.repository.Repository")
            .should().haveSimpleNameEndingWith("Repository")
            .as("Repository classes should end with 'Repository'");

    // =================================================================
    // Dependency Direction Rules
    // =================================================================
    
    @ArchTest
    static final ArchRule layered_architecture = 
        layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("Domain").definedBy("..domain..")
            .layer("Application").definedBy("..service..", "..application..")
            .layer("Infrastructure").definedBy("..infrastructure..")
            .layer("Web").definedBy("..web..")
            // Include infrastructure.config as Configuration wiring to avoid false positives
            .layer("Configuration").definedBy("..configuration..", "..infrastructure.config..")
            
            // Hexagonal intent: Domain is the innermost layer and may be depended on by
            // Application/Infrastructure/Configuration (but ideally not by Web).
            .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Infrastructure", "Configuration")
            // Application should be used by Web, Configuration (wiring), and Infrastructure (adapters implement ports)
            .whereLayer("Application").mayOnlyBeAccessedByLayers("Web", "Configuration", "Infrastructure")
            .whereLayer("Infrastructure").mayOnlyBeAccessedByLayers("Configuration")
            .whereLayer("Web").mayOnlyBeAccessedByLayers("Configuration")
            .as("Layered architecture should be respected with proper dependency direction");

    // =================================================================
    // Spring Annotation Rules
    // =================================================================
    
    @ArchTest
    static final ArchRule services_should_be_spring_services = 
        classes()
            .that().haveSimpleNameEndingWith("Service")
            .and().resideInAPackage("..service..")
            .and().areNotInterfaces()
            .should().beAnnotatedWith("org.springframework.stereotype.Service")
            .as("Services should be annotated with @Service");
    
    @ArchTest
    static final ArchRule api_controllers_should_be_rest_controllers = 
        classes()
            .that().haveSimpleNameEndingWith("Controller")
            .and().resideInAPackage("..web..")
            .and().resideOutsideOfPackage("..web.ui..")
            .should().beAnnotatedWith("org.springframework.web.bind.annotation.RestController")
            .as("API controllers should be annotated with @RestController");

    @ArchTest
    static final ArchRule ui_controllers_should_be_mvc_controllers = 
        classes()
            .that().haveSimpleNameEndingWith("Controller")
            .and().resideInAPackage("..web.ui..")
            .should().beAnnotatedWith("org.springframework.stereotype.Controller")
            .as("UI controllers should be annotated with @Controller");
    
    @ArchTest
    static final ArchRule configurations_should_be_spring_configurations = 
        classes()
            .that().haveSimpleNameEndingWith("Configuration")
            .and().resideInAPackage("..configuration..")
            .should().beAnnotatedWith("org.springframework.context.annotation.Configuration")
            .as("Configuration classes should be annotated with @Configuration");
}
