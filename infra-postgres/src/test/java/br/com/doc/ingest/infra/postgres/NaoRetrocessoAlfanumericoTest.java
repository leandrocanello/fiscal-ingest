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
import br.com.doc.ingest.domain.model.FixturesFiscais;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Invariantes 1 e 2 com CNPJ alfanumérico (NT Conjunta 2025.001): a chave
 * {@code (fonte, cnpj_consultado, ambiente)} continua isolando estados, o CAS recusa retrocesso
 * e o banco barra o UPDATE que faria {@code ult_nsu} voltar.
 */
class NaoRetrocessoAlfanumericoTest extends PostgresTestBase {

    private static final AtomicInteger CONTADOR = new AtomicInteger();
    private static final Clock RELOGIO = Clock.fixed(Instant.parse("2026-07-06T00:00:00Z"), ZoneOffset.UTC);
    private static final FonteId FONTE = new FonteId("NFE_AN");
    private static final Cnpj ALFA = new Cnpj(FixturesFiscais.cnpj("12ABC34501DE"));
    // A e 6 colidem no DV: CNPJ válido e diferente, que não pode compartilhar estado com ALFA
    private static final Cnpj COLIDENTE = new Cnpj(FixturesFiscais.cnpj("126BC34501DE"));
    private static final ChaveColeta CHAVE = new ChaveColeta(FONTE, ALFA, Ambiente.PRODUCAO);

    private Jdbi jdbi;
    private JdbiEstadoColeta repositorio;

    @BeforeEach
    void prepara() {
        var ds = dataSourceNoSchema("nao_retrocesso_alfa_" + CONTADOR.incrementAndGet());
        Migracoes.aplicar(ds);
        jdbi = Jdbi.create(ds);
        repositorio = new JdbiEstadoColeta(jdbi, RELOGIO);
        repositorio.criarSeAusente(CHAVE);
        repositorio.confirmarLote(new ConfirmacaoLote(repositorio.carregar(CHAVE).orElseThrow(),
                new Cursor("5"), new Nsu(5), List.of(new EntradaLedger(new Nsu(5), StatusNsu.BAIXADO))));
    }

    @Test
    void casRecusaRetrocessoComCnpjAlfanumerico() {
        var lida = repositorio.carregar(CHAVE).orElseThrow();

        var resultado = repositorio.confirmarLote(new ConfirmacaoLote(lida, new Cursor("3"), new Nsu(3),
                List.of(new EntradaLedger(new Nsu(3), StatusNsu.BAIXADO))));

        assertThat(resultado).isEqualTo(new ResultadoCas.RetrocessoRecusado(new Nsu(5)));
        assertThat(repositorio.carregar(CHAVE)).contains(lida);
        assertThat(ledgerDe(ALFA)).containsExactly(new EntradaLedger(new Nsu(5), StatusNsu.BAIXADO));
    }

    @Test
    void bancoRecusaUpdateQueRetrocedeComCnpjAlfanumerico() {
        assertThatThrownBy(() -> jdbi.useHandle(h -> h.execute(
                "update estado_coleta set ult_nsu = 4 where cnpj_consultado = ?", ALFA.valor())))
                .hasMessageContaining("retrocesso de ult_nsu");

        assertThat(repositorio.carregar(CHAVE).orElseThrow().ultNsu()).isEqualTo(new Nsu(5));
    }

    @Test
    void segundaConfirmacaoNaMesmaVersaoPerdeOCas() {
        var lida = repositorio.carregar(CHAVE).orElseThrow();
        repositorio.confirmarLote(new ConfirmacaoLote(lida, new Cursor("7"), new Nsu(7), List.of()));

        var resultado = repositorio.confirmarLote(new ConfirmacaoLote(lida, new Cursor("9"), new Nsu(9), List.of()));

        assertThat(resultado).isEqualTo(new ResultadoCas.ConflitoDeVersao(lida.versao() + 1));
        assertThat(repositorio.carregar(CHAVE).orElseThrow().ultNsu()).isEqualTo(new Nsu(7));
    }

    @Test
    void cnpjsQueColidemNoDvSaoEstadosELedgersSeparados() {
        var chaveColidente = new ChaveColeta(FONTE, COLIDENTE, Ambiente.PRODUCAO);
        repositorio.criarSeAusente(chaveColidente);

        assertThat(repositorio.carregar(chaveColidente))
                .contains(new PosicaoColeta(chaveColidente, Cursor.inicial(), new Nsu(0), 0));
        assertThat(ledgerDe(COLIDENTE)).isEmpty();
        assertThat(repositorio.carregar(CHAVE).orElseThrow().ultNsu()).isEqualTo(new Nsu(5));
    }

    private List<EntradaLedger> ledgerDe(Cnpj cnpj) {
        return new JdbiNsuLedger(jdbi, Ambiente.PRODUCAO, RELOGIO).listarEntradas(FONTE, cnpj);
    }
}
