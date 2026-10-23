package br.com.doc.ingest.adapter.nfe.doczip;

import br.com.doc.ingest.domain.ledger.StatusNsu;

import java.util.Optional;

/** Tipo de documento de um {@code docZip} da distribuição, conforme o atributo {@code schema}. */
public enum TipoSchemaDfe {
    RES_NFE("resNFe", StatusNsu.RESUMO),
    PROC_NFE("procNFe", StatusNsu.BAIXADO),
    RES_EVENTO("resEvento", StatusNsu.EVENTO),
    PROC_EVENTO_NFE("procEventoNFe", StatusNsu.EVENTO),
    DESCONHECIDO(null, null);

    private final String nomeBase;
    private final StatusNsu status;

    TipoSchemaDfe(String nomeBase, StatusNsu status) {
        this.nomeBase = nomeBase;
        this.status = status;
    }

    /** Nome base do schema (sem versão nem extensão); {@code null} para {@link #DESCONHECIDO}. */
    String nomeBase() {
        return nomeBase;
    }

    /** Status sugerido para o NSU no ledger; vazio quando o schema é desconhecido. */
    public Optional<StatusNsu> statusSugerido() {
        return Optional.ofNullable(status);
    }
}
