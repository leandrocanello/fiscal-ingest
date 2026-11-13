package br.com.doc.ingest.infra.postgres;

import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Esquema da outbox (V6): payload jsonb, idempotência e mutabilidade restrita a publicado_em. */
class OutboxEsquemaTest extends PostgresTestBase {

    private static final AtomicInteger CONTADOR = new AtomicInteger();
    private static final String INSERT = "insert into outbox (topico, chave, versao_schema, chave_idempotencia, "
            + "payload, criado_em) values ('docs.coletados', 'k', 1, ?, cast(? as jsonb), now())";

    private Jdbi jdbi;

    @BeforeEach
    void prepara() {
        DataSource ds = dataSourceNoSchema("outbox_" + CONTADOR.incrementAndGet());
        Migracoes.aplicar(ds);
        jdbi = Jdbi.create(ds);
    }

    private long contar(Handle h, String sql) {
        return h.createQuery(sql).mapTo(Long.class).one();
    }

    @Test
    void insereMensagemComPublicadoEmNulo() {
        jdbi.useHandle(h -> {
            h.execute(INSERT, "id-1", "{\"a\":1}");
            assertThat(contar(h, "select count(*) from outbox where publicado_em is null")).isEqualTo(1);
        });
    }

    @Test
    void chaveIdempotenciaRepetidaEhIgnoradaComOnConflict() {
        jdbi.useHandle(h -> {
            h.execute(INSERT, "id-1", "{}");
            int n = h.execute(INSERT + " on conflict (chave_idempotencia) do nothing", "id-1", "{}");
            assertThat(n).isZero();
            assertThat(contar(h, "select count(*) from outbox")).isEqualTo(1);
        });
    }

    @Test
    void payloadQueNaoEhJsonEhRecusadoPeloBanco() {
        jdbi.useHandle(h -> assertThatThrownBy(() -> h.execute(INSERT, "id-1", "<xml>"))
                .hasMessageContaining("json"));
    }

    @Test
    void soPublicadoEmPodeMudarEUmaVez() {
        jdbi.useHandle(h -> {
            h.execute(INSERT, "id-1", "{}");
            assertThatThrownBy(() -> h.execute("update outbox set payload = '{\"b\":2}'"))
                    .hasMessageContaining("outbox");
            assertThatThrownBy(() -> h.execute("update outbox set topico = 'x'"))
                    .hasMessageContaining("outbox");

            h.execute("update outbox set publicado_em = now()");
            assertThat(contar(h, "select count(*) from outbox where publicado_em is not null")).isEqualTo(1);

            assertThatThrownBy(() -> h.execute("update outbox set publicado_em = now() + interval '1 day'"))
                    .hasMessageContaining("outbox");
            assertThatThrownBy(() -> h.execute("update outbox set publicado_em = null"))
                    .hasMessageContaining("outbox");
        });
    }

    @Test
    void deleteEhRecusado() {
        jdbi.useHandle(h -> {
            h.execute(INSERT, "id-1", "{}");
            assertThatThrownBy(() -> h.execute("delete from outbox")).hasMessageContaining("outbox");
            assertThat(contar(h, "select count(*) from outbox")).isEqualTo(1);
        });
    }
}
