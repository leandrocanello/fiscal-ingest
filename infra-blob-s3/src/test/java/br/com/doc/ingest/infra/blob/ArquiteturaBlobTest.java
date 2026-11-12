package br.com.doc.ingest.infra.blob;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/** Regras de dependência de camadas (CLAUDE.md §19, ADR-027). */
@AnalyzeClasses(packages = "br.com.doc.ingest", importOptions = ImportOption.DoNotIncludeTests.class)
class ArquiteturaBlobTest {

    @ArchTest
    static final ArchRule infraBlobNaoUsaSpring = noClasses()
            .that().resideInAPackage("br.com.doc.ingest.infra.blob..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "br.com.doc.ingest.infra.postgres..")
            .allowEmptyShould(true)
            .because("Spring só nos app-*; infra-* não dependem entre si (ADR-027, CLAUDE.md §19)");

    @ArchTest
    static final ArchRule dominioNaoDependeDoSdkS3 = noClasses()
            .that().resideInAPackage("br.com.doc.ingest.domain..")
            .should().dependOnClassesThat().resideInAPackage("software.amazon..")
            .because("domain não depende de nada (CLAUDE.md §19)");
}
