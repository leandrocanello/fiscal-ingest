package br.com.doc.ingest.domain.adapter;

/**
 * Manifestação do destinatário sobre uma nota recebida (CLAUDE.md §3.3).
 * O domínio só conhece o significado; o código do evento da fonte (ex.: tpEvento da SEFAZ)
 * é mapeado dentro do adapter (invariante 4).
 */
public enum TipoManifestacao {
    CIENCIA(false),
    CONFIRMACAO(false),
    DESCONHECIMENTO(false),
    OPERACAO_NAO_REALIZADA(true);

    private final boolean exigeJustificativa;

    TipoManifestacao(boolean exigeJustificativa) {
        this.exigeJustificativa = exigeJustificativa;
    }

    /** Se a manifestação precisa vir acompanhada de justificativa textual. */
    public boolean exigeJustificativa() {
        return exigeJustificativa;
    }
}
