package br.com.doc.ingest.infra.postgres;

import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.HandleConsumer;
import org.jdbi.v3.core.Jdbi;

import java.sql.Timestamp;
import java.time.Clock;

/**
 * Consumo idempotente (ADR-015): registra {@code (consumidor, chave_idempotencia)} e executa a ação na mesma
 * transação. Falha na ação desfaz o registro, permitindo retentar.
 */
public final class RegistroDeConsumo {

    private final Jdbi jdbi;
    private final Clock clock;

    public RegistroDeConsumo(Jdbi jdbi, Clock clock) {
        this.jdbi = jdbi;
        this.clock = clock;
    }

    /** Devolve {@code true} se executou a ação; {@code false} se essa chave já foi consumida por esse consumidor. */
    public boolean executarUmaVez(String consumidor, String chaveIdempotencia, HandleConsumer<RuntimeException> acao) {
        // Handle próprio: o Jdbi reaproveitaria o handle da thread (ex.: a transação do relay) e a falha dele
        // desfaria o registro do consumo.
        try (Handle h = jdbi.open()) {
            return h.inTransaction(x -> {
                int inseridas = h.createUpdate("""
                                insert into outbox_consumo (consumidor, chave_idempotencia, processado_em)
                                values (:consumidor, :chave, :agora)
                                on conflict do nothing
                                """)
                        .bind("consumidor", consumidor)
                        .bind("chave", chaveIdempotencia)
                        .bind("agora", Timestamp.from(clock.instant()))
                        .execute();
                if (inseridas == 0) {
                    return false;
                }
                acao.useHandle(h);
                return true;
            });
        }
    }
}
