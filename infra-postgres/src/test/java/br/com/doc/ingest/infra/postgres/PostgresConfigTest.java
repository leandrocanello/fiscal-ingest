package br.com.doc.ingest.infra.postgres;

import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PostgresConfigTest extends PostgresTestBase {

    @Test
    void montaDataSourceAPartirDeUrlUsuarioSenha() {
        var base = dataSource();
        var pg = (org.postgresql.ds.PGSimpleDataSource) base;
        var config = new PostgresConfig(pg.getUrl(), pg.getUser(), pg.getPassword().toCharArray());

        Jdbi jdbi = FabricaJdbi.criar(config);

        int um = jdbi.withHandle(h -> h.createQuery("select 1").mapTo(Integer.class).one());
        assertThat(um).isEqualTo(1);
    }

    @Test
    void toStringNaoExpoeSenha() {
        var config = new PostgresConfig("jdbc:postgresql://h/db", "usr", "segredo-123".toCharArray());

        assertThat(config.toString()).doesNotContain("segredo-123").contains("usr");
    }
}
