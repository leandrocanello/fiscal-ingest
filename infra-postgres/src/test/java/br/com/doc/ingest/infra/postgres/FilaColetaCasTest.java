package br.com.doc.ingest.infra.postgres;

import br.com.doc.ingest.domain.adapter.Cursor;
import br.com.doc.ingest.domain.coleta.ChaveColeta;
import br.com.doc.ingest.domain.coleta.ConfirmacaoLote;
import br.com.doc.ingest.domain.coleta.PedidoReivindicacao;
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

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** A posição devolvida pelo claim confirma no CAS da E3-S02; o lease é independente do cursor (DA-34). */
class FilaColetaCasTest extends PostgresTestBase {

    private static final AtomicInteger CONTADOR = new AtomicInteger();
    private static final Instant INICIO = Instant.parse("2026-01-01T12:00:00Z");
    private static final Duration LEASE = Duration.ofMinutes(5);
    private static final ChaveColeta CHAVE =
            new ChaveColeta(new FonteId("NFE_AN"), new Cnpj("11222333000181"), Ambiente.PRODUCAO);

    private Jdbi jdbi;
    private RelogioManual relogio;
    private JdbiFilaColeta fila;
    private JdbiEstadoColeta repositorio;

    @BeforeEach
    void prepara() {
        var ds = dataSourceNoSchema("fila_cas_" + CONTADOR.incrementAndGet());
        Migracoes.aplicar(ds);
        jdbi = Jdbi.create(ds);
        relogio = new RelogioManual(INICIO);
        fila = new JdbiFilaColeta(jdbi, relogio);
        repositorio = new JdbiEstadoColeta(jdbi, relogio);
        repositorio.criarSeAusente(CHAVE);
    }

    private PosicaoColeta reivindica(String dono) {
        var r = fila.reivindicar(new PedidoReivindicacao(dono, LEASE, 10));
        assertThat(r).hasSize(1);
        return r.get(0).posicao();
    }

    private static ConfirmacaoLote lote(PosicaoColeta p, long ate) {
        return new ConfirmacaoLote(p, new Cursor(Long.toString(ate)), new Nsu(ate),
                List.of(new EntradaLedger(new Nsu(ate), StatusNsu.BAIXADO)));
    }

    private Map<String, Object> lease() {
        return jdbi.withHandle(h -> h.createQuery(
                        "select lease_owner, lease_ate from estado_coleta where cnpj_consultado = :c")
                .bind("c", CHAVE.cnpj().valor()).mapToMap().one());
    }

    @Test
    void posicaoDoClaimConfirmaNoCas() {
        var posicao = reivindica("A");

        assertThat(repositorio.confirmarLote(lote(posicao, 3))).isEqualTo(new ResultadoCas.Aplicado(posicao.versao() + 1));
        assertThat(repositorio.carregar(CHAVE).orElseThrow().ultNsu()).isEqualTo(new Nsu(3));
    }

    @Test
    void confirmarNaoMexeNoLease() {
        var posicao = reivindica("A");
        var antes = lease();

        repositorio.confirmarLote(lote(posicao, 3));

        assertThat(lease()).isEqualTo(antes);
        assertThat(antes.get("lease_owner")).isEqualTo("A");
    }

    @Test
    void donoVencidoAindaConfirmaOLoteQueJaConsumiu() {
        var posicaoA = reivindica("A");
        relogio.avancar(LEASE);
        var posicaoB = reivindica("B");

        assertThat(repositorio.confirmarLote(lote(posicaoA, 5))).isEqualTo(new ResultadoCas.Aplicado(posicaoA.versao() + 1));
        assertThat(repositorio.confirmarLote(lote(posicaoB, 7))).isInstanceOf(ResultadoCas.ConflitoDeVersao.class);
        assertThat(repositorio.carregar(CHAVE).orElseThrow().ultNsu()).isEqualTo(new Nsu(5));
        assertThat(lease().get("lease_owner")).isEqualTo("B");
    }
}
