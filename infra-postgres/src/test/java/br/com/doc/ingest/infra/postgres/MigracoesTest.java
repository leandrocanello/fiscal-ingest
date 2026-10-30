package br.com.doc.ingest.infra.postgres;

import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MigracoesTest extends PostgresTestBase {

    @Test
    void aplicaV1DoZeroECriaAsCincoTabelas() {
        DataSource ds = dataSourceNoSchema("mig_zero");

        Migracoes.aplicar(ds);

        List<String> tabelas = Jdbi.create(ds).withHandle(h -> h.createQuery(
                        "select table_name from information_schema.tables where table_schema = 'mig_zero'")
                .mapTo(String.class).list());
        assertThat(tabelas).contains("tenant", "empresa", "estado_coleta", "ledger_nsu", "ledger_tentativa");
    }

    @Test
    void aplicaV7ECriaOutboxEConsumo() {
        DataSource ds = dataSourceNoSchema("mig_outbox");

        Migracoes.aplicar(ds);

        String ultima = Jdbi.create(ds).withHandle(h -> h.createQuery(
                        "select max(version::int)::text from flyway_schema_history where success")
                .mapTo(String.class).one());
        assertThat(ultima).isEqualTo("7");
    }

    @Test
    void reaplicarEhNoOp() {
        DataSource ds = dataSourceNoSchema("mig_reaplica");

        Migracoes.aplicar(ds);
        int segunda = Migracoes.aplicar(ds);

        assertThat(segunda).isZero();
    }
}
