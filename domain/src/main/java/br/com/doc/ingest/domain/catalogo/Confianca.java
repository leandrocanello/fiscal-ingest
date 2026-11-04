package br.com.doc.ingest.domain.catalogo;

/** Nível de confiança de uma proveniência (CLAUDE.md §4). */
public enum Confianca {
    /** Veio da SEFAZ/AN por mTLS. */
    OFICIAL,
    /** Veio por push e a situação foi conferida na SEFAZ. */
    CONFIRMADO_SEFAZ,
    /** Veio por push com as assinaturas da nota e do protocolo válidas. */
    VALIDADO_ASSINATURA,
    /** Veio por push sem validação completa: armazenado, mas sinalizado. */
    DECLARADO
}
