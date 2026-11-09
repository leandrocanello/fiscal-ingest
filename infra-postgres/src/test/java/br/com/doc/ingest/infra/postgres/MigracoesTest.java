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
    void criaAsTabelasDoCatalogo() {
        DataSource ds = dataSourceNoSchema("mig_catalogo");

        Migracoes.aplicar(ds);

        List<String> tabelas = Jdbi.create(ds).withHandle(h -> h.createQuery(
                        "select table_name from information_schema.tables where table_schema = 'mig_catalogo'")
                .mapTo(String.class).list());
        assertThat(tabelas).contains("documento", "arquivo", "documento_empresa", "proveniencia");
    }

        @Test
    void criaOutboxEConsumo() {
        DataSource ds = dataSourceNoSchema("mig_outbox");

        Migracoes.aplicar(ds);

        // Confere as tabelas, não a última versão: fixar a versão quebra a cada migration nova (V8, V9, V12...).
        java.util.List<String> tabelas = Jdbi.create(ds).withHandle(h -> h.createQuery(
                        "select table_name from information_schema.tables "
                                + "where table_schema = 'mig_outbox' and table_name in ('outbox', 'outbox_consumo') "
                                + "order by table_name")
                .mapTo(String.class).list());
        assertThat(tabelas).containsExactly("outbox", "outbox_consumo");
    }

    @Test
    void reaplicarEhNoOp() {
        DataSource ds = dataSourceNoSchema("mig_reaplica");

        Migracoes.aplicar(ds);
        int segunda = Migracoes.aplicar(ds);

        assertThat(segunda).isZero();
    }

    @Test
    void aplicaAteV4ECriaOPapelDaAplicacao() {
        DataSource ds = dataSourceNoSchema("mig_v4");

        Migracoes.aplicar(ds);

        Jdbi jdbi = Jdbi.create(ds);
        List<String> versoes = jdbi.withHandle(h -> h.createQuery(
                        "select version from flyway_schema_history where success order by installed_rank")
                .mapTo(String.class).list());
        long papeis = jdbi.withHandle(h -> h.createQuery(
                        "select count(*) from pg_roles where rolname = 'fiscal_app'")
                .mapTo(Long.class).one());
        assertThat(versoes).contains("4");
        assertThat(papeis).isEqualTo(1);
    }

    @Test
    void aplicarEmDoisSchemasNaoFalhaPorqueOPapelJaExiste() {
        DataSource primeiro = dataSourceNoSchema("mig_papel_a");
        DataSource segundo = dataSourceNoSchema("mig_papel_b");

        Migracoes.aplicar(primeiro);
        int aplicadas = Migracoes.aplicar(segundo);

        assertThat(aplicadas).isGreaterThanOrEqualTo(4);
    }
}
