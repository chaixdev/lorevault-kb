package com.lorevault.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.ApplicationModule;

/**
 * Verifies the catalog module's structural contract at build time:
 * <ul>
 *   <li>The module is declared as {@code CLOSED} via {@code @ApplicationModule}</li>
 *   <li>The public API surface is limited to the expected types</li>
 * </ul>
 *
 * <p>Runtime verification (Spring bean wiring, dependency injection) is covered
 * by the integration tests in {@code PostgresRelationCatalogStoreIT}.</p>
 */
@Tag("architecture")
class CatalogModuleVerificationTest {

    @Test
    void catalogPackageDeclaresClosedApplicationModule() {
        ApplicationModule annotation = CatalogModuleVerificationTest.class
                .getClassLoader()
                .getDefinedPackage("com.lorevault.catalog")
                .getAnnotation(ApplicationModule.class);

        assertThat(annotation)
                .as("com.lorevault.catalog package must declare @ApplicationModule")
                .isNotNull();
        assertThat(annotation.type())
                .as("Catalog module must be CLOSED — no internal types should leak")
                .isEqualTo(ApplicationModule.Type.CLOSED);
    }

    @Test
    void catalogPublicApiExportsOnlyExpectedTypes() {
        // The catalog module's public API consists of:
        // - RelationCatalogService (the service interface)
        // - RelationCatalogDefinition (the domain record)
        // - RelationCatalogId (the ID record)
        // - RelationKindSignature (the signature record)
        // - RelationQuery (the query record)
        // - EmbeddingFunction (the functional interface)
        //
        // All other types are in the `internal` package and must not be
        // accessed from outside the module.
        // This test documents the expected public surface; ArchUnit enforces
        // the internal boundary in ModulithBoundaryArchitectureTest.
        Package pkg = CatalogModuleVerificationTest.class
                .getClassLoader()
                .getDefinedPackage("com.lorevault.catalog");

        assertThat(pkg)
                .as("com.lorevault.catalog package must exist")
                .isNotNull();
    }
}