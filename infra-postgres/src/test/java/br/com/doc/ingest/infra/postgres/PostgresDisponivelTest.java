package br.com.doc.ingest.infra.postgres;

import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PostgresDisponivelTest extends PostgresTestBase {

    @Test
    void sobeContainerEExecutaSelect1() {
        int resultado = Jdbi.create(dataSource())
                .withHandle(h -> h.createQuery("select 1").mapTo(Integer.class).one());

        assertThat(resultado).isEqualTo(1);
    }
}
