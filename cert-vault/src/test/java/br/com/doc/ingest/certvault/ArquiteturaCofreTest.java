package br.com.doc.ingest.certvault;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.core.importer.Location;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/** Regras do módulo cert-vault: só domínio + JDK, sem log, sem rede e sem disco (CLAUDE.md §19, inv. 13 e 14). */
@AnalyzeClasses(packages = "br.com.doc.ingest.certvault",
        importOptions = {ImportOption.DoNotIncludeTests.class, ArquiteturaCofreTest.SemTestFixtures.class})
class ArquiteturaCofreTest {

    private static final String COFRE = "br.com.doc.ingest.certvault..";

    /** O gerador de cadeia sintética (BouncyCastle) vive em testFixtures e não é código de produção. */
    static final class SemTestFixtures implements ImportOption {
        @Override
        public boolean includes(Location location) {
            return !location.contains("/testFixtures/") && !location.contains("-test-fixtures");
        }
    }

    @ArchTest
    static final ArchRule soDependeDoDominioEDoJdk = classes()
            .that().resideInAPackage(COFRE)
            .should().onlyDependOnClassesThat().resideInAnyPackage(
                    COFRE, "br.com.doc.ingest.domain..", "java..", "javax..", "")
            .because("cert-vault depende só do domínio e do JDK (CLAUDE.md §19)");

    @ArchTest
    static final ArchRule semLogNoCofre = noClasses()
            .that().resideInAPackage(COFRE)
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.slf4j..", "java.util.logging..", "org.apache.logging..", "org.apache.commons.logging..",
                    "ch.qos.logback..")
            .orShould().dependOnClassesThat().haveFullyQualifiedName("java.lang.System$Logger")
            .orShould().accessField(System.class, "out")
            .orShould().accessField(System.class, "err")
            .orShould().callMethod(Throwable.class, "printStackTrace")
            .because("o cofre manipula .pfx, senha e chaves: nada dele vai para log (invariante 13)");

    @ArchTest
    static final ArchRule semHttpNemSocket = noClasses()
            .that().resideInAPackage(COFRE)
            .should().dependOnClassesThat().resideInAnyPackage("java.net..", "javax.net..", "jdk.net..")
            .because("o cofre não fala com a rede; nunca SEFAZ real (invariante 14)");

    @ArchTest
    static final ArchRule semEscritaEmDisco = noClasses()
            .that().resideInAPackage(COFRE)
            .should().dependOnClassesThat().resideInAnyPackage("java.nio.file..", "java.nio.channels..")
            .orShould().dependOnClassesThat().haveSimpleNameStartingWith("File")
            .orShould().dependOnClassesThat().haveFullyQualifiedName("java.io.RandomAccessFile")
            .because("segredo nunca vai para disco em claro (invariante 13); o cifrado vai pelo BlobStore");

    @ArchTest
    static final ArchRule semSpring = noClasses()
            .that().resideInAPackage(COFRE)
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework..")
            .because("Spring só nos app-* (ADR-027)");
}
