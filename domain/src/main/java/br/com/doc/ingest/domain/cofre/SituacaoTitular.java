package br.com.doc.ingest.domain.cofre;

import java.time.Instant;

/** Situação dos certificados de um titular num instante (DA-53). */
public sealed interface SituacaoTitular {

    /** Certificado válido agora, com o maior {@code validoAte}. */
    record Vigente(CertificadoGuardado certificado) implements SituacaoTitular {
    }

    /** Nenhum válido nem vencido; o primeiro (menor {@code validoDe}) passa a valer em {@code validoDe}. */
    record AindaNaoValido(Instant validoDe) implements SituacaoTitular {
    }

    /** Há vencidos e nenhum válido; {@code validoAte} é o maior entre os vencidos. */
    record Vencido(Instant validoAte) implements SituacaoTitular {
    }

    /** O titular não tem certificado. */
    record Ausente() implements SituacaoTitular {
    }
}
