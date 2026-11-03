package br.com.doc.ingest.domain.cofre;

/** Por que um certificado guardado não pôde ser decifrado. */
public enum FalhaAbertura {
    BLOB_AUSENTE,
    ENVELOPE_INVALIDO,
    KEK_DESCONHECIDA
}
