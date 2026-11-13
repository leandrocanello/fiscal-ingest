package br.com.doc.ingest.infra.postgres;

import br.com.doc.ingest.domain.adapter.Cursor;
import br.com.doc.ingest.domain.coleta.ChaveColeta;
import br.com.doc.ingest.domain.coleta.ConfirmacaoLote;
import br.com.doc.ingest.domain.coleta.PosicaoColeta;
import br.com.doc.ingest.domain.coleta.ResultadoCas;
import br.com.doc.ingest.domain.coleta.ResultadoTroca;
import br.com.doc.ingest.domain.ledger.EntradaLedger;
import br.com.doc.ingest.domain.ledger.StatusNsu;
import br.com.doc.ingest.domain.model.Ambiente;
import br.com.doc.ingest.domain.model.Cnpj;
import br.com.doc.ingest.domain.model.FonteId;
import br.com.doc.ingest.domain.model.Nsu;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Caos da rotação (CLAUDE.md §15 item 7, §7.4): trocar o certificado ao mesmo tempo em que
 * um fetcher confirma lote não perde cursor (inv. 1); duas rotações concorrentes têm um vencedor.
 */
class CaosRotacaoTest extends PostgresTestBase {

    private static final AtomicInteger CONTADOR = new AtomicInteger();
    private static final Clock RELOGIO = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    private static final ChaveColeta CHAVE =
            new ChaveColeta(new FonteId("NFE_AN"), new Cnpj("11222333000181"), Ambiente.PRODUCAO);

    private Jdbi jdbi;
    private JdbiEstadoColeta repositorio;

    @BeforeEach
    void prepara() {
        var ds = dataSourceNoSchema("caos_rotacao_" + CONTADOR.incrementAndGet());
        Migracoes.aplicar(ds);
        jdbi = Jdbi.create(ds);
        repositorio = new JdbiEstadoColeta(jdbi, RELOGIO);
        repositorio.criarSeAusente(CHAVE);
        assertThat(repositorio.trocarCertificado(CHAVE, null, 1L)).isInstanceOf(ResultadoTroca.Trocado.class);
    }

    @RepeatedTest(50)
    void rotacaoEConfirmarLoteAoMesmoTempoNaoPerdemCursor() throws Exception {
        var lida = repositorio.carregar(CHAVE).orElseThrow();
        var lote = new ConfirmacaoLote(lida, new Cursor("5"), new Nsu(5),
                List.of(new EntradaLedger(new Nsu(5), StatusNsu.BAIXADO)));

        var resultados = concorrer(List.of(
                () -> repositorio.confirmarLote(lote),
                () -> repositorio.trocarCertificado(CHAVE, 1L, 2L)));

        assertThat(resultados.get(0)).isEqualTo(new ResultadoCas.Aplicado(1));
        assertThat(resultados.get(1)).isInstanceOf(ResultadoTroca.Trocado.class);
        assertThat(repositorio.carregar(CHAVE)).contains(new PosicaoColeta(CHAVE, new Cursor("5"), new Nsu(5), 1));
        assertThat(certificadoId()).isEqualTo(2L);
    }

    @RepeatedTest(50)
    void duasRotacoesConcorrentesTemUmVencedor() throws Exception {
        var resultados = concorrer(List.of(
                () -> repositorio.trocarCertificado(CHAVE, 1L, 2L),
                () -> repositorio.trocarCertificado(CHAVE, 1L, 3L)));

        assertThat(resultados).filteredOn(r -> r instanceof ResultadoTroca.Trocado).hasSize(1);
        var vencedor = resultados.get(0) instanceof ResultadoTroca.Trocado ? 2L : 3L;
        var perdedor = resultados.get(0) instanceof ResultadoTroca.Trocado ? resultados.get(1) : resultados.get(0);
        assertThat(perdedor).isEqualTo(new ResultadoTroca.Conflito(vencedor));
        assertThat(certificadoId()).isEqualTo(vencedor);
    }

    private Long certificadoId() {
        return jdbi.withHandle(h -> h.createQuery("select certificado_id from estado_coleta")
                .mapTo(Long.class).one());
    }

    private static <T> List<T> concorrer(List<Callable<T>> tarefas) throws Exception {
        var barreira = new CyclicBarrier(tarefas.size());
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futuros = new ArrayList<Future<T>>();
            for (var tarefa : tarefas) {
                futuros.add(executor.submit(() -> {
                    barreira.await(10, TimeUnit.SECONDS);
                    return tarefa.call();
                }));
            }
            var resultados = new ArrayList<T>();
            for (var futuro : futuros) {
                resultados.add(futuro.get(30, TimeUnit.SECONDS));
            }
            return resultados;
        }
    }
}
