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
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** Estado de coleta por (fonte, cnpj_consultado, ambiente) com CAS do cursor (E3-S02). */
class JdbiEstadoColetaTest extends PostgresTestBase {

    private static final AtomicInteger CONTADOR = new AtomicInteger();
    private static final Clock RELOGIO = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    private static final ChaveColeta CHAVE =
            new ChaveColeta(new FonteId("NFE_AN"), new Cnpj("11222333000181"), Ambiente.PRODUCAO);

    private Jdbi jdbi;
    private JdbiEstadoColeta repositorio;

    @BeforeEach
    void prepara() {
        var ds = dataSourceNoSchema("estado_coleta_" + CONTADOR.incrementAndGet());
        Migracoes.aplicar(ds);
        jdbi = Jdbi.create(ds);
        repositorio = new JdbiEstadoColeta(jdbi, RELOGIO);
    }

    @Test
    void criarSeAusenteNasceComCursorInicialNsuZeroVersaoZero() {
        repositorio.criarSeAusente(CHAVE);

        assertThat(repositorio.carregar(CHAVE))
                .contains(new PosicaoColeta(CHAVE, Cursor.inicial(), new Nsu(0), 0));
    }

    @Test
    void criarSeAusenteDuasVezesEhNoOp() {
        repositorio.criarSeAusente(CHAVE);
        jdbi.useHandle(h -> h.execute(
                "update estado_coleta set cursor_token = '42', ult_nsu = 42, versao = 3"));

        repositorio.criarSeAusente(CHAVE);

        assertThat(repositorio.carregar(CHAVE))
                .contains(new PosicaoColeta(CHAVE, new Cursor("42"), new Nsu(42), 3));
    }

    @Test
    void carregarChaveInexistenteDevolveVazio() {
        assertThat(repositorio.carregar(CHAVE)).isEmpty();
    }

    @Test
    void ambientesDiferentesSaoEstadosDiferentes() {
        var homologacao = new ChaveColeta(CHAVE.fonte(), CHAVE.cnpj(), Ambiente.HOMOLOGACAO);
        repositorio.criarSeAusente(CHAVE);
        jdbi.useHandle(h -> h.execute("update estado_coleta set ult_nsu = 7, versao = 1"));

        assertThat(repositorio.carregar(homologacao)).isEmpty();
        repositorio.criarSeAusente(homologacao);

        assertThat(repositorio.carregar(homologacao))
                .contains(new PosicaoColeta(homologacao, Cursor.inicial(), new Nsu(0), 0));
        assertThat(repositorio.carregar(CHAVE).orElseThrow().ultNsu()).isEqualTo(new Nsu(7));
    }

    @Test
    void confirmarNaVersaoLidaAvancaCursorIncrementaVersaoERegistraLedger() {
        var depois = Clock.fixed(Instant.parse("2026-01-01T01:00:00Z"), ZoneOffset.UTC);
        var confirmador = new JdbiEstadoColeta(jdbi, depois);
        repositorio.criarSeAusente(CHAVE);
        var lida = repositorio.carregar(CHAVE).orElseThrow();
        var entradas = List.of(
                new EntradaLedger(new Nsu(1), StatusNsu.RESUMO),
                new EntradaLedger(new Nsu(2), StatusNsu.EVENTO),
                new EntradaLedger(new Nsu(3), StatusNsu.BAIXADO));

        var resultado = confirmador.confirmarLote(
                new ConfirmacaoLote(lida, new Cursor("3"), new Nsu(3), entradas));

        assertThat(resultado).isEqualTo(new ResultadoCas.Aplicado(1));
        assertThat(repositorio.carregar(CHAVE))
                .contains(new PosicaoColeta(CHAVE, new Cursor("3"), new Nsu(3), 1));
        assertThat(ledgerDe(CHAVE)).containsExactlyElementsOf(entradas);
        assertThat(atualizadoEm()).isEqualTo(depois.instant());
    }

    @Test
    void confirmarSemEntradasAvancaSoOCursor() {
        repositorio.criarSeAusente(CHAVE);
        var lida = repositorio.carregar(CHAVE).orElseThrow();

        var resultado = repositorio.confirmarLote(
                new ConfirmacaoLote(lida, new Cursor("9"), new Nsu(9), List.of()));

        assertThat(resultado).isEqualTo(new ResultadoCas.Aplicado(1));
        assertThat(repositorio.carregar(CHAVE))
                .contains(new PosicaoColeta(CHAVE, new Cursor("9"), new Nsu(9), 1));
        assertThat(ledgerDe(CHAVE)).isEmpty();
    }

    private List<EntradaLedger> ledgerDe(ChaveColeta chave) {
        return new JdbiNsuLedger(jdbi, chave.ambiente(), RELOGIO).listarEntradas(chave.fonte(), chave.cnpj());
    }

    private Instant atualizadoEm() {
        return jdbi.withHandle(h -> h.createQuery("select atualizado_em from estado_coleta")
                .mapTo(java.sql.Timestamp.class).one()).toInstant();
    }
}
