package br.com.doc.ingest.infra.postgres;

import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Invariantes 6 e 7: arquivo e proveniência não mudam nem somem, garantido pelo próprio banco. */
class CatalogoAppendOnlyTest extends PostgresTestBase {

    private static final AtomicInteger CONTADOR = new AtomicInteger();
    private static final String CHAVE = "35260111222333000181550010000000011000000011";

    private Jdbi jdbi;

    @BeforeEach
    void prepara() {
        DataSource ds = dataSourceNoSchema("catalogo_append_only_" + CONTADOR.incrementAndGet());
        Migracoes.aplicar(ds);
        jdbi = Jdbi.create(ds);
        jdbi.useHandle(h -> {
            long tenant = h.createUpdate("insert into tenant (nome) values ('t')")
                    .executeAndReturnGeneratedKeys("id").mapTo(Long.class).one();
            long documento = h.createUpdate("insert into documento "
                            + "(tenant_id, chave_acesso, modelo, situacao, so_declarado, atualizado_em) "
                            + "values (:t, :c, '55', 'AUTORIZADA', false, now())")
                    .bind("t", tenant).bind("c", CHAVE)
                    .executeAndReturnGeneratedKeys("id").mapTo(Long.class).one();
            long arquivo = h.createUpdate("insert into arquivo "
                            + "(tenant_id, documento_id, tipo, blob_ref, sha256, recebido_em) "
                            + "values (:t, :d, 'COMPLETO', 'doc/x.xml.gz', :s, now())")
                    .bind("t", tenant).bind("d", documento).bind("s", "a".repeat(64))
                    .executeAndReturnGeneratedKeys("id").mapTo(Long.class).one();
            h.createUpdate("insert into proveniencia "
                            + "(tenant_id, arquivo_id, origem, confianca, recebimento_id, recebido_em) "
                            + "values (:t, :a, 'UPLOAD', 'DECLARADO', 1, now())")
                    .bind("t", tenant).bind("a", arquivo).execute();
        });
    }

    private long conta(String tabela) {
        return jdbi.withHandle(h -> h.createQuery("select count(*) from " + tabela).mapTo(Long.class).one());
    }

    private String valor(String sql) {
        return jdbi.withHandle(h -> h.createQuery(sql).mapTo(String.class).one());
    }

    @Test
    void updateEDeleteEmProvenienciaSaoRecusados() {
        assertThatThrownBy(() -> jdbi.useHandle(h -> h.execute("update proveniencia set confianca = 'CONFIRMADO_SEFAZ'")))
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbi.useHandle(h -> h.execute("delete from proveniencia")))
                .hasMessageContaining("append-only");

        assertThat(conta("proveniencia")).isEqualTo(1);
        assertThat(valor("select confianca from proveniencia")).isEqualTo("DECLARADO");
    }

    @Test
    void updateEmArquivoEhRecusado() {
        assertThatThrownBy(() -> jdbi.useHandle(h -> h.execute("update arquivo set blob_ref = 'doc/outro.xml.gz'")))
                .hasMessageContaining("append-only");

        assertThat(valor("select blob_ref from arquivo")).isEqualTo("doc/x.xml.gz");
    }

    @Test
    void deleteEmArquivoEhRecusado() {
        assertThatThrownBy(() -> jdbi.useHandle(h -> h.execute("delete from arquivo")))
                .hasMessageContaining("append-only");

        assertThat(conta("arquivo")).isEqualTo(1);
    }
}
