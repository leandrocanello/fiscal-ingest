package br.com.doc.ingest.infra.postgres;

import br.com.doc.ingest.domain.adapter.Cursor;
import br.com.doc.ingest.domain.coleta.ChaveColeta;
import br.com.doc.ingest.domain.coleta.ConfirmacaoLote;
import br.com.doc.ingest.domain.coleta.ResultadoCas;
import br.com.doc.ingest.domain.ledger.EntradaLedger;
import br.com.doc.ingest.domain.ledger.StatusNsu;
import br.com.doc.ingest.domain.model.Ambiente;
import br.com.doc.ingest.domain.model.Cnpj;
import br.com.doc.ingest.domain.model.FonteId;
import br.com.doc.ingest.domain.model.Nsu;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    // ===== Privilégios mínimos (invariante 6 e coleta) =====

    private static final Clock RELOGIO = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    private static final ChaveColeta CHAVE =
            new ChaveColeta(new FonteId("NFE_AN"), new Cnpj("11222333000181"), Ambiente.PRODUCAO);

    /** Fecha o buraco do TRUNCATE (o trigger da V2 só cobre UPDATE/DELETE): o papel nem tem o privilégio. */
    @Test
    void ledgerRecusaUpdateDeleteETruncateParaOPapelApp() {
        Migracoes.aplicar(dataSourceNoSchema("papel_app_ledger"));
        Jdbi app = Jdbi.create(dataSourceComoApp("papel_app_ledger"));

        app.useHandle(h -> {
            h.execute("insert into ledger_tentativa (fonte, cnpj_consultado, ambiente, iniciada_em) "
                    + "values ('NFE_AN', '11222333000181', 'PRODUCAO', now())");
            h.execute("insert into ledger_nsu (fonte, cnpj_consultado, ambiente, nsu, status, recebido_em) "
                    + "values ('NFE_AN', '11222333000181', 'PRODUCAO', 1, 'RESUMO', now())");
        });
        long linhas = app.withHandle(h -> h.createQuery(
                "select (select count(*) from ledger_nsu) + (select count(*) from ledger_tentativa)")
                .mapTo(Long.class).one());
        assertThat(linhas).isEqualTo(2);

        for (String tabela : List.of("ledger_nsu", "ledger_tentativa")) {
            for (String sql : List.of(
                    "update " + tabela + " set fonte = 'X'",
                    "delete from " + tabela,
                    "truncate " + tabela + " cascade")) {
                assertThatThrownBy(() -> app.useHandle(h -> h.execute(sql)))
                        .as(sql)
                        .rootCause().hasMessageContaining("permission denied");
            }
        }
    }

    @Test
    void estadoColetaSemDeleteNemTruncate() {
        Migracoes.aplicar(dataSourceNoSchema("papel_app_estado"));
        Jdbi app = Jdbi.create(dataSourceComoApp("papel_app_estado"));

        for (String sql : List.of("delete from estado_coleta", "truncate estado_coleta")) {
            assertThatThrownBy(() -> app.useHandle(h -> h.execute(sql)))
                    .as(sql)
                    .rootCause().hasMessageContaining("permission denied");
        }
    }

    /** Os grants não podem quebrar o CAS nem o ledger (invariantes 1, 2 e 5). */
    @Test
    void confirmarLoteFuncionaComoPapelApp() {
        Migracoes.aplicar(dataSourceNoSchema("papel_app_cas"));
        Jdbi app = Jdbi.create(dataSourceComoApp("papel_app_cas"));
        var repositorio = new JdbiEstadoColeta(app, RELOGIO);
        var ledger = new JdbiNsuLedger(app, Ambiente.PRODUCAO, RELOGIO);

        repositorio.criarSeAusente(CHAVE);
        var lida = repositorio.carregar(CHAVE).orElseThrow();
        var entradas = List.of(
                new EntradaLedger(new Nsu(1), StatusNsu.RESUMO),
                new EntradaLedger(new Nsu(2), StatusNsu.BAIXADO));

        var resultado = repositorio.confirmarLote(new ConfirmacaoLote(lida, new Cursor("2"), new Nsu(2), entradas));

        assertThat(resultado).isEqualTo(new ResultadoCas.Aplicado(1));
        assertThat(ledger.listarEntradas(CHAVE.fonte(), CHAVE.cnpj())).containsExactlyElementsOf(entradas);
    }
}
