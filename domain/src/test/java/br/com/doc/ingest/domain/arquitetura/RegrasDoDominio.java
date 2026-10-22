package br.com.doc.ingest.domain.arquitetura;

import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

public final class RegrasDoDominio {

    private RegrasDoDominio() {
    }

    public static final ArchRule SO_DEPENDE_DO_JDK_E_DE_SI_MESMO = noClasses()
            .should().dependOnClassesThat()
            .resideOutsideOfPackages("java..", "br.com.doc.ingest.domain..")
            .because("o domínio é puro: só JDK e o próprio pacote (CLAUDE.md §19, inv. 4)");

    public static final ArchRule NAO_FAZ_IO = noClasses()
            .should().dependOnClassesThat()
            .resideInAnyPackage("java.io..", "java.nio.file..", "java.nio.channels..",
                    "java.net..", "java.sql..", "javax..")
            .because("o domínio não faz I/O (CLAUDE.md §2.2, inv. 3)");
}
