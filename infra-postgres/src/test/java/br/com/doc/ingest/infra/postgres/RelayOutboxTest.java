package br.com.doc.ingest.infra.postgres;

import br.com.doc.ingest.domain.outbox.MensagemOutbox;
import br.com.doc.ingest.domain.outbox.Publicador;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Relay do outbox: entrega pelo menos uma vez, marcando publicado_em (E3-S04). */
class RelayOutboxTest extends PostgresTestBase {

    private static final AtomicInteger CONTADOR = new AtomicInteger();
    private static final Clock RELOGIO = Clock.fixed(Instant.parse("2026-02-01T00:00:00Z"), ZoneOffset.UTC);

    private Jdbi jdbi;
    private final List<String> publicadas = new ArrayList<>();

    @BeforeEach
    void prepara() {
        var ds = dataSourceNoSchema("relay_" + CONTADOR.incrementAndGet());
        Migracoes.aplicar(ds);
        jdbi = Jdbi.create(ds);
    }

    private void insere(String idempotencia) {
        jdbi.useHandle(h -> OutboxSql.inserir(h,
                new MensagemOutbox("docs.coletados", "k", 1, idempotencia, "{}"), RELOGIO.instant()));
    }

    private long pendentes() {
        return jdbi.withHandle(h -> h.createQuery("select count(*) from outbox where publicado_em is null")
                .mapTo(Long.class).one());
    }

    private Publicador gravador() {
        return m -> publicadas.add(m.chaveIdempotencia());
    }

    @Test
    void publicaPendentesEmOrdemDeIdEMarcaPublicadoEm() {
        insere("a");
        insere("b");
        insere("c");

        int n = new RelayOutbox(jdbi, RELOGIO, gravador(), 10).publicarPendentes();

        assertThat(n).isEqualTo(3);
        assertThat(publicadas).containsExactly("a", "b", "c");
        assertThat(pendentes()).isZero();
        assertThat(jdbi.withHandle(h -> h.createQuery("select distinct publicado_em from outbox")
                .mapTo(Timestamp.class).one()).toInstant()).isEqualTo(RELOGIO.instant());
    }

    @Test
    void semPendentesDevolveZero() {
        assertThat(new RelayOutbox(jdbi, RELOGIO, gravador(), 10).publicarPendentes()).isZero();
    }

    @Test
    void respeitaTamanhoDoLote() {
        insere("a");
        insere("b");
        insere("c");

        assertThat(new RelayOutbox(jdbi, RELOGIO, gravador(), 2).publicarPendentes()).isEqualTo(2);
        assertThat(pendentes()).isEqualTo(1);
    }

    @Test
    void falhaDoPublicadorNaoMarcaEARetentaNaProximaRodada() {
        insere("a");
        insere("b");
        insere("c");
        var falhar = new AtomicInteger(1);
        Publicador instavel = m -> {
            if (m.chaveIdempotencia().equals("b") && falhar.getAndDecrement() > 0) {
                throw new IllegalStateException("fora do ar");
            }
            publicadas.add(m.chaveIdempotencia());
        };
        var relay = new RelayOutbox(jdbi, RELOGIO, instavel, 10);

        assertThatThrownBy(relay::publicarPendentes).isInstanceOf(IllegalStateException.class);
        assertThat(publicadas).containsExactly("a");
        assertThat(pendentes()).isEqualTo(2);

        assertThat(relay.publicarPendentes()).isEqualTo(2);
        assertThat(publicadas).containsExactly("a", "b", "c");
        assertThat(pendentes()).isZero();
    }
}
