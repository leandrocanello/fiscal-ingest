package br.com.doc.ingest.infra.postgres;

import br.com.doc.ingest.domain.adapter.Cursor;
import br.com.doc.ingest.domain.coleta.ChaveColeta;
import br.com.doc.ingest.domain.coleta.ConfirmacaoLote;
import br.com.doc.ingest.domain.coleta.PosicaoColeta;
import br.com.doc.ingest.domain.coleta.ResultadoCas;
import br.com.doc.ingest.domain.ledger.EntradaLedger;
import br.com.doc.ingest.domain.ledger.StatusNsu;
import br.com.doc.ingest.domain.model.Ambiente;
import br.com.doc.ingest.domain.model.Cnpj;
import br.com.doc.ingest.domain.model.FonteId;
import br.com.doc.ingest.domain.model.Nsu;
import br.com.doc.ingest.domain.outbox.MensagemOutbox;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Caos (CLAUDE.md §15 item 7, 1º marcador): queda depois de gravar o ledger e antes do CAS.
 * A transação inteira é desfeita e o mesmo lote, relido, é confirmado sem perda nem duplicata (inv. 5).
 */
class CaosFalhaNoMeioTest extends PostgresTestBase {

    private static final AtomicInteger CONTADOR = new AtomicInteger();
    private static final Clock RELOGIO = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    private static final ChaveColeta CHAVE =
            new ChaveColeta(new FonteId("NFE_AN"), new Cnpj("11222333000181"), Ambiente.PRODUCAO);

    private Jdbi jdbi;
    private JdbiEstadoColeta repositorio;

    @BeforeEach
    void prepara() {
        // Schema próprio: o trigger de falha não vaza para os outros testes.
        var ds = dataSourceNoSchema("caos_falha_" + CONTADOR.incrementAndGet());
        Migracoes.aplicar(ds);
        jdbi = Jdbi.create(ds);
        repositorio = new JdbiEstadoColeta(jdbi, RELOGIO);
        repositorio.criarSeAusente(CHAVE);
    }

    @Test
    void falhaAntesDoCasNaoAvancaNemRegistraEReprocessarFunciona() {
        var lida = repositorio.carregar(CHAVE).orElseThrow();
        var entradas = LongStream.rangeClosed(1, 4)
                .mapToObj(n -> new EntradaLedger(new Nsu(n), StatusNsu.RESUMO))
                .toList();
        var lote = new ConfirmacaoLote(lida, new Cursor("4"), new Nsu(4), entradas);

        simularQuedaNoCas();
        assertThatThrownBy(() -> repositorio.confirmarLote(lote))
                .hasMessageContaining("queda simulada");

        assertThat(ledger()).isEmpty();
        assertThat(repositorio.carregar(CHAVE)).contains(lida);

        removerQueda();
        assertThat(repositorio.confirmarLote(lote)).isEqualTo(new ResultadoCas.Aplicado(1));

        assertThat(ledger()).containsExactlyElementsOf(entradas);
        assertThat(repositorio.carregar(CHAVE)).contains(
                new PosicaoColeta(CHAVE, new Cursor("4"), new Nsu(4), 1));
    }

    @Test
    void falhaAntesDoCasNaoGravaOutbox() {
        var lida = repositorio.carregar(CHAVE).orElseThrow();
        var lote = new ConfirmacaoLote(lida, new Cursor("4"), new Nsu(4),
                List.of(new EntradaLedger(new Nsu(4), StatusNsu.RESUMO)),
                List.of(new MensagemOutbox("docs.coletados", CHAVE.cnpj().valor(), 1, "lote-4", "{\"nsu\":4}")));

        simularQuedaNoCas();
        assertThatThrownBy(() -> repositorio.confirmarLote(lote))
                .hasMessageContaining("queda simulada");

        assertThat(ledger()).isEmpty();
        assertThat(mensagensNoOutbox()).isZero();
        assertThat(repositorio.carregar(CHAVE)).contains(lida);

        removerQueda();
        assertThat(repositorio.confirmarLote(lote)).isEqualTo(new ResultadoCas.Aplicado(1));
        assertThat(mensagensNoOutbox()).isEqualTo(1);
    }

    private long mensagensNoOutbox() {
        return jdbi.withHandle(h -> h.createQuery("select count(*) from outbox").mapTo(Long.class).one());
    }

    private void simularQuedaNoCas() {
        jdbi.useHandle(h -> {
            h.execute("""
                    create function queda_simulada() returns trigger language plpgsql as $$
                    begin
                      raise exception 'queda simulada antes do CAS';
                    end $$
                    """);
            h.execute("""
                    create trigger queda_simulada before update on estado_coleta
                    for each row execute function queda_simulada()
                    """);
        });
    }

    private void removerQueda() {
        jdbi.useHandle(h -> h.execute("drop trigger queda_simulada on estado_coleta"));
    }

    private List<EntradaLedger> ledger() {
        return new JdbiNsuLedger(jdbi, CHAVE.ambiente(), RELOGIO).listarEntradas(CHAVE.fonte(), CHAVE.cnpj());
    }
}
