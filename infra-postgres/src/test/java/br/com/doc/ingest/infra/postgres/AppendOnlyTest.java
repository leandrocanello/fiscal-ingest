package br.com.doc.ingest.infra.postgres;

import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Invariante 6: ledger append-only garantido pelo próprio banco. */
class AppendOnlyTest extends PostgresTestBase {

    private static final AtomicInteger CONTADOR = new AtomicInteger();
    private static final String CNPJ = "11222333000181";

    private Jdbi jdbi;

    @BeforeEach
    void prepara() {
        DataSource ds = dataSourceNoSchema("append_only_" + CONTADOR.incrementAndGet());
        Migracoes.aplicar(ds);
        jdbi = Jdbi.create(ds);
        jdbi.useHandle(h -> {
            h.execute("insert into ledger_tentativa (fonte, cnpj_consultado, ambiente, iniciada_em, desfecho) "
                    + "values ('f', ?, 'PRODUCAO', now(), 'Ok')", CNPJ);
            h.execute("insert into ledger_nsu (fonte, cnpj_consultado, ambiente, nsu, status, recebido_em) "
                    + "values ('f', ?, 'PRODUCAO', 1, 'BAIXADO', now())", CNPJ);
        });
    }

    private long contar(Handle h, String sql) {
        return h.createQuery(sql).mapTo(Long.class).one();
    }

    @Test
    void updateEDeleteEmLedgerSaoRecusadosPeloBanco() {
        jdbi.useHandle(h -> {
            assertThatThrownBy(() -> h.execute("update ledger_nsu set status = 'GAP'"))
                    .hasMessageContaining("append-only");
            assertThatThrownBy(() -> h.execute("delete from ledger_nsu"))
                    .hasMessageContaining("append-only");
            assertThatThrownBy(() -> h.execute("update ledger_tentativa set desfecho = 'Vazio'"))
                    .hasMessageContaining("append-only");
            assertThatThrownBy(() -> h.execute("delete from ledger_tentativa"))
                    .hasMessageContaining("append-only");

            assertThat(contar(h, "select count(*) from ledger_nsu where status = 'BAIXADO'")).isEqualTo(1);
            assertThat(contar(h, "select count(*) from ledger_tentativa where desfecho = 'Ok'")).isEqualTo(1);
        });
    }
}
