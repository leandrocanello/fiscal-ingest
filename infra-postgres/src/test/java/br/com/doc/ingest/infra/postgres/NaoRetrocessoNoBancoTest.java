package br.com.doc.ingest.infra.postgres;

import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Invariante 1: o banco recusa qualquer UPDATE que faça ult_nsu retroceder (defesa em profundidade do CAS). */
class NaoRetrocessoNoBancoTest extends PostgresTestBase {

    private static final AtomicInteger CONTADOR = new AtomicInteger();
    private static final String CNPJ = "11222333000181";

    private Jdbi jdbi;

    @BeforeEach
    void prepara() {
        DataSource ds = dataSourceNoSchema("nao_retrocesso_" + CONTADOR.incrementAndGet());
        Migracoes.aplicar(ds);
        jdbi = Jdbi.create(ds);
        jdbi.useHandle(h -> h.execute("insert into estado_coleta (fonte, cnpj_consultado, ambiente, cursor_token, ult_nsu) "
                + "values ('f', ?, 'PRODUCAO', '10', 10)", CNPJ));
    }

    private long ultNsu() {
        return jdbi.withHandle(h -> h.createQuery("select ult_nsu from estado_coleta").mapTo(Long.class).one());
    }

    @Test
    void updateQueDiminuiUltNsuEhRecusadoPeloBanco() {
        assertThatThrownBy(() -> jdbi.useHandle(h -> h.execute("update estado_coleta set ult_nsu = 9")))
                .hasMessageContaining("retrocesso de ult_nsu");

        assertThat(ultNsu()).isEqualTo(10);
    }

    @Test
    void updateQueMantemOuAumentaUltNsuPassa() {
        jdbi.useHandle(h -> h.execute("update estado_coleta set ult_nsu = 10, versao = versao + 1"));
        assertThat(ultNsu()).isEqualTo(10);

        jdbi.useHandle(h -> h.execute("update estado_coleta set ult_nsu = 11, versao = versao + 1"));
        assertThat(ultNsu()).isEqualTo(11);
    }

    @Test
    void colunasDoCursorNaoAceitamNulo() {
        assertThatThrownBy(() -> jdbi.useHandle(h -> h.execute("update estado_coleta set ult_nsu = null")))
                .hasMessageContaining("ult_nsu");
        assertThatThrownBy(() -> jdbi.useHandle(h -> h.execute("update estado_coleta set cursor_token = null")))
                .hasMessageContaining("cursor_token");

        jdbi.useHandle(h -> h.execute("insert into estado_coleta (fonte, cnpj_consultado, ambiente) "
                + "values ('g', ?, 'PRODUCAO')", CNPJ));
        var padrao = jdbi.withHandle(h -> h.createQuery(
                        "select cursor_token || '|' || ult_nsu from estado_coleta where fonte = 'g'")
                .mapTo(String.class).one());
        assertThat(padrao).isEqualTo("|0");
    }
}
