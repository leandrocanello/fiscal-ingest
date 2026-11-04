package br.com.doc.ingest.infra.postgres;

import br.com.doc.ingest.domain.outbox.MensagemOutbox;
import org.jdbi.v3.core.Handle;

import java.sql.Timestamp;
import java.time.Instant;

/** SQL da {@code outbox}: inserção idempotente pela {@code chave_idempotencia} (ADR-015). */
final class OutboxSql {

    private OutboxSql() {
    }

    static void inserir(Handle h, MensagemOutbox m, Instant criadoEm) {
        h.createUpdate("""
                        insert into outbox (topico, chave, versao_schema, chave_idempotencia, payload, criado_em)
                        values (:topico, :chave, :versao, :idempotencia, cast(:payload as jsonb), :criadoEm)
                        on conflict (chave_idempotencia) do nothing
                        """)
                .bind("topico", m.topico())
                .bind("chave", m.chave())
                .bind("versao", m.versaoSchema())
                .bind("idempotencia", m.chaveIdempotencia())
                .bind("payload", m.payloadJson())
                .bind("criadoEm", Timestamp.from(criadoEm))
                .execute();
    }
}
