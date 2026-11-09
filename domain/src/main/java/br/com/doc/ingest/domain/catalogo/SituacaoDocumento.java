package br.com.doc.ingest.domain.catalogo;

/** Situação de um documento. A ordem declarada importa: a situação não regride na fusão (DA-27). */
public enum SituacaoDocumento {
    SO_RESUMO,
    AUTORIZADA,
    CANCELADA,
    DENEGADA
}
