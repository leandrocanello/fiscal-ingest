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
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Caos (CLAUDE.md §15 item 7): fetchers concorrentes no mesmo CNPJ — o CAS do cursor
 * garante que só um avança por versão (inv. 2, §6.5 (c)) e o perdedor não deixa rastro no ledger.
 */
class CaosDoisFetchersTest extends PostgresTestBase {

    private static final AtomicInteger CONTADOR = new AtomicInteger();
    private static final Clock RELOGIO = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    private static final ChaveColeta CHAVE =
            new ChaveColeta(new FonteId("NFE_AN"), new Cnpj("11222333000181"), Ambiente.PRODUCAO);

    private static final List<StatusNsu> STATUS =
            List.of(StatusNsu.BAIXADO, StatusNsu.RESUMO, StatusNsu.EVENTO, StatusNsu.DESCARTADO);

    private Jdbi jdbi;
    private JdbiEstadoColeta repositorio;

    @BeforeEach
    void prepara() {
        var ds = dataSourceNoSchema("caos_fetchers_" + CONTADOR.incrementAndGet());
        Migracoes.aplicar(ds);
        jdbi = Jdbi.create(ds);
        repositorio = new JdbiEstadoColeta(jdbi, RELOGIO);
        repositorio.criarSeAusente(CHAVE);
    }

    @RepeatedTest(50)
    void doisFetchersNaMesmaVersaoSoUmAvanca() throws Exception {
        var lida = repositorio.carregar(CHAVE).orElseThrow();
        var loteA = lote(lida, 5, StatusNsu.RESUMO);
        var loteB = lote(lida, 7, StatusNsu.BAIXADO);

        var resultados = concorrer(List.of(loteA, loteB));

        assertThat(resultados).containsExactlyInAnyOrder(new ResultadoCas.Aplicado(1), new ResultadoCas.ConflitoDeVersao(1));
        var vencedor = resultados.get(0) instanceof ResultadoCas.Aplicado ? loteA : loteB;
        assertThat(repositorio.carregar(CHAVE)).contains(
                new PosicaoColeta(CHAVE, vencedor.novoCursor(), vencedor.novoUltNsu(), 1));
        assertThat(ledger()).containsExactlyElementsOf(vencedor.entradas());
    }

    @Test
    void nFetchersConcorrentesSoUmAvancaPorVersao() throws Exception {
        int fetchers = 8;
        int rodadas = 5;
        ConfirmacaoLote vencedor = null;
        for (int rodada = 1; rodada <= rodadas; rodada++) {
            var lida = repositorio.carregar(CHAVE).orElseThrow();
            var lotes = new ArrayList<ConfirmacaoLote>();
            for (int f = 1; f <= fetchers; f++) {
                lotes.add(lote(lida, lida.ultNsu().valor() + f, STATUS.get(f % STATUS.size())));
            }

            var resultados = concorrer(lotes);

            long aplicados = resultados.stream().filter(ResultadoCas.Aplicado.class::isInstance).count();
            assertThat(aplicados).as("rodada %d", rodada).isEqualTo(1);
            assertThat(resultados).as("rodada %d", rodada)
                    .filteredOn(r -> !(r instanceof ResultadoCas.Aplicado))
                    .containsOnly(new ResultadoCas.ConflitoDeVersao(rodada));
            assertThat(resultados).contains(new ResultadoCas.Aplicado(rodada));
            vencedor = lotes.get(resultados.indexOf(new ResultadoCas.Aplicado(rodada)));
            assertThat(repositorio.carregar(CHAVE)).contains(
                    new PosicaoColeta(CHAVE, vencedor.novoCursor(), vencedor.novoUltNsu(), rodada));
        }

        var nsus = ledger().stream().map(e -> e.nsu().valor()).toList();
        // Só os vencedores gravaram: NSUs contíguos de 1 ao ultNsu final, cada um uma única vez.
        assertThat(nsus).containsExactlyElementsOf(
                LongStream.rangeClosed(1, vencedor.novoUltNsu().valor()).boxed().toList());
    }

    /** Lote com NSUs de {@code lida.ultNsu + 1} até {@code ate}, todos com o mesmo status. */
    private static ConfirmacaoLote lote(PosicaoColeta lida, long ate, StatusNsu status) {
        var entradas = LongStream.rangeClosed(lida.ultNsu().valor() + 1, ate)
                .mapToObj(n -> new EntradaLedger(new Nsu(n), status))
                .toList();
        return new ConfirmacaoLote(lida, new Cursor(Long.toString(ate)), new Nsu(ate), entradas);
    }

    /** Dispara todos os lotes ao mesmo tempo (barreira) e devolve os resultados na ordem dos lotes. */
    private List<ResultadoCas> concorrer(List<ConfirmacaoLote> lotes) throws Exception {
        var barreira = new CyclicBarrier(lotes.size());
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futuros = new ArrayList<Future<ResultadoCas>>();
            for (var lote : lotes) {
                futuros.add(executor.submit(() -> {
                    barreira.await(10, TimeUnit.SECONDS);
                    return repositorio.confirmarLote(lote);
                }));
            }
            var resultados = new ArrayList<ResultadoCas>();
            for (var futuro : futuros) {
                resultados.add(futuro.get(30, TimeUnit.SECONDS));
            }
            return resultados;
        }
    }

    private List<EntradaLedger> ledger() {
        return new JdbiNsuLedger(jdbi, CHAVE.ambiente(), RELOGIO).listarEntradas(CHAVE.fonte(), CHAVE.cnpj());
    }
}
