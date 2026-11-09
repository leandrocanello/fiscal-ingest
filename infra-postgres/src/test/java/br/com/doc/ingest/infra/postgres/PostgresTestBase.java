package br.com.doc.ingest.infra.postgres;

import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

import javax.sql.DataSource;

/** Base dos testes de integração: um único container Postgres compartilhado pela JVM de testes. */
abstract class PostgresTestBase {

    // Imagem fixada num único lugar (DA-11).
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    // Login só de teste que herda de fiscal_app (V4). Senha fixa, sem valor fora do container.
    private static final String USUARIO_APP = "fiscal_app_teste";
    private static final String SENHA_APP = "senha-so-de-teste";

    static {
        POSTGRES.start();
    }

    static DataSource dataSource() {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(POSTGRES.getJdbcUrl());
        ds.setUser(POSTGRES.getUsername());
        ds.setPassword(POSTGRES.getPassword());
        return ds;
    }

    /** DataSource apontando para um schema próprio (criado se faltar), isolando testes de migração. */
    static DataSource dataSourceNoSchema(String schema) {
        try (var c = dataSource().getConnection(); var st = c.createStatement()) {
            st.execute("create schema if not exists " + schema);
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException(e);
        }
        PGSimpleDataSource ds = (PGSimpleDataSource) dataSource();
        ds.setCurrentSchema(schema);
        return ds;
    }

    /**
     * DataSource como {@code fiscal_app_teste} (sem superuser, sem BYPASSRLS), no schema dado.
     * Exige que as migrations já tenham sido aplicadas nesse schema (o papel nasce na V4).
     */
    static DataSource dataSourceComoApp(String schema) {
        garantirUsuarioApp();
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(POSTGRES.getJdbcUrl());
        ds.setUser(USUARIO_APP);
        ds.setPassword(SENHA_APP);
        ds.setCurrentSchema(schema);
        return ds;
    }

    private static synchronized void garantirUsuarioApp() {
        try (var c = dataSource().getConnection(); var st = c.createStatement()) {
            st.execute("do $$ begin if not exists (select 1 from pg_roles where rolname = '" + USUARIO_APP + "') then "
                    + "create role " + USUARIO_APP + " login nosuperuser nobypassrls password '" + SENHA_APP + "' "
                    + "in role fiscal_app; end if; end $$");
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException(e);
        }
    }
}
