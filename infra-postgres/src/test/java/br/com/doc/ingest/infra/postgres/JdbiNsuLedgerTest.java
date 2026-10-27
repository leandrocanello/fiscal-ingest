package br.com.doc.ingest.infra.postgres;

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

class JdbiNsuLedgerTest extends PostgresTestBase {

    private static final AtomicInteger CONTADOR = new AtomicInteger();
    private static final Clock RELOGIO = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    private static final FonteId FONTE = new FonteId("NFE_AN");
    private static final Cnpj CNPJ = new Cnpj("11222333000181");

    private Jdbi jdbi;
    private JdbiNsuLedger ledger;

    @BeforeEach
    void prepara() {
        var ds = dataSourceNoSchema("ledger_" + CONTADOR.incrementAndGet());
        Migracoes.aplicar(ds);
        jdbi = Jdbi.create(ds);
        ledger = new JdbiNsuLedger(jdbi, Ambiente.PRODUCAO, RELOGIO);
    }

    private static EntradaLedger e(long nsu, StatusNsu s) {
        return new EntradaLedger(new Nsu(nsu), s);
    }

    @Test
    void registrarEListarDevolveEmOrdemDeNsu() {
        ledger.registrar(FONTE, CNPJ, e(3, StatusNsu.EVENTO));
        ledger.registrar(FONTE, CNPJ, e(1, StatusNsu.BAIXADO));
        ledger.registrar(FONTE, CNPJ, e(2, StatusNsu.RESUMO));

        assertThat(ledger.listarEntradas(FONTE, CNPJ)).containsExactly(
                e(1, StatusNsu.BAIXADO), e(2, StatusNsu.RESUMO), e(3, StatusNsu.EVENTO));
    }

    @Test
    void listarFiltraPorFonteCnpjEAmbiente() {
        var outraFonte = new FonteId("CTE_AN");
        var outroCnpj = new Cnpj("99888777000166");
        var homologacao = new JdbiNsuLedger(jdbi, Ambiente.HOMOLOGACAO, RELOGIO);
        ledger.registrar(FONTE, CNPJ, e(1, StatusNsu.BAIXADO));
        ledger.registrar(outraFonte, CNPJ, e(2, StatusNsu.BAIXADO));
        ledger.registrar(FONTE, outroCnpj, e(3, StatusNsu.BAIXADO));
        homologacao.registrar(FONTE, CNPJ, e(4, StatusNsu.BAIXADO));

        assertThat(ledger.listarEntradas(FONTE, CNPJ)).containsExactly(e(1, StatusNsu.BAIXADO));
        assertThat(homologacao.listarEntradas(FONTE, CNPJ)).containsExactly(e(4, StatusNsu.BAIXADO));
    }

    @Test
    void reregistrarMesmoNsuNaoFalhaNemDuplica() {
        ledger.registrar(FONTE, CNPJ, e(1, StatusNsu.BAIXADO));
        ledger.registrar(FONTE, CNPJ, e(1, StatusNsu.BAIXADO));

        assertThat(ledger.listarEntradas(FONTE, CNPJ)).hasSize(1);
    }

    @Test
    void reregistrarComStatusDiferenteMantemOPrimeiro() {
        ledger.registrar(FONTE, CNPJ, e(1, StatusNsu.RESUMO));
        ledger.registrar(FONTE, CNPJ, e(1, StatusNsu.BAIXADO));

        assertThat(ledger.listarEntradas(FONTE, CNPJ)).isEqualTo(List.of(e(1, StatusNsu.RESUMO)));
    }
}
