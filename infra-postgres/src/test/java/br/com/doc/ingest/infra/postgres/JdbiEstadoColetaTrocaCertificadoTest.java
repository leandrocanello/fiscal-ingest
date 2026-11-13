package br.com.doc.ingest.infra.postgres;

import br.com.doc.ingest.domain.coleta.ChaveColeta;
import br.com.doc.ingest.domain.coleta.ResultadoTroca;
import br.com.doc.ingest.domain.coleta.VinculoCertificado;
import br.com.doc.ingest.domain.model.Ambiente;
import br.com.doc.ingest.domain.model.Cnpj;
import br.com.doc.ingest.domain.model.FonteId;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** Troca do {@code certificado_id} por CAS, sem tocar no cursor (E4-S04, CLAUDE.md §7.4). */
class JdbiEstadoColetaTrocaCertificadoTest extends PostgresTestBase {

    private static final AtomicInteger CONTADOR = new AtomicInteger();
    private static final Clock RELOGIO = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    private static final ChaveColeta CHAVE =
            new ChaveColeta(new FonteId("NFE_AN"), new Cnpj("11222333000181"), Ambiente.PRODUCAO);

    private Jdbi jdbi;
    private JdbiEstadoColeta repositorio;

    @BeforeEach
    void prepara() {
        var ds = dataSourceNoSchema("troca_cert_" + CONTADOR.incrementAndGet());
        Migracoes.aplicar(ds);
        jdbi = Jdbi.create(ds);
        repositorio = new JdbiEstadoColeta(jdbi, RELOGIO);
    }

    @Test
    void trocaQuandoOAtualEhOEsperado() {
        repositorio.criarSeAusente(CHAVE);
        definirCertificado(5L);

        assertThat(repositorio.trocarCertificado(CHAVE, 5L, 6L)).isEqualTo(new ResultadoTroca.Trocado());
        assertThat(certificado()).isEqualTo(6L);
    }

    @Test
    void trocaDeNuloParaOPrimeiro() {
        repositorio.criarSeAusente(CHAVE);

        assertThat(repositorio.trocarCertificado(CHAVE, null, 7L)).isEqualTo(new ResultadoTroca.Trocado());
        assertThat(certificado()).isEqualTo(7L);
    }

    @Test
    void atualDiferenteDoEsperadoDevolveConflitoSemEfeito() {
        repositorio.criarSeAusente(CHAVE);
        definirCertificado(5L);

        assertThat(repositorio.trocarCertificado(CHAVE, 4L, 6L)).isEqualTo(new ResultadoTroca.Conflito(5L));
        assertThat(certificado()).isEqualTo(5L);

        assertThat(repositorio.trocarCertificado(CHAVE, null, 6L)).isEqualTo(new ResultadoTroca.Conflito(5L));
        assertThat(certificado()).isEqualTo(5L);
    }

    @Test
    void chaveInexistenteDevolveNaoEncontrada() {
        assertThat(repositorio.trocarCertificado(CHAVE, null, 1L)).isEqualTo(new ResultadoTroca.NaoEncontrada());
    }

    @Test
    void trocaParaOMesmoEhSemMudanca() {
        repositorio.criarSeAusente(CHAVE);
        definirCertificado(5L);

        assertThat(repositorio.trocarCertificado(CHAVE, 5L, 5L)).isEqualTo(new ResultadoTroca.SemMudanca());
        assertThat(certificado()).isEqualTo(5L);
    }

    @Test
    void trocaNaoMexeEmCursorUltNsuVersaoLeaseNemBloqueio() {
        repositorio.criarSeAusente(CHAVE);
        jdbi.useHandle(h -> h.execute("""
                update estado_coleta set certificado_id = 5, cursor_token = '42', ult_nsu = 42, max_nsu = 50,
                    versao = 3, estado = 'BLOQUEADO_ATE', bloqueado_ate = '2026-02-01T00:00:00Z',
                    due_at = '2026-02-01T00:00:00Z', lease_owner = 'outro-worker', lease_ate = '2099-01-01T00:00:00Z'
                """));
        var antes = fotoSemCertificadoENem();

        assertThat(repositorio.trocarCertificado(CHAVE, 5L, 6L)).isEqualTo(new ResultadoTroca.Trocado());

        assertThat(certificado()).isEqualTo(6L);
        assertThat(fotoSemCertificadoENem()).isEqualTo(antes);
    }

    @Test
    void horaDaTrocaVemDoClock() {
        var depois = Clock.fixed(Instant.parse("2026-03-04T05:06:07Z"), ZoneOffset.UTC);
        repositorio.criarSeAusente(CHAVE);

        new JdbiEstadoColeta(jdbi, depois).trocarCertificado(CHAVE, null, 1L);

        var atualizadoEm = jdbi.withHandle(h -> h.createQuery("select atualizado_em from estado_coleta")
                .map((rs, ctx) -> rs.getTimestamp(1)).one());
        assertThat(atualizadoEm).isEqualTo(Timestamp.from(depois.instant()));
    }

    @Test
    void certificadosDoCnpjListaTodasAsChavesDoCnpj() {
        var homologacao = new ChaveColeta(CHAVE.fonte(), CHAVE.cnpj(), Ambiente.HOMOLOGACAO);
        var outroCnpj = new ChaveColeta(CHAVE.fonte(), new Cnpj("11444777000161"), Ambiente.PRODUCAO);
        repositorio.criarSeAusente(CHAVE);
        repositorio.criarSeAusente(homologacao);
        repositorio.criarSeAusente(outroCnpj);
        repositorio.trocarCertificado(CHAVE, null, 5L);

        assertThat(repositorio.certificadosDoCnpj(CHAVE.cnpj())).containsExactlyInAnyOrder(
                new VinculoCertificado(CHAVE, 5L),
                new VinculoCertificado(homologacao, null));
    }

    private void definirCertificado(Long id) {
        jdbi.useHandle(h -> h.createUpdate("update estado_coleta set certificado_id = :id")
                .bind("id", id).execute());
    }

    private Long certificado() {
        return jdbi.withHandle(h -> h.createQuery("select certificado_id from estado_coleta")
                .mapTo(Long.class).one());
    }

    private String fotoSemCertificadoENem() {
        return jdbi.withHandle(h -> h.createQuery("""
                        select cursor_token || '|' || ult_nsu || '|' || max_nsu || '|' || versao || '|' || estado || '|'
                            || bloqueado_ate || '|' || due_at || '|' || lease_owner || '|' || lease_ate
                        from estado_coleta
                        """)
                .mapTo(String.class).one());
    }
}
