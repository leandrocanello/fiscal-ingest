package br.com.doc.ingest.domain.coleta;

import java.time.Duration;
import java.util.Objects;

/**
 * Pedido de claim: o {@code dono} (instância do scheduler) quer até {@code limite} estados de coleta
 * vencidos, com lease de duração {@code lease} (CLAUDE.md §6.3, §6.5).
 */
public record PedidoReivindicacao(String dono, Duration lease, int limite) {

    public PedidoReivindicacao {
        Objects.requireNonNull(dono, "dono");
        Objects.requireNonNull(lease, "lease");
        if (dono.isBlank()) {
            throw new IllegalArgumentException("dono não pode ser vazio");
        }
        if (lease.isZero() || lease.isNegative()) {
            throw new IllegalArgumentException("lease deve ser positivo: " + lease);
        }
        if (limite <= 0) {
            throw new IllegalArgumentException("limite deve ser positivo: " + limite);
        }
    }
}
