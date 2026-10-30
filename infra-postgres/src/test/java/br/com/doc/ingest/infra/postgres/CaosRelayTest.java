package br.com.doc.ingest.infra.postgres;

import br.com.doc.ingest.domain.outbox.MensagemOutbox;
import br.com.doc.ingest.domain.outbox.Publicador;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Caos (CLAUDE.md §15 item 7, 3º marcador): relays concorrentes e queda entre publicar e marcar.
 * Entrega pelo menos uma vez, sem perda, com ordem por chave (E3-S04).
 */
class CaosRelayTest extends PostgresTestBase {

    private static final AtomicInteger CONTADOR = new AtomicInteger();
    private static final Clock RELOGIO = Clock.fixed(Instant.parse("2026-02-01T00:00:00Z"), ZoneOffset.UTC);
    private static final int CHAVES = 5;
    private static final int MENSAGENS = 50;

    private Jdbi jdbi;

    @BeforeEach
    void prepara() {
        var ds = dataSourceNoSchema("caos_relay_" + CONTADOR.incrementAndGet());
        Migracoes.aplicar(ds);
        jdbi = Jdbi.create(ds);
    }

    private void insere(String chave, String idempotencia) {
        jdbi.useHandle(h -> OutboxSql.inserir(h,
                new MensagemOutbox("docs.coletados", chave, 1, idempotencia, "{}"), RELOGIO.instant()));
    }

    private long pendentes() {
        return jdbi.withHandle(h -> h.createQuery("select count(*) from outbox where publicado_em is null")
                .mapTo(Long.class).one());
    }

    @RepeatedTest(20)
    void doisRelaysConcorrentesNaoPublicamAMesmaMensagemDuasVezes() throws Exception {
        for (int i = 0; i < MENSAGENS; i++) {
            insere("k" + (i % CHAVES), "m" + String.format("%02d", i));
        }
        List<String> publicadas = Collections.synchronizedList(new ArrayList<>());
        Publicador fake = m -> publicadas.add(m.chaveIdempotencia());
        int relays = 2 + (CONTADOR.get() % 3);
        var barreira = new CyclicBarrier(relays);

        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> futuros = new ArrayList<>();
            for (int r = 0; r < relays; r++) {
                futuros.add(pool.submit(() -> {
                    var relay = new RelayOutbox(jdbi, RELOGIO, fake, 100);
                    barreira.await();
                    while (relay.publicarPendentes() > 0 || pendentes() > 0) {
                        Thread.onSpinWait();
                    }
                    return null;
                }));
            }
            for (var f : futuros) {
                f.get();
            }
        }

        assertThat(publicadas).hasSize(MENSAGENS).doesNotHaveDuplicates();
        for (int k = 0; k < CHAVES; k++) {
            int chave = k;
            assertThat(publicadas.stream().filter(s -> Integer.parseInt(s.substring(1)) % CHAVES == chave).toList())
                    .isSorted();
        }
    }

    @Test
    void quedaDepoisDePublicarAntesDeMarcarRepublicaENaoPerde() {
        insere("A", "a1");
        insere("A", "a2");
        List<String> publicadas = new ArrayList<>();
        var relay = new RelayOutbox(jdbi, RELOGIO, m -> publicadas.add(m.chaveIdempotencia()), 10);

        jdbi.useHandle(h -> {
            h.execute("""
                    create function queda_ao_marcar() returns trigger language plpgsql as $$
                    begin
                      raise exception 'queda simulada ao marcar';
                    end $$
                    """);
            h.execute("""
                    create trigger queda_ao_marcar before update on outbox
                    for each row execute function queda_ao_marcar()
                    """);
        });
        assertThatThrownBy(relay::publicarPendentes).hasMessageContaining("queda simulada");
        assertThat(publicadas).containsExactly("a1");
        assertThat(pendentes()).isEqualTo(2);

        jdbi.useHandle(h -> h.execute("drop trigger queda_ao_marcar on outbox"));
        assertThat(relay.publicarPendentes()).isEqualTo(2);

        assertThat(publicadas).containsExactly("a1", "a1", "a2");
        assertThat(pendentes()).isZero();
    }
}
