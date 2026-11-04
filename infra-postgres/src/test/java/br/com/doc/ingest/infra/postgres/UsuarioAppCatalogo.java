package br.com.doc.ingest.infra.postgres;

import org.jdbi.v3.core.Jdbi;
import org.postgresql.ds.PGSimpleDataSource;

import javax.sql.DataSource;

/**
 * Login sem superuser que herda de {@code fiscal_app}, para os testes do catálogo passarem pela RLS
 * (superuser a ignora). Mesmos atributos da V4 da E3-S03.
 */
final class UsuarioAppCatalogo {

    private static final String LOGIN = "catalogo_jdbi_teste";

    private UsuarioAppCatalogo() {
    }

    /** Chamar <b>antes</b> de migrar: os grants da V9 são condicionais à existência de {@code fiscal_app}. */
    static synchronized void garantirPapeis() {
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
    static Jdbi jdbi(Jdbi admin, String schema) {
        admin.useHandle(h -> h.execute("grant usage on schema " + schema + " to " + LOGIN));
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(((PGSimpleDataSource) PostgresTestBase.dataSource()).getUrl());
        ds.setUser(LOGIN);
        ds.setPassword(LOGIN);
        ds.setCurrentSchema(schema);
        return Jdbi.create((DataSource) ds);
    }
}
