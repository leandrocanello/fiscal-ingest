package br.com.doc.ingest.infra.postgres;

import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Isolamento por tenant da {@code quarentena} (ADR-013, §13). Semeia como superuser e verifica como
 * usuário sem superuser (superuser ignora RLS).
 */
class QuarentenaRlsTest extends PostgresTestBase {

    private static final AtomicInteger CONTADOR = new AtomicInteger();
    private static final String CHAVE = "35260111222333000181550010000000011000000011";

    private Jdbi admin;
    private Jdbi app;
    private long tenantA;
    private long tenantB;

    @BeforeEach
    void prepara() {
        String schema = "quarentena_rls_" + CONTADOR.incrementAndGet();
        UsuarioAppCatalogo.garantirPapeis();
        DataSource ds = dataSourceNoSchema(schema);
        Migracoes.aplicar(ds);
        admin = Jdbi.create(ds);
        app = UsuarioAppCatalogo.jdbi(admin, schema);
        tenantA = semear("a");
        tenantB = semear("b");
    }

    private long semear(String sha) {
        return admin.inTransaction(h -> {
            long tenant = h.createUpdate("insert into tenant (nome) values ('t')")
                    .executeAndReturnGeneratedKeys("id").mapTo(Long.class).one();
            long documento = h.createUpdate("insert into documento (tenant_id, chave_acesso, modelo, situacao, so_declarado, atualizado_em) "
                            + "values (:t, :c, '55', 'SO_RESUMO', false, now())")
                    .bind("t", tenant).bind("c", CHAVE)
                    .executeAndReturnGeneratedKeys("id").mapTo(Long.class).one();
            long arquivo = h.createUpdate("insert into arquivo (tenant_id, documento_id, tipo, blob_ref, sha256, recebido_em) "
                            + "values (:t, :d, 'RESUMO', 'doc/x.xml.gz', :s, now())")
                    .bind("t", tenant).bind("d", documento).bind("s", sha.repeat(64))
                    .executeAndReturnGeneratedKeys("id").mapTo(Long.class).one();
            h.createUpdate("insert into quarentena (tenant_id, arquivo_id, motivo, criado_em) values (:t, :a, 'SEM_EMPRESA', now())")
                    .bind("t", tenant).bind("a", arquivo).execute();
            return tenant;
        });
    }

    private <T> T comoTenant(long tenant, Function<Handle, T> acao) {
        return app.inTransaction(h -> {
            h.createQuery("select set_config('app.tenant_id', :t, true)")
                    .bind("t", Long.toString(tenant)).mapTo(String.class).one();
            return acao.apply(h);
        });
    }

    private static List<Long> tenantsVistos(Handle h) {
        return h.createQuery("select tenant_id from quarentena").mapTo(Long.class).list();
    }

    private long abertasDe(long tenant) {
        return admin.withHandle(h -> h.createQuery(
                        "select count(*) from quarentena where tenant_id = :t and resolvido_em is null")
                .bind("t", tenant).mapTo(Long.class).one());
    }

    @Test
    void semTenantNaoVeNada() {
        List<Long> semTenant = app.withHandle(QuarentenaRlsTest::tenantsVistos);
        List<Long> comTenantVazio = app.inTransaction(h -> {
            h.createQuery("select set_config('app.tenant_id', '', true)").mapTo(String.class).one();
            return tenantsVistos(h);
        });

        assertThat(semTenant).isEmpty();
        assertThat(comTenantVazio).isEmpty();
    }

    @Test
    void tenantANaoVeNemResolveQuarentenaDeB() {
        assertThat(comoTenant(tenantA, QuarentenaRlsTest::tenantsVistos)).containsExactly(tenantA);

        int resolvidas = comoTenant(tenantA, h -> h.execute(
                "update quarentena set resolvido_em = now() where tenant_id = " + tenantB));

        assertThat(resolvidas).isZero();
        assertThat(abertasDe(tenantB)).isEqualTo(1);
    }
}
