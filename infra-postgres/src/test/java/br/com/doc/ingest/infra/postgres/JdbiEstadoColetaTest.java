package br.com.doc.ingest.infra.postgres;

import br.com.doc.ingest.domain.adapter.Cursor;
import br.com.doc.ingest.domain.coleta.ChaveColeta;
import br.com.doc.ingest.domain.coleta.PosicaoColeta;
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
}
