package br.com.doc.ingest.infra.postgres;

import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Row-Level Security por tenant (ADR-013, §13: "o tenant A não lê nada do B").
 * Os dados são semeados como superuser; toda leitura é como {@code fiscal_app_teste} (sem superuser e
 * sem BYPASSRLS) — superuser ignora RLS, então um assert feito com ele não provaria nada.
 */
class RlsTenantTest extends PostgresTestBase {

    private static final String SCHEMA = "rls_tenant";

    private static Jdbi admin;
    private static Jdbi app;

    private long tenantA;
    private long tenantB;
    private List<Long> empresasA;
    private List<Long> empresasB;

    @BeforeAll
    static void migrar() {
        var ds = dataSourceNoSchema(SCHEMA);
        Migracoes.aplicar(ds);
        admin = Jdbi.create(ds);
        app = Jdbi.create(dataSourceComoApp(SCHEMA));
    }

    @BeforeEach
    void semear() {
        admin.useHandle(h -> {
            h.execute("truncate empresa, tenant restart identity cascade");
            tenantA = inserirTenant(h, "Escritório A");
            tenantB = inserirTenant(h, "Escritório B");
            empresasA = List.of(
                    inserirEmpresa(h, tenantA, "11111111000101"),
                    inserirEmpresa(h, tenantA, "11111111000202"));
            empresasB = List.of(
                    inserirEmpresa(h, tenantB, "22222222000101"),
                    inserirEmpresa(h, tenantB, "22222222000202"),
                    inserirEmpresa(h, tenantB, "11111111000101")); // mesmo CNPJ de A, outro tenant
        });
    }

    @Test
    void semTenantNaSessaoNaoVeNenhumaEmpresaNemTenant() {
        long empresas = app.withHandle(h -> h.createQuery("select count(*) from empresa").mapTo(Long.class).one());
        long tenants = app.withHandle(h -> h.createQuery("select count(*) from tenant").mapTo(Long.class).one());

        assertThat(empresas).isZero();
        assertThat(tenants).isZero();
    }

    @Test
    void tenantAVeSoAsSuasEmpresas() {
        List<Long> ids = comoTenant(tenantA, h -> h.createQuery("select id from empresa order by id")
                .mapTo(Long.class).list());

        assertThat(ids).containsExactlyElementsOf(empresasA);
    }

    @Test
    void tenantANaoLeEmpresaDeBPorId() {
        List<Long> ids = comoTenant(tenantA, h -> h.createQuery("select id from empresa where id = :id")
                .bind("id", empresasB.get(0)).mapTo(Long.class).list());

        assertThat(ids).isEmpty();
    }

    @Test
    void tenantAVeSoASiMesmoNaTabelaTenant() {
        List<Long> ids = comoTenant(tenantA, h -> h.createQuery("select id from tenant").mapTo(Long.class).list());

        assertThat(ids).containsExactly(tenantA);
    }

    @Test
    void tenantIdVazioNaSessaoNaoQuebraEVeNada() {
        long empresas = app.inTransaction(h -> {
            h.createQuery("select set_config('app.tenant_id', '', true)").mapTo(String.class).one();
            return h.createQuery("select count(*) from empresa").mapTo(Long.class).one();
        });

        assertThat(empresas).isZero();
    }

    private static <R> R comoTenant(long tenantId, Function<Handle, R> leitura) {
        return app.inTransaction(h -> {
            h.createQuery("select set_config('app.tenant_id', :t, true)")
                    .bind("t", Long.toString(tenantId)).mapTo(String.class).one();
            return leitura.apply(h);
        });
    }

    private static long inserirTenant(Handle h, String nome) {
        return h.createQuery("insert into tenant (nome) values (:nome) returning id")
                .bind("nome", nome).mapTo(Long.class).one();
    }

    private static long inserirEmpresa(Handle h, long tenantId, String cnpj) {
        return h.createQuery("insert into empresa (tenant_id, cnpj, raiz_cnpj, razao_social, tipo, ambiente) "
                        + "values (:t, :cnpj, :raiz, 'Empresa sintética', 'EMPRESA', 'PRODUCAO') returning id")
                .bind("t", tenantId).bind("cnpj", cnpj).bind("raiz", cnpj.substring(0, 8))
                .mapTo(Long.class).one();
    }
}
