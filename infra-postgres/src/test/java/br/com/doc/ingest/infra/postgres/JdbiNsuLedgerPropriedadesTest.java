package br.com.doc.ingest.infra.postgres;

import br.com.doc.ingest.domain.ledger.EntradaLedger;
import br.com.doc.ingest.domain.ledger.GapCalculator;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Idempotência do ledger em Postgres (inv. 5 e 6, ADR-005): um item por NSU, status do primeiro registro. */
class JdbiNsuLedgerPropriedadesTest extends PostgresTestBase {

    private static final Clock RELOGIO = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    private static final FonteId FONTE = new FonteId("NFE_AN");
    private static final Cnpj CNPJ = new Cnpj("11222333000181");

    private static Jdbi jdbi;
    private static JdbiNsuLedger ledger;

    private static synchronized void garantePronto() {
        if (jdbi == null) {
            var ds = dataSourceNoSchema("ledger_propriedades");
            Migracoes.aplicar(ds);
            jdbi = Jdbi.create(ds);
            ledger = new JdbiNsuLedger(jdbi, Ambiente.PRODUCAO, RELOGIO);
        }
    }

    @BeforeTry
    void limpa() {
        garantePronto();
        jdbi.useHandle(h -> h.execute("truncate ledger_nsu cascade"));
    }

    @Provide
    Arbitrary<List<EntradaLedger>> registros() {
        return Combinators.combine(
                        Arbitraries.longs().between(1, 30),
                        Arbitraries.of(StatusNsu.class))
                .as((n, s) -> new EntradaLedger(new Nsu(n), s))
                .list().ofMaxSize(40);
    }

    @Property(tries = 25)
    void umItemPorNsuComStatusDoPrimeiroEmOrdemCrescente(@ForAll("registros") List<EntradaLedger> registros) {
        Map<Long, EntradaLedger> esperado = new LinkedHashMap<>();
        for (var r : registros) {
            esperado.putIfAbsent(r.nsu().valor(), r);
            ledger.registrar(FONTE, CNPJ, r);
        }
        var previsto = new ArrayList<>(esperado.values());
        previsto.sort((a, b) -> Long.compare(a.nsu().valor(), b.nsu().valor()));

        var lido = ledger.listarEntradas(FONTE, CNPJ);

        assertThat(lido).isEqualTo(previsto);
        assertThat(new GapCalculator().calcular(lido)).isEqualTo(new GapCalculator().calcular(previsto));
    }
}
