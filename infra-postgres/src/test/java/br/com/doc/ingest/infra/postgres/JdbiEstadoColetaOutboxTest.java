package br.com.doc.ingest.infra.postgres;

import br.com.doc.ingest.domain.adapter.Cursor;
import br.com.doc.ingest.domain.coleta.ChaveColeta;
import br.com.doc.ingest.domain.coleta.ConfirmacaoLote;
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

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** Mensagens do outbox gravadas na mesma transação do ledger + CAS (E3-S04, invariante 5). */
class JdbiEstadoColetaOutboxTest extends PostgresTestBase {

    private static final AtomicInteger CONTADOR = new AtomicInteger();
    private static final Clock RELOGIO = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    private static final ChaveColeta CHAVE =
            new ChaveColeta(new FonteId("NFE_AN"), new Cnpj("11222333000181"), Ambiente.PRODUCAO);

    private Jdbi jdbi;
    private JdbiEstadoColeta repositorio;

    @BeforeEach
    void prepara() {
        var ds = dataSourceNoSchema("estado_outbox_" + CONTADOR.incrementAndGet());
        Migracoes.aplicar(ds);
        jdbi = Jdbi.create(ds);
        repositorio = new JdbiEstadoColeta(jdbi, RELOGIO);
        repositorio.criarSeAusente(CHAVE);
    }

    @Test
    void confirmarGravaMensagensNaMesmaTransacao() {
        var lida = repositorio.carregar(CHAVE).orElseThrow();
        var resultado = repositorio.confirmarLote(lote(lida, "lote-1"));

        assertThat(resultado).isEqualTo(new ResultadoCas.Aplicado(1));
        assertThat(contarOutbox()).isEqualTo(1);
        assertThat(consulta("select topico || '|' || chave || '|' || chave_idempotencia from outbox", String.class))
                .isEqualTo("docs.coletados|11222333000181|lote-1");
        assertThat(consulta("select publicado_em is null from outbox", Boolean.class)).isTrue();
        assertThat(consulta("select criado_em from outbox", Timestamp.class).toInstant())
                .isEqualTo(RELOGIO.instant());
    }

    @Test
    void reconfirmarMesmoLoteNaoDuplicaMensagem() {
        var lida = repositorio.carregar(CHAVE).orElseThrow();
        repositorio.confirmarLote(lote(lida, "lote-1"));
        var relida = repositorio.carregar(CHAVE).orElseThrow();

        var resultado = repositorio.confirmarLote(lote(relida, "lote-1"));

        assertThat(resultado).isInstanceOf(ResultadoCas.Aplicado.class);
        assertThat(contarOutbox()).isEqualTo(1);
    }

    @Test
    void conflitoDeVersaoNaoGravaMensagens() {
        var velha = repositorio.carregar(CHAVE).orElseThrow();
        repositorio.confirmarLote(new ConfirmacaoLote(velha, new Cursor("1"), new Nsu(1), List.of()));

        var resultado = repositorio.confirmarLote(lote(velha, "lote-1"));

        assertThat(resultado).isInstanceOf(ResultadoCas.ConflitoDeVersao.class);
        assertThat(contarOutbox()).isZero();
    }

    @Test
    void retrocessoRecusadoNaoGravaMensagens() {
        repositorio.confirmarLote(new ConfirmacaoLote(
                repositorio.carregar(CHAVE).orElseThrow(), new Cursor("5"), new Nsu(5), List.of()));
        var lida = repositorio.carregar(CHAVE).orElseThrow();

        var resultado = repositorio.confirmarLote(lote(lida, "lote-1"));

        assertThat(resultado).isInstanceOf(ResultadoCas.RetrocessoRecusado.class);
        assertThat(contarOutbox()).isZero();
    }

    private static ConfirmacaoLote lote(br.com.doc.ingest.domain.coleta.PosicaoColeta lida, String idempotencia) {
        return new ConfirmacaoLote(lida, new Cursor("3"), new Nsu(3),
                List.of(new EntradaLedger(new Nsu(3), StatusNsu.BAIXADO)),
                List.of(new MensagemOutbox("docs.coletados", "11222333000181", 1, idempotencia,
                        "{\"nsu\":3}")));
    }

    private <T> T consulta(String sql, Class<T> tipo) {
        return jdbi.withHandle(h -> h.createQuery(sql).mapTo(tipo).one());
    }

    private long contarOutbox() {
        return jdbi.withHandle(h -> h.createQuery("select count(*) from outbox").mapTo(Long.class).one());
    }
}
