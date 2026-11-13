package br.com.doc.ingest.infra.postgres;

import org.jdbi.v3.core.Jdbi;
import org.postgresql.ds.PGSimpleDataSource;

import javax.sql.DataSource;

/**
 * Login sem superuser que herda de {@code fiscal_app}, para os testes do catálogo passarem pela RLS
 * (superuser a ignora). Mesmos atributos da V4 da E3-S03.
 */
public final class UsuarioAppCatalogo {

    private static final String LOGIN = "catalogo_jdbi_teste";

    private UsuarioAppCatalogo() {
    }

    /** Chamar <b>antes</b> de migrar: os grants da V9 são condicionais à existência de {@code fiscal_app}. */
    public static synchronized void garantirPapeis() {
        try (var c = PostgresTestBase.dataSource().getConnection(); var st = c.createStatement()) {
            st.execute("do $$ begin if not exists (select 1 from pg_roles where rolname = 'fiscal_app') then "
                    + "create role fiscal_app nologin nosuperuser nobypassrls; end if; end $$");
            st.execute("do $$ begin if not exists (select 1 from pg_roles where rolname = '" + LOGIN + "') then "
                    + "create role " + LOGIN + " login nosuperuser nobypassrls password '" + LOGIN + "' "
                    + "in role fiscal_app; end if; end $$");
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    /** {@link Jdbi} do login da aplicação no schema; {@code admin} é quem concede o acesso ao schema. */
    public static Jdbi jdbi(Jdbi admin, String schema) {
        admin.useHandle(h -> h.execute("grant usage on schema " + schema + " to " + LOGIN));
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(((PGSimpleDataSource) PostgresTestBase.dataSource()).getUrl());
        ds.setUser(LOGIN);
        ds.setPassword(LOGIN);
        ds.setCurrentSchema(schema);
        return Jdbi.create((DataSource) ds);
    }

    /**
     * Faz no schema o que a V4/V5 da E3-S03 farão com {@code empresa} (leitura pelo {@code fiscal_app} e RLS
     * forçada), enquanto elas não estão na base. Se a política da V5 já existir, não mexe.
     */
    public static void ligarRlsDeEmpresa(Jdbi admin, String schema) {
        admin.useHandle(h -> {
            boolean temV5 = h.createQuery("select count(*) > 0 from pg_policies "
                            + "where schemaname = :s and tablename = 'empresa' and policyname = 'empresa_isolamento'")
                    .bind("s", schema).mapTo(Boolean.class).one();
            if (!temV5) {
                h.execute("grant select on empresa to fiscal_app");
                h.execute("alter table empresa enable row level security");
                h.execute("alter table empresa force row level security");
                h.execute("create policy empresa_isolamento on empresa "
                        + "using (tenant_id = nullif(current_setting('app.tenant_id', true), '')::bigint)");
            }
        });
    }
}
