package br.com.doc.ingest.infra.postgres;

import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Trava de esquema (ADR-013, §13): toda tabela de negócio com {@code tenant_id} nasce com RLS forçada e política.
 * Uma migration futura (catálogo, feed, outbox...) que esqueça a RLS quebra este teste, não o isolamento em produção.
 */
class TodaTabelaComTenantIdTemRlsTest extends PostgresTestBase {

    private static final String SCHEMA = "rls_meta";

    // Tabelas sem tenant_id, e por quê:
    // - tenant: é a própria raiz; a RLS dela usa a coluna id (V5).
    // - estado_coleta, ledger_nsu, ledger_tentativa: a coleta é por (fonte, cnpj_consultado, ambiente), e um CNPJ
    //   que serve a dois tenants é coletado uma vez só (§8.1, DA-24). O tenant entra no catálogo.
    // - flyway_schema_history: controle das migrations, fora do negócio.
    private static final Set<String> TABELAS_SEM_TENANT =
            Set.of("tenant", "estado_coleta", "ledger_nsu", "ledger_tentativa", "flyway_schema_history");

    private static Jdbi admin;

    @BeforeAll
    static void migrar() {
        var ds = dataSourceNoSchema(SCHEMA);
        Migracoes.aplicar(ds);
        admin = Jdbi.create(ds);
    }

    @Test
    void todaTabelaComColunaTenantIdTemRlsForcadaEPolitica() {
        List<String> comTenant = tabelasComTenantId();
        List<String> semRlsCompleta = admin.withHandle(h -> h.createQuery("""
                        select c.relname
                          from pg_class c
                          join pg_namespace n on n.oid = c.relnamespace
                         where n.nspname = :schema
                           and c.relname in (<tabelas>)
                           and not (c.relrowsecurity and c.relforcerowsecurity
                                    and exists (select 1 from pg_policies p
                                                 where p.schemaname = n.nspname and p.tablename = c.relname))
                         order by c.relname""")
                .bind("schema", SCHEMA)
                .bindList("tabelas", comTenant)
                .mapTo(String.class).list());

        assertThat(comTenant).isNotEmpty();
        assertThat(semRlsCompleta).as("tabelas com tenant_id sem RLS forçada e política").isEmpty();
    }

    @Test
    void tabelasSemTenantSaoSoAsDeclaradas() {
        List<String> todas = admin.withHandle(h -> h.createQuery("""
                        select table_name from information_schema.tables
                         where table_schema = :schema and table_type = 'BASE TABLE'""")
                .bind("schema", SCHEMA)
                .mapTo(String.class).list());
        List<String> comTenant = tabelasComTenantId();

        List<String> semTenant = todas.stream().filter(t -> !comTenant.contains(t)).toList();

        assertThat(semTenant).containsExactlyInAnyOrderElementsOf(TABELAS_SEM_TENANT);
    }

    private static List<String> tabelasComTenantId() {
        return admin.withHandle(h -> h.createQuery("""
                        select c.table_name
                          from information_schema.columns c
                          join information_schema.tables t
                            on t.table_schema = c.table_schema and t.table_name = c.table_name
                         where c.table_schema = :schema and c.column_name = 'tenant_id'
                           and t.table_type = 'BASE TABLE'
                         order by c.table_name""")
                .bind("schema", SCHEMA)
                .mapTo(String.class).list());
    }
}
