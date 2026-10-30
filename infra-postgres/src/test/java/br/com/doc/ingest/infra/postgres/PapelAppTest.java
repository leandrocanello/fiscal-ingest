package br.com.doc.ingest.infra.postgres;

import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Papel da aplicação ({@code fiscal_app}): sem superuser e sem BYPASSRLS, senão o RLS por tenant
 * (ADR-013, §13) não valeria para ele. É um papel de grupo; logins herdam dele.
 */
class PapelAppTest extends PostgresTestBase {

    @Test
    void papelFiscalAppExisteSemSuperuserNemBypassRls() {
        DataSource ds = dataSourceNoSchema("papel_app");
        Migracoes.aplicar(ds);

        Map<String, Object> papel = Jdbi.create(ds).withHandle(h -> h.createQuery(
                        "select rolsuper, rolbypassrls, rolcanlogin from pg_roles where rolname = 'fiscal_app'")
                .mapToMap().one());

        assertThat(papel).containsEntry("rolsuper", false)
                .containsEntry("rolbypassrls", false)
                .containsEntry("rolcanlogin", false);
    }

    @Test
    void loginDeTesteHerdaDoPapelEConsegueUsarOSchema() {
        DataSource ds = dataSourceNoSchema("papel_app_login");
        Migracoes.aplicar(ds);

        long schemasVisiveis = Jdbi.create(dataSourceComoApp("papel_app_login")).withHandle(h -> h.createQuery(
                        "select count(*) from information_schema.schemata where schema_name = 'papel_app_login'")
                .mapTo(Long.class).one());
        String usuario = Jdbi.create(dataSourceComoApp("papel_app_login")).withHandle(h -> h.createQuery(
                "select current_user").mapTo(String.class).one());

        assertThat(usuario).isEqualTo("fiscal_app_teste");
        assertThat(schemasVisiveis).isEqualTo(1);
    }
}
