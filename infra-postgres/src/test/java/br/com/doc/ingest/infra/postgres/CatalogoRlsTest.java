package br.com.doc.ingest.infra.postgres;

import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;

import javax.sql.DataSource;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Isolamento por tenant (ADR-013, §13) e privilégios do papel da aplicação nas tabelas do catálogo.
 * Semeia como superuser e verifica como usuário sem superuser (superuser ignora RLS e não provaria nada).
 */
class CatalogoRlsTest extends PostgresTestBase {

    private static final AtomicInteger CONTADOR = new AtomicInteger();
    private static final String LOGIN_APP = "catalogo_app_teste";
    private static final String CHAVE = "35260111222333000181550010000000011000000011";
    private static final String CHAVE_2 = "35260111222333000181550010000000021000000022";
    private static final List<String> TABELAS = List.of("documento", "arquivo", "documento_empresa", "proveniencia");

    private Jdbi admin;
    private Jdbi app;
    private Semente a;
    private Semente b;

    private record Semente(long tenant, long empresa, long documento, long arquivo) {
    }

    @BeforeEach
    void prepara() {
        String schema = "catalogo_rls_" + CONTADOR.incrementAndGet();
        garantePapeis();
        DataSource ds = dataSourceNoSchema(schema);
        Migracoes.aplicar(ds);
        admin = Jdbi.create(ds);
        admin.useHandle(h -> h.execute("grant usage on schema " + schema + " to " + LOGIN_APP));
        app = Jdbi.create(dataSourceComoApp(schema));
        a = semear("Escritório A", "11111111000101", "a".repeat(64));
        b = semear("Escritório B", "22222222000101", "b".repeat(64));
    }

    // Privado no teste para não conflitar com a E3-S03; mesmos atributos da V4 dela.
    private static synchronized void garantePapeis() {
        try (var c = dataSource().getConnection(); var st = c.createStatement()) {
            st.execute("do $$ begin if not exists (select 1 from pg_roles where rolname = 'fiscal_app') then "
                    + "create role fiscal_app nologin nosuperuser nobypassrls; end if; end $$");
            st.execute("do $$ begin if not exists (select 1 from pg_roles where rolname = '" + LOGIN_APP + "') then "
                    + "create role " + LOGIN_APP + " login nosuperuser nobypassrls password '" + LOGIN_APP + "' "
                    + "in role fiscal_app; end if; end $$");
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private static DataSource dataSourceComoApp(String schema) {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(((PGSimpleDataSource) dataSource()).getUrl());
        ds.setUser(LOGIN_APP);
        ds.setPassword(LOGIN_APP);
        ds.setCurrentSchema(schema);
        return ds;
    }

    private Semente semear(String nome, String cnpj, String sha) {
        return admin.inTransaction(h -> {
            long tenant = h.createUpdate("insert into tenant (nome) values (:n)").bind("n", nome)
                    .executeAndReturnGeneratedKeys("id").mapTo(Long.class).one();
            long empresa = h.createUpdate("insert into empresa (tenant_id, cnpj, raiz_cnpj, razao_social, tipo, ambiente) "
                            + "values (:t, :c, substr(:c, 1, 8), 'x', 'EMPRESA', 'PRODUCAO')")
                    .bind("t", tenant).bind("c", cnpj)
                    .executeAndReturnGeneratedKeys("id").mapTo(Long.class).one();
            long documento = inserirDocumento(h, tenant, CHAVE);
            long arquivo = inserirArquivo(h, tenant, documento, sha);
            inserirVinculo(h, tenant, documento, empresa, "SAIDA");
            inserirProveniencia(h, tenant, arquivo, 1);
            return new Semente(tenant, empresa, documento, arquivo);
        });
    }

    private static long inserirDocumento(Handle h, long tenant, String chave) {
        return h.createUpdate("insert into documento (tenant_id, chave_acesso, modelo, situacao, so_declarado, atualizado_em) "
                        + "values (:t, :c, '55', 'AUTORIZADA', false, now())")
                .bind("t", tenant).bind("c", chave)
                .executeAndReturnGeneratedKeys("id").mapTo(Long.class).one();
    }

    private static long inserirArquivo(Handle h, long tenant, long documento, String sha) {
        return h.createUpdate("insert into arquivo (tenant_id, documento_id, tipo, blob_ref, sha256, recebido_em) "
                        + "values (:t, :d, 'COMPLETO', 'doc/x.xml.gz', :s, now())")
                .bind("t", tenant).bind("d", documento).bind("s", sha)
                .executeAndReturnGeneratedKeys("id").mapTo(Long.class).one();
    }

    private static void inserirVinculo(Handle h, long tenant, long documento, long empresa, String direcao) {
        h.createUpdate("insert into documento_empresa (tenant_id, documento_id, empresa_id, direcao) "
                        + "values (:t, :d, :e, :dir)")
                .bind("t", tenant).bind("d", documento).bind("e", empresa).bind("dir", direcao).execute();
    }

    private static void inserirProveniencia(Handle h, long tenant, long arquivo, long recebimento) {
        h.createUpdate("insert into proveniencia (tenant_id, arquivo_id, origem, confianca, recebimento_id, recebido_em) "
                        + "values (:t, :a, 'UPLOAD', 'DECLARADO', :r, now())")
                .bind("t", tenant).bind("a", arquivo).bind("r", recebimento).execute();
    }

    private <T> T comoTenant(long tenant, Function<Handle, T> acao) {
        return app.inTransaction(h -> {
            h.createQuery("select set_config('app.tenant_id', :t, true)")
                    .bind("t", Long.toString(tenant)).mapTo(String.class).one();
            return acao.apply(h);
        });
    }

    private static List<Long> tenantsVistos(Handle h, String tabela) {
        return h.createQuery("select tenant_id from " + tabela).mapTo(Long.class).list();
    }

    private List<Long> tenantsVistosPor(long tenant, String tabela) {
        return comoTenant(tenant, h -> tenantsVistos(h, tabela));
    }

    private List<Long> tenantsVistosSemTenant(String tabela) {
        return app.withHandle(h -> tenantsVistos(h, tabela));
    }

    private List<Long> tenantsVistosComTenantVazio(String tabela) {
        return app.inTransaction(h -> {
            h.createQuery("select set_config('app.tenant_id', '', true)").mapTo(String.class).one();
            return tenantsVistos(h, tabela);
        });
    }

    private String situacaoVistaPor(long tenant) {
        return comoTenant(tenant, h -> h.createQuery("select situacao from documento").mapTo(String.class).one());
    }

    private int executaComoTenant(long tenant, String sql) {
        return comoTenant(tenant, h -> h.execute(sql));
    }

    private boolean ehPrivilegiado() {
        return app.withHandle(h -> h.createQuery(
                        "select rolsuper or rolbypassrls from pg_roles where rolname = current_user")
                .mapTo(Boolean.class).one());
    }

    private boolean papelTem(String tabela, String privilegio) {
        return admin.withHandle(h -> h.createQuery("select has_table_privilege('fiscal_app', :tabela, :privilegio)")
                .bind("tabela", tabela).bind("privilegio", privilegio).mapTo(Boolean.class).one());
    }

    @Test
    void usuarioDoTesteNaoEhSuperuser() {
        assertThat(ehPrivilegiado()).isFalse();
    }

    @Test
    void semTenantNaoVeNadaNasQuatroTabelas() {
        for (String tabela : TABELAS) {
            assertThat(tenantsVistosSemTenant(tabela)).as(tabela).isEmpty();
            assertThat(tenantsVistosComTenantVazio(tabela)).as(tabela + " com tenant vazio").isEmpty();
        }
    }

    @Test
    void tenantAVeSoOsSeus() {
        for (String tabela : TABELAS) {
            assertThat(tenantsVistosPor(a.tenant(), tabela)).as(tabela).containsExactly(a.tenant());
            assertThat(tenantsVistosPor(b.tenant(), tabela)).as(tabela).containsExactly(b.tenant());
        }
    }

    @Test
    void tenantAInsereNasSuas() {
        comoTenant(a.tenant(), h -> {
            long documento = inserirDocumento(h, a.tenant(), CHAVE_2);
            long arquivo = inserirArquivo(h, a.tenant(), documento, "c".repeat(64));
            inserirVinculo(h, a.tenant(), documento, a.empresa(), "ENTRADA");
            inserirProveniencia(h, a.tenant(), arquivo, 2);
            return null;
        });

        assertThat(tenantsVistosPor(a.tenant(), "proveniencia")).hasSize(2);
    }

    @Test
    void tenantANaoInsereEmB() {
        assertThatThrownBy(() -> comoTenant(a.tenant(), h -> inserirDocumento(h, b.tenant(), CHAVE_2)))
                .hasMessageContaining("row-level security");
        assertThatThrownBy(() -> comoTenant(a.tenant(), h -> inserirArquivo(h, b.tenant(), b.documento(), "c".repeat(64))))
                .hasMessageContaining("row-level security");
        assertThatThrownBy(() -> comoTenant(a.tenant(), h -> {
            inserirVinculo(h, b.tenant(), b.documento(), b.empresa(), "ENTRADA");
            return null;
        })).hasMessageContaining("row-level security");
        assertThatThrownBy(() -> comoTenant(a.tenant(), h -> {
            inserirProveniencia(h, b.tenant(), b.arquivo(), 2);
            return null;
        })).hasMessageContaining("row-level security");

        for (String tabela : TABELAS) {
            assertThat(tenantsVistosPor(b.tenant(), tabela)).as(tabela).hasSize(1);
        }
    }

    @Test
    void tenantANaoMoveDocumentoParaB() {
        assertThatThrownBy(() -> executaComoTenant(a.tenant(),
                "update documento set tenant_id = " + b.tenant() + ", chave_acesso = '" + CHAVE_2 + "'"))
                .hasMessageContaining("row-level security");

        assertThat(tenantsVistosPor(a.tenant(), "documento")).containsExactly(a.tenant());
    }

    @Test
    void tenantANaoAtualizaDocumentoDeB() {
        int alteradas = executaComoTenant(a.tenant(),
                "update documento set situacao = 'CANCELADA' where id = " + b.documento());

        assertThat(alteradas).isZero();
        assertThat(situacaoVistaPor(b.tenant())).isEqualTo("AUTORIZADA");
    }

    @Test
    void papelDaAplicacaoNaoTemUpdateDeleteNemTruncateEmProveniencia() {
        for (String privilegio : List.of("UPDATE", "DELETE", "TRUNCATE")) {
            assertThat(papelTem("proveniencia", privilegio)).as(privilegio).isFalse();
        }

        assertThatThrownBy(() -> executaComoTenant(a.tenant(), "update proveniencia set recebimento_id = 9"))
                .hasMessageContaining("permission denied");
        assertThatThrownBy(() -> executaComoTenant(a.tenant(), "delete from proveniencia"))
                .hasMessageContaining("permission denied");
        assertThatThrownBy(() -> executaComoTenant(a.tenant(), "truncate proveniencia"))
                .hasMessageContaining("permission denied");
    }

    @Test
    void papelDaAplicacaoSoAtualizaDocumento() {
        for (String tabela : TABELAS) {
            assertThat(papelTem(tabela, "SELECT")).as(tabela).isTrue();
            assertThat(papelTem(tabela, "INSERT")).as(tabela).isTrue();
            assertThat(papelTem(tabela, "UPDATE")).as(tabela).isEqualTo(tabela.equals("documento"));
            assertThat(papelTem(tabela, "DELETE")).as(tabela).isFalse();
            assertThat(papelTem(tabela, "TRUNCATE")).as(tabela).isFalse();
        }

        assertThat(executaComoTenant(a.tenant(), "update documento set situacao = 'CANCELADA'")).isEqualTo(1);
        assertThatThrownBy(() -> executaComoTenant(a.tenant(), "update arquivo set blob_ref = 'y'"))
                .hasMessageContaining("permission denied");
        assertThatThrownBy(() -> executaComoTenant(a.tenant(), "update documento_empresa set direcao = 'ENTRADA'"))
                .hasMessageContaining("permission denied");
    }
}
