package br.com.doc.ingest.infra.postgres;

import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Restrições da tabela {@code quarentena} (V10, §8, inv. 6). Roda como superuser, em schema próprio. */
class QuarentenaEsquemaTest extends PostgresTestBase {

    private static final AtomicInteger CONTADOR = new AtomicInteger();
    private static final String CHAVE = "35260111222333000181550010000000011000000011";

    private Jdbi jdbi;
    private long tenantA;
    private long tenantB;
    private long arquivoA;

    @BeforeEach
    void prepara() {
        UsuarioAppCatalogo.garantirPapeis();
        DataSource ds = dataSourceNoSchema("quarentena_esquema_" + CONTADOR.incrementAndGet());
        Migracoes.aplicar(ds);
        jdbi = Jdbi.create(ds);
        tenantA = jdbi.withHandle(QuarentenaEsquemaTest::novoTenant);
        tenantB = jdbi.withHandle(QuarentenaEsquemaTest::novoTenant);
        arquivoA = jdbi.withHandle(h -> novoArquivo(h, tenantA));
    }

    private static long novoTenant(Handle h) {
        return h.createUpdate("insert into tenant (nome) values ('t')")
                .executeAndReturnGeneratedKeys("id").mapTo(Long.class).one();
    }

    private static long novoArquivo(Handle h, long tenant) {
        long documento = h.createUpdate("insert into documento (tenant_id, chave_acesso, modelo, situacao, so_declarado, atualizado_em) "
                        + "values (:t, :c, '55', 'SO_RESUMO', false, now())")
                .bind("t", tenant).bind("c", CHAVE)
                .executeAndReturnGeneratedKeys("id").mapTo(Long.class).one();
        return h.createUpdate("insert into arquivo (tenant_id, documento_id, tipo, blob_ref, sha256, recebido_em) "
                        + "values (:t, :d, 'RESUMO', 'doc/x.xml.gz', :s, now())")
                .bind("t", tenant).bind("d", documento).bind("s", "a".repeat(64))
                .executeAndReturnGeneratedKeys("id").mapTo(Long.class).one();
    }

    private void abrir(long tenant, long arquivo, String motivo) {
        jdbi.useHandle(h -> h.createUpdate("insert into quarentena (tenant_id, arquivo_id, motivo, criado_em) "
                        + "values (:t, :a, :m, now())")
                .bind("t", tenant).bind("a", arquivo).bind("m", motivo).execute());
    }

    private void executar(String sql) {
        jdbi.useHandle(h -> h.execute(sql));
    }

    private boolean papelTemNaTabela(String privilegio) {
        return jdbi.withHandle(h -> h.createQuery("select has_table_privilege('fiscal_app', 'quarentena', :p)")
                .bind("p", privilegio).mapTo(Boolean.class).one());
    }

    private boolean papelTemNaColuna(String coluna, String privilegio) {
        return jdbi.withHandle(h -> h.createQuery("select has_column_privilege('fiscal_app', 'quarentena', :c, :p)")
                .bind("c", coluna).bind("p", privilegio).mapTo(Boolean.class).one());
    }

    @Test
    void motivoInvalidoEhRecusado() {
        assertThatThrownBy(() -> abrir(tenantA, arquivoA, "OUTRO")).hasMessageContaining("check constraint");
        assertThatCode(() -> abrir(tenantA, arquivoA, "SEM_EMPRESA")).doesNotThrowAnyException();
    }

    @Test
    void arquivoDeOutroTenantEhRecusado() {
        assertThatThrownBy(() -> abrir(tenantB, arquivoA, "SEM_EMPRESA")).hasMessageContaining("foreign key");
    }

    @Test
    void segundaQuarentenaAbertaDoMesmoArquivoEhRecusada() {
        abrir(tenantA, arquivoA, "SEM_EMPRESA");

        assertThatThrownBy(() -> abrir(tenantA, arquivoA, "SEM_EMPRESA")).hasMessageContaining("duplicate key");

        executar("update quarentena set resolvido_em = now()");
        assertThatCode(() -> abrir(tenantA, arquivoA, "SEM_EMPRESA")).doesNotThrowAnyException();
    }

    @Test
    void papelDaAplicacaoSoAtualizaResolvidoEm() {
        assertThat(papelTemNaTabela("SELECT")).isTrue();
        assertThat(papelTemNaTabela("INSERT")).isTrue();
        assertThat(papelTemNaTabela("UPDATE")).isFalse();
        assertThat(papelTemNaTabela("DELETE")).isFalse();
        assertThat(papelTemNaTabela("TRUNCATE")).isFalse();
        assertThat(papelTemNaColuna("resolvido_em", "UPDATE")).isTrue();
        for (String coluna : new String[] {"id", "tenant_id", "arquivo_id", "motivo", "criado_em"}) {
            assertThat(papelTemNaColuna(coluna, "UPDATE")).as(coluna).isFalse();
        }
    }

    @Test
    void resolvidaNaoReabreNemEhApagadaNemMudaOutraColuna() {
        abrir(tenantA, arquivoA, "SEM_EMPRESA");

        assertThatThrownBy(() -> executar("update quarentena set criado_em = now() - interval '1 day'"))
                .hasMessageContaining("quarentena");
        assertThatThrownBy(() -> executar("delete from quarentena")).hasMessageContaining("append-only");

        executar("update quarentena set resolvido_em = now()");
        assertThatThrownBy(() -> executar("update quarentena set resolvido_em = null"))
                .hasMessageContaining("quarentena");
        assertThatThrownBy(() -> executar("update quarentena set resolvido_em = now() + interval '1 day'"))
                .hasMessageContaining("quarentena");
    }
}
