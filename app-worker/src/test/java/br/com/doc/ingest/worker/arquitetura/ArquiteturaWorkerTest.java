package br.com.doc.ingest.worker.arquitetura;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/** O processor só conhece portas do domínio (CLAUDE.md §19, inv. 3 e 4, DA-32). */
@AnalyzeClasses(packages = "br.com.doc.ingest", importOptions = ImportOption.DoNotIncludeTests.class)
class ArquiteturaWorkerTest {

    @ArchTest
    static final ArchRule processadorNaoDependeDeAdapterNemDeInfra = noClasses()
            .that().resideInAPackage("br.com.doc.ingest.worker..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "br.com.doc.ingest.adapter..", "br.com.doc.ingest.infra..",
                    "org.springframework..", "jakarta.xml..", "javax.xml..", "org.w3c..", "java.sql..")
            .because("o processor fala só com portas: XML e cStat ficam no adapter, SQL na infra (DA-32)");
}
