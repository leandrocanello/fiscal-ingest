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
}
