package br.com.doc.ingest.infra.postgres;

import br.com.doc.ingest.domain.outbox.MensagemOutbox;
import br.com.doc.ingest.domain.outbox.Publicador;
import org.jdbi.v3.core.Jdbi;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.Optional;

/**
 * Relay do outbox (ADR-015): lê pendentes com {@code FOR UPDATE SKIP LOCKED}, entrega ao {@link Publicador} e
 * marca {@code publicado_em}. Uma transação por mensagem; entrega pelo menos uma vez (queda entre publicar e
 * marcar republica). Falha do publicador propaga e deixa a mensagem pendente.
 */
public final class RelayOutbox {

    private record Pendente(long id, MensagemOutbox mensagem) {
    }

    private final Jdbi jdbi;
    private final Clock clock;
    private final Publicador publicador;
    private final int tamanhoLote;

    public RelayOutbox(Jdbi jdbi, Clock clock, Publicador publicador, int tamanhoLote) {
        if (tamanhoLote < 1) {
            throw new IllegalArgumentException("tamanhoLote deve ser >= 1");
        }
        this.jdbi = jdbi;
        this.clock = clock;
        this.publicador = publicador;
        this.tamanhoLote = tamanhoLote;
    }

    /** Publica até {@code tamanhoLote} mensagens pendentes; devolve quantas foram publicadas e marcadas. */
    public int publicarPendentes() {
        int publicadas = 0;
        while (publicadas < tamanhoLote && publicarUma()) {
            publicadas++;
        }
        return publicadas;
    }

    private boolean publicarUma() {
        return jdbi.inTransaction(h -> {
            Optional<Pendente> achada = h.createQuery("""
                            select id, topico, chave, versao_schema, chave_idempotencia, cast(payload as text) as payload
                            from outbox o
                            where o.publicado_em is null
                            order by o.id
                            limit 1
                            for update skip locked
                            """)
                    .map((rs, ctx) -> new Pendente(rs.getLong("id"), new MensagemOutbox(
                            rs.getString("topico"), rs.getString("chave"), rs.getInt("versao_schema"),
                            rs.getString("chave_idempotencia"), rs.getString("payload"))))
                    .findOne();
            if (achada.isEmpty()) {
                return false;
            }
            publicador.publicar(achada.get().mensagem());
            h.createUpdate("update outbox set publicado_em = :agora where id = :id")
                    .bind("agora", Timestamp.from(clock.instant()))
                    .bind("id", achada.get().id())
                    .execute();
            return true;
        });
    }
}
