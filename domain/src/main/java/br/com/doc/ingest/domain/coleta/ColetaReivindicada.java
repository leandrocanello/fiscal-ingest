package br.com.doc.ingest.domain.coleta;

import java.time.Instant;
import java.util.Objects;

/** Estado de coleta reivindicado: a posição atual (base do CAS) e o lease do dono. */
public record ColetaReivindicada(PosicaoColeta posicao, String dono, Instant leaseAte) {

    public ColetaReivindicada {
        Objects.requireNonNull(posicao, "posicao");
        Objects.requireNonNull(dono, "dono");
        Objects.requireNonNull(leaseAte, "leaseAte");
        if (dono.isBlank()) {
            throw new IllegalArgumentException("dono não pode ser vazio");
        }
    }
}
