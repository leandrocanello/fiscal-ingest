package br.com.doc.ingest.domain.arquitetura;

import com.tngtech.archunit.lang.ArchRule;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;

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

    public static final ArchRule NAO_USA_RELOGIO_SOLTO = noClasses()
            .should().callMethod(Instant.class, "now")
            .orShould().callMethod(LocalDate.class, "now")
            .orShould().callMethod(LocalDateTime.class, "now")
            .orShould().callMethod(OffsetDateTime.class, "now")
            .orShould().callMethod(ZonedDateTime.class, "now")
            .orShould().callMethod(Clock.class, "systemUTC")
            .orShould().callMethod(Clock.class, "systemDefaultZone")
            .orShould().callMethod(System.class, "currentTimeMillis")
            .orShould().callMethod(System.class, "nanoTime")
            .orShould().callMethodWhere(com.tngtech.archunit.base.DescribedPredicate.describe(
                    "Thread.sleep(..)",
                    c -> c.getTargetOwner().isEquivalentTo(Thread.class) && c.getName().equals("sleep")))
            .because("o domínio recebe o tempo por Clock injetado (CLAUDE.md §15)");
}
