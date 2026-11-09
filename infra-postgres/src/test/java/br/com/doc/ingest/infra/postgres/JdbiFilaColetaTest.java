package br.com.doc.ingest.infra.postgres;

import br.com.doc.ingest.domain.adapter.Cursor;
import br.com.doc.ingest.domain.coleta.ChaveColeta;
import br.com.doc.ingest.domain.coleta.ColetaReivindicada;
import br.com.doc.ingest.domain.coleta.PedidoReivindicacao;
import br.com.doc.ingest.domain.coleta.PosicaoColeta;
import br.com.doc.ingest.domain.model.Ambiente;
import br.com.doc.ingest.domain.model.Cnpj;
import br.com.doc.ingest.domain.model.FonteId;
import br.com.doc.ingest.domain.model.Nsu;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** Claim com SKIP LOCKED e lease sobre estado_coleta (E4-S01). */
class JdbiFilaColetaTest extends PostgresTestBase {

    private static final AtomicInteger CONTADOR = new AtomicInteger();
    private static final Instant INICIO = Instant.parse("2026-01-01T12:00:00Z");
    private static final Duration LEASE = Duration.ofMinutes(5);
    private static final ChaveColeta CHAVE =
            new ChaveColeta(new FonteId("NFE_AN"), new Cnpj("11222333000181"), Ambiente.PRODUCAO);

    private Jdbi jdbi;
    private RelogioManual relogio;
    private JdbiFilaColeta fila;

    @BeforeEach
    void prepara() {
        var ds = dataSourceNoSchema("fila_coleta_" + CONTADOR.incrementAndGet());
        Migracoes.aplicar(ds);
        jdbi = Jdbi.create(ds);
        relogio = new RelogioManual(INICIO);
        fila = new JdbiFilaColeta(jdbi, relogio);
    }

    private void insere(String estado, Instant dueAt, Instant bloqueadoAte, String leaseOwner, Instant leaseAte) {
        jdbi.useHandle(h -> h.createUpdate("""
                        insert into estado_coleta (fonte, cnpj_consultado, ambiente, cursor_token, ult_nsu, versao,
                                                   estado, due_at, bloqueado_ate, lease_owner, lease_ate, atualizado_em)
                        values (:fonte, :cnpj, :ambiente, '0', 0, 0, :estado, :due, :bloq, :dono, :leaseAte, :agora)
                        """)
                .bind("fonte", CHAVE.fonte().id())
                .bind("cnpj", CHAVE.cnpj().valor())
                .bind("ambiente", CHAVE.ambiente().name())
                .bind("estado", estado)
                .bind("due", dueAt == null ? null : Timestamp.from(dueAt))
                .bind("bloq", bloqueadoAte == null ? null : Timestamp.from(bloqueadoAte))
                .bind("dono", leaseOwner)
                .bind("leaseAte", leaseAte == null ? null : Timestamp.from(leaseAte))
                .bind("agora", Timestamp.from(INICIO))
                .execute());
    }

    private List<ColetaReivindicada> reivindica(String dono) {
        return fila.reivindicar(new PedidoReivindicacao(dono, LEASE, 10));
    }

    @Test
    void reivindicaEstadoNovoSemDueAt() {
        insere("ATIVO", null, null, null, null);

        assertThat(reivindica("A")).hasSize(1);
    }

    @Test
    void reivindicaQuandoDueAtChegou() {
        insere("ATIVO", INICIO, null, null, null);

        assertThat(reivindica("A")).hasSize(1);
    }

    @Test
    void naoReivindicaAntesDoDueAt() {
        insere("ATIVO", INICIO.plusMillis(1), null, null, null);

        assertThat(reivindica("A")).isEmpty();
    }

    @Test
    void naoReivindicaEnquantoBloqueado() {
        insere("ATIVO", null, INICIO.plusMillis(1), null, null);

        assertThat(reivindica("A")).isEmpty();
    }

    @Test
    void reivindicaQuandoBloqueadoAteChegou() {
        insere("ATIVO", null, INICIO, null, null);

        assertThat(reivindica("A")).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"BLOQUEADO_ATE", "AGUARDANDO_CERTIFICADO", "CERT_VENCIDO", "PAUSADO_PELO_CLIENTE",
            "CONSENTIMENTO_REVOGADO", "ERRO_DEFINITIVO"})
    void soEstadoAtivoEhReivindicado(String estado) {
        insere(estado, null, null, null, null);

        assertThat(reivindica("A")).isEmpty();
    }

    @Test
    void naoReivindicaComLeaseVigente() {
        insere("ATIVO", null, null, "B", INICIO.plusMillis(1));

        assertThat(reivindica("A")).isEmpty();
    }

    @Test
    void reivindicaLeaseVencido() {
        insere("ATIVO", null, null, "B", INICIO);

        var r = reivindica("A");

        assertThat(r).hasSize(1);
        assertThat(r.get(0).dono()).isEqualTo("A");
    }

    @Test
    void gravaDonoELeaseEDevolveAPosicaoAtual() {
        insere("ATIVO", null, null, null, null);
        jdbi.useHandle(h -> h.execute("update estado_coleta set cursor_token = '42', ult_nsu = 42, versao = 3"));

        var r = reivindica("A");

        assertThat(r).containsExactly(new ColetaReivindicada(
                new PosicaoColeta(CHAVE, new Cursor("42"), new Nsu(42), 3), "A", INICIO.plus(LEASE)));
        String dono = jdbi.withHandle(h -> h.createQuery("select lease_owner from estado_coleta")
                .mapTo(String.class).one());
        Instant leaseAte = jdbi.withHandle(h -> h.createQuery("select lease_ate from estado_coleta")
                .mapTo(Timestamp.class).one().toInstant());
        assertThat(dono).isEqualTo("A");
        assertThat(leaseAte).isEqualTo(INICIO.plus(LEASE));
    }

    @Test
    void reivindicarNaoMudaVersaoNemCursor() {
        insere("ATIVO", null, null, null, null);
        jdbi.useHandle(h -> h.execute("update estado_coleta set cursor_token = '42', ult_nsu = 42, versao = 3"));

        reivindica("A");

        var linha = jdbi.withHandle(h -> h.createQuery("select cursor_token || '/' || ult_nsu || '/' || versao from estado_coleta")
                .mapTo(String.class).one());
        assertThat(linha).isEqualTo("42/42/3");
    }
}
