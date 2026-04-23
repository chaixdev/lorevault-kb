package com.lorevault.api.architecture;

import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Architecture validation assertions for top-level core package boundaries.
 */
@AnalyzeClasses(packages = "com.lorevault.api", importOptions = ImportOption.DoNotIncludeTests.class)
class CorePackageBoundaryArchitectureTest {

    @ArchTest
    static final ArchRule core_top_level_packages_should_be_cycle_free = slices()
            .matching("com.lorevault.api.(ai|content|ingestion|library|search|health|config).(*)..")
            .should().beFreeOfCycles();
}
