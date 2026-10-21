package br.com.doc.ingest.domain.ledger;

/**
 * Status de um NSU no ledger append-only.
 *
 * <p>Todo NSU que passou pelo sistema tem um status registrado para auditoria.
 * Os status {@link #DESCARTADO} e {@link #FORA_PRAZO} indicam que o NSU foi
 * processado mas não gerou documento útil — eles <b>preenchem</b> a sequência
 * (não são considerados gaps pelo {@link GapCalculator}).
 */
public enum StatusNsu {
    /** NSU baixado com documento completo (procNFe, etc.). */
    BAIXADO,
    /** NSU contém apenas resumo (resNFe) — aguarda manifestação. */
    RESUMO,
    /** NSU contém um evento (manifestação, cancelamento, etc.). */
    EVENTO,
    /** NSU processado mas descartado (duplicado, sem interesse, etc.). */
    DESCARTADO,
    /** NSU fora do prazo de disponibilidade da SEFAZ (~90 dias). */
    FORA_PRAZO,
    /** NSU identificado como faltante (gap) — pendente de recuperação. */
    GAP
}
