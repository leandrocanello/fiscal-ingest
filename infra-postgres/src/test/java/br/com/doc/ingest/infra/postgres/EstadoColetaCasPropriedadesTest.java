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
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeTry;
import org.jdbi.v3.core.Jdbi;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Propriedades do CAS do cursor (CLAUDE.md §15 item 2, inv. 1 e 6): sequências aleatórias de
 * confirmações, algumas a partir de posições velhas e algumas tentando retroceder o NSU.
 */
class EstadoColetaCasPropriedadesTest extends PostgresTestBase {

    private static final Clock RELOGIO = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    private static final ChaveColeta CHAVE =
            new ChaveColeta(new FonteId("NFE_AN"), new Cnpj("11222333000181"), Ambiente.PRODUCAO);

    private static Jdbi jdbi;
    private static JdbiEstadoColeta repositorio;

    /** Uma confirmação: {@code posicaoVelha} escolhe uma posição já vista (ou -1 = recarregar). */
    record Operacao(int posicaoVelha, long novoUltNsu, List<EntradaLedger> candidatas) { }

    /** O que se observou ao aplicar uma sequência de operações. */
    record Execucao(List<Long> ultNsuObservados, List<ResultadoCas> resultados,
                    List<ConfirmacaoLote> confirmacoes, PosicaoColeta finalLida, List<EntradaLedger> ledger) { }

    private static synchronized void garantePronto() {
        if (jdbi == null) {
            var ds = dataSourceNoSchema("estado_coleta_propriedades");
            Migracoes.aplicar(ds);
            jdbi = Jdbi.create(ds);
            repositorio = new JdbiEstadoColeta(jdbi, RELOGIO);
        }
    }

    @BeforeTry
    void limpa() {
        garantePronto();
        jdbi.useHandle(h -> h.execute("truncate ledger_nsu, estado_coleta cascade"));
        repositorio.criarSeAusente(CHAVE);
    }

    @Provide
    Arbitrary<List<Operacao>> operacoes() {
        var entrada = Combinators.combine(
                        Arbitraries.longs().between(1, 40),
                        Arbitraries.of(StatusNsu.class))
                .as((n, s) -> new EntradaLedger(new Nsu(n), s));
        return Combinators.combine(
                        Arbitraries.integers().between(-1, 5),
                        Arbitraries.longs().between(0, 40),
                        entrada.list().ofMaxSize(6))
                .as(Operacao::new)
                .list().ofMinSize(1).ofMaxSize(12);
    }

    @Property(tries = 15)
    void ultNsuNuncaDecresce(@ForAll("operacoes") List<Operacao> operacoes) {
        var observados = executar(operacoes).ultNsuObservados();

        for (int i = 1; i < observados.size(); i++) {
            assertThat(observados.get(i)).as("passo %d", i).isGreaterThanOrEqualTo(observados.get(i - 1));
        }
    }

    @Property(tries = 15)
    void versaoIgualANumeroDeAplicados(@ForAll("operacoes") List<Operacao> operacoes) {
        var execucao = executar(operacoes);

        long aplicados = execucao.resultados().stream().filter(ResultadoCas.Aplicado.class::isInstance).count();
        assertThat(execucao.finalLida().versao()).isEqualTo(aplicados);
    }

    @Property(tries = 15)
    void ledgerContemExatamenteAsEntradasDosAplicados(@ForAll("operacoes") List<Operacao> operacoes) {
        var execucao = executar(operacoes);

        Map<Long, EntradaLedger> esperado = new TreeMap<>();
        for (int i = 0; i < execucao.resultados().size(); i++) {
            if (execucao.resultados().get(i) instanceof ResultadoCas.Aplicado) {
                for (var e : execucao.confirmacoes().get(i).entradas()) {
                    esperado.putIfAbsent(e.nsu().valor(), e);
                }
            }
        }
        assertThat(execucao.ledger()).containsExactlyElementsOf(esperado.values());
    }

    private static Execucao executar(List<Operacao> operacoes) {
        var vistas = new ArrayList<PosicaoColeta>();
        var observados = new ArrayList<Long>();
        var resultados = new ArrayList<ResultadoCas>();
        var confirmacoes = new ArrayList<ConfirmacaoLote>();
        var atual = repositorio.carregar(CHAVE).orElseThrow();
        vistas.add(atual);
        observados.add(atual.ultNsu().valor());
        for (var op : operacoes) {
            var lida = op.posicaoVelha() < 0
                    ? repositorio.carregar(CHAVE).orElseThrow()
                    : vistas.get(Math.max(0, vistas.size() - 1 - op.posicaoVelha()));
            var entradas = op.candidatas().stream()
                    .filter(e -> e.nsu().valor() <= op.novoUltNsu())
                    .toList();
            var confirmacao = new ConfirmacaoLote(lida, new Cursor(Long.toString(op.novoUltNsu())),
                    new Nsu(op.novoUltNsu()), entradas);

            resultados.add(repositorio.confirmarLote(confirmacao));
            confirmacoes.add(confirmacao);

            atual = repositorio.carregar(CHAVE).orElseThrow();
            vistas.add(atual);
            observados.add(atual.ultNsu().valor());
        }
        var ledger = new JdbiNsuLedger(jdbi, CHAVE.ambiente(), RELOGIO).listarEntradas(CHAVE.fonte(), CHAVE.cnpj());
        return new Execucao(observados, resultados, confirmacoes, atual, ledger);
    }
}
