package br.com.doc.ingest.domain.catalogo;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * De onde veio um arquivo e com qual confiança. Pull ({@code DFE_AN}) é sempre {@code OFICIAL} e aponta
 * para o ledger; push nunca é {@code OFICIAL} e aponta para o recebimento (inv. 9, DA-27).
 *
 * @param origem        canal de chegada
 * @param confianca     nível de confiança
 * @param ledgerNsuId   linha do ledger, só em {@code DFE_AN}
 * @param recebimentoId recebimento, só em push
 * @param recebidoEm    momento do recebimento
 */
public record ProvenienciaInformada(
        OrigemArquivo origem,
        Confianca confianca,
        Optional<Long> ledgerNsuId,
        Optional<Long> recebimentoId,
        Instant recebidoEm) {

    public ProvenienciaInformada {
        Objects.requireNonNull(origem, "origem");
        Objects.requireNonNull(confianca, "confianca");
        Objects.requireNonNull(ledgerNsuId, "ledgerNsuId");
        Objects.requireNonNull(recebimentoId, "recebimentoId");
        Objects.requireNonNull(recebidoEm, "recebidoEm");
        if (ledgerNsuId.isPresent() && recebimentoId.isPresent()) {
            throw new IllegalArgumentException("proveniência não pode ter ledger e recebimento juntos");
        }
        if (origem == OrigemArquivo.DFE_AN) {
            if (confianca != Confianca.OFICIAL) {
                throw new IllegalArgumentException("DFE_AN só pode ser OFICIAL");
            }
            if (ledgerNsuId.isEmpty()) {
                throw new IllegalArgumentException("DFE_AN exige ledger");
            }
        } else {
            if (confianca == Confianca.OFICIAL) {
                throw new IllegalArgumentException("push não pode se declarar OFICIAL");
            }
            if (recebimentoId.isEmpty()) {
                throw new IllegalArgumentException("push exige recebimento");
            }
        }
    }
}
