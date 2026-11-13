package br.com.doc.ingest.domain.coleta;

import java.util.Objects;

/** Certificado hoje ligado a um estado de coleta; {@code certificadoId} é nulo se ainda não há. */
public record VinculoCertificado(ChaveColeta chave, Long certificadoId) {

    public VinculoCertificado {
        Objects.requireNonNull(chave, "chave");
    }
}
