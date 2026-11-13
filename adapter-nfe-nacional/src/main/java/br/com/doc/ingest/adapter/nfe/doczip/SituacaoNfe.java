package br.com.doc.ingest.adapter.nfe.doczip;

import br.com.doc.ingest.domain.catalogo.SituacaoDocumento;

import java.util.Optional;
import java.util.Set;

/**
 * Tabela única de códigos da NF-e → {@link SituacaoDocumento} (DA-33). Os códigos ficam só aqui:
 * o domínio nunca vê {@code cStat} nem {@code cSitNFe} (inv. 4).
 *
 * <p><b>[VALIDAR]</b> no MOC e no smoke E2-S12; registrar a fonte em {@code docs/FONTES-OFICIAIS.md}.
 * Código desconhecido vira {@code SO_RESUMO}, que nunca sobrepõe situação melhor na fusão (DA-27).
 * A situação é reconstruível a partir do bruto: trocar a tabela e reprocessar.
 */
final class SituacaoNfe {

    private static final Set<String> PROTOCOLO_AUTORIZADA = Set.of("100", "150");
    private static final Set<String> PROTOCOLO_DENEGADA = Set.of("110", "205", "301", "302", "303");
    private static final Set<String> EVENTO_CANCELAMENTO = Set.of("110111", "110112");

    private SituacaoNfe() {
    }

    /** {@code cSitNFe} do {@code resNFe}: 1 autorizada (só resumo), 2 denegada, 3 cancelada. */
    static SituacaoDocumento doResumo(Optional<String> cSitNFe) {
        return switch (cSitNFe.orElse("")) {
            case "2" -> SituacaoDocumento.DENEGADA;
            case "3" -> SituacaoDocumento.CANCELADA;
            default -> SituacaoDocumento.SO_RESUMO;
        };
    }

    /** {@code cStat} do protocolo do {@code nfeProc}. */
    static SituacaoDocumento doProtocolo(Optional<String> cStat) {
        String c = cStat.orElse("");
        if (PROTOCOLO_AUTORIZADA.contains(c)) {
            return SituacaoDocumento.AUTORIZADA;
        }
        if (PROTOCOLO_DENEGADA.contains(c)) {
            return SituacaoDocumento.DENEGADA;
        }
        return SituacaoDocumento.SO_RESUMO;
    }

    /** {@code tpEvento}: cancelamento (110111) e cancelamento por substituição (110112). */
    static SituacaoDocumento doEvento(String tpEvento) {
        return EVENTO_CANCELAMENTO.contains(tpEvento) ? SituacaoDocumento.CANCELADA : SituacaoDocumento.SO_RESUMO;
    }
}
