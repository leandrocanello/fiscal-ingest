package br.com.doc.ingest.infra.postgres;

import br.com.doc.ingest.domain.outbox.MensagemOutbox;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Consumo idempotente do outbox (E3-S04): mensagem republicada tem efeito aplicado uma vez. */
class RegistroDeConsumoTest extends PostgresTestBase {

    private static final AtomicInteger CONTADOR = new AtomicInteger();
    private static final Clock RELOGIO = Clock.fixed(Instant.parse("2026-02-01T00:00:00Z"), ZoneOffset.UTC);

    private Jdbi jdbi;
    private RegistroDeConsumo registro;

    @BeforeEach
    void prepara() {
        var ds = dataSourceNoSchema("consumo_" + CONTADOR.incrementAndGet());
        Migracoes.aplicar(ds);
        jdbi = Jdbi.create(ds);
        registro = new RegistroDeConsumo(jdbi, RELOGIO);
    }

    @Test
    void executaAAcaoUmaVezPorConsumidorEChave() {
        AtomicInteger execucoes = new AtomicInteger();

        boolean primeira = registro.executarUmaVez("proc", "k1", h -> execucoes.incrementAndGet());
        boolean segunda = registro.executarUmaVez("proc", "k1", h -> execucoes.incrementAndGet());

        assertThat(primeira).isTrue();
        assertThat(segunda).isFalse();
        assertThat(execucoes).hasValue(1);
    }

    @Test
    void consumidoresDiferentesSaoIndependentes() {
        AtomicInteger execucoes = new AtomicInteger();

        registro.executarUmaVez("a", "k1", h -> execucoes.incrementAndGet());
        boolean outro = registro.executarUmaVez("b", "k1", h -> execucoes.incrementAndGet());

        assertThat(outro).isTrue();
        assertThat(execucoes).hasValue(2);
    }

    @Test
    void falhaNaAcaoNaoRegistraEPermiteRetentar() {
        assertThatThrownBy(() -> registro.executarUmaVez("proc", "k1", h -> {
            throw new IllegalStateException("falhou");
        })).hasMessageContaining("falhou");

        AtomicInteger execucoes = new AtomicInteger();
        boolean retentativa = registro.executarUmaVez("proc", "k1", h -> execucoes.incrementAndGet());

        assertThat(retentativa).isTrue();
        assertThat(execucoes).hasValue(1);
    }

    @Test
    void relayPublicaDuasVezesConsumidorProcessaUmaVez() {
        jdbi.useHandle(h -> {
            OutboxSql.inserir(h, new MensagemOutbox("docs.coletados", "A", 1, "a1", "{}"), RELOGIO.instant());
            OutboxSql.inserir(h, new MensagemOutbox("docs.coletados", "A", 1, "a2", "{}"), RELOGIO.instant());
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
        List<String> efeitos = new ArrayList<>();
        var relay = new RelayOutbox(jdbi, RELOGIO,
                m -> registro.executarUmaVez("proc", m.chaveIdempotencia(), h -> efeitos.add(m.chaveIdempotencia())),
                10);

        assertThatThrownBy(relay::publicarPendentes).hasMessageContaining("queda simulada");
        jdbi.useHandle(h -> h.execute("drop trigger queda_ao_marcar on outbox"));
        relay.publicarPendentes();

        assertThat(efeitos).containsExactly("a1", "a2");
    }
}
