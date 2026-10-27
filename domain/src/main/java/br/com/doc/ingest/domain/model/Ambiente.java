package br.com.doc.ingest.domain.model;

/**
 * Ambiente de consulta. A sequência de NSU é independente por ambiente (CLAUDE.md §3.1).
 * Não carrega o código de protocolo (tpAmb): isso é detalhe do adapter.
 */
public enum Ambiente {
    PRODUCAO,
    HOMOLOGACAO
}
