package br.com.doc.ingest.infra.postgres;

import br.com.doc.ingest.domain.outbox.MensagemOutbox;
import br.com.doc.ingest.domain.outbox.Publicador;
import org.jdbi.v3.core.Jdbi;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
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

    private static final String SEPARADOR = "\u0001";

    private enum Resultado { PUBLICADA, FALHOU, VAZIO }

    /**
     * Publica até {@code tamanhoLote} mensagens pendentes; devolve quantas foram publicadas e marcadas. Uma mensagem
     * que falha deixa a sua chave de fora do resto da rodada (as demais seguem: falha isolada, §6.5); ao fim, a
     * primeira falha é relançada.
     */
    public int publicarPendentes() {
        int publicadas = 0;
        List<String> excluidas = new ArrayList<>();
        RuntimeException primeiraFalha = null;
        while (publicadas < tamanhoLote) {
            var falha = new RuntimeException[1];
            var chave = new String[1];
            Resultado r = publicarUma(excluidas, falha, chave);
            if (r == Resultado.VAZIO) {
                break;
            }
            if (r == Resultado.FALHOU) {
                excluidas.add(chave[0]);
                if (primeiraFalha == null) {
                    primeiraFalha = falha[0];
                }
                continue;
            }
            publicadas++;
        }
        if (primeiraFalha != null) {
            throw primeiraFalha;
        }
        return publicadas;
    }

    private Resultado publicarUma(List<String> excluidas, RuntimeException[] falha, String[] chave) {
        return jdbi.inTransaction(h -> {
            Optional<Pendente> achada = h.createQuery("""
                            select id, topico, chave, versao_schema, chave_idempotencia, cast(payload as text) as payload
                            from outbox o
                            where o.publicado_em is null
                              and (o.topico || chr(1) || o.chave) <> all (:excluidas)
                              and not exists (select 1 from outbox o2
                                              where o2.topico = o.topico and o2.chave = o.chave
                                                and o2.id < o.id and o2.publicado_em is null)
                            order by o.id
                            limit 1
                            for update skip locked
                            """)
                    .bindArray("excluidas", String.class, excluidas)
                    .map((rs, ctx) -> new Pendente(rs.getLong("id"), new MensagemOutbox(
                            rs.getString("topico"), rs.getString("chave"), rs.getInt("versao_schema"),
                            rs.getString("chave_idempotencia"), rs.getString("payload"))))
                    .findOne();
            if (achada.isEmpty()) {
                return Resultado.VAZIO;
            }
            try {
                publicador.publicar(achada.get().mensagem());
            } catch (RuntimeException e) {
                falha[0] = e;
                chave[0] = achada.get().mensagem().topico() + SEPARADOR + achada.get().mensagem().chave();
                return Resultado.FALHOU;
            }
            h.createUpdate("update outbox set publicado_em = :agora where id = :id")
                    .bind("agora", Timestamp.from(clock.instant()))
                    .bind("id", achada.get().id())
                    .execute();
            return Resultado.PUBLICADA;
        });
    }
}
