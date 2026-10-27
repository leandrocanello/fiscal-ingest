package br.com.doc.ingest.infra.postgres;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/** Regras de dependência de camadas (CLAUDE.md §19, ADR-025, ADR-027). */
@AnalyzeClasses(packages = "br.com.doc.ingest", importOptions = ImportOption.DoNotIncludeTests.class)
class ArquiteturaInfraTest {

    @ArchTest
    static final ArchRule infraPostgresNaoUsaSpringNemJpa = noClasses()
            .that().resideInAPackage("br.com.doc.ingest.infra.postgres..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "jakarta.persistence..", "org.hibernate..")
            .allowEmptyShould(true)
            .because("persistência com JDBI + Flyway, sem JPA; Spring só nos app-* (ADR-025, ADR-027)");

    @ArchTest
    static final ArchRule dominioNaoDependeDeInfra = noClasses()
            .that().resideInAPackage("br.com.doc.ingest.domain..")
            .should().dependOnClassesThat().resideInAnyPackage("br.com.doc.ingest.infra..")
            .because("domain não depende de nada (CLAUDE.md §19)");
}
