package br.com.doc.ingest.infra.postgres;

import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EsquemaRestricoesTest extends PostgresTestBase {

    private static final AtomicInteger CONTADOR = new AtomicInteger();
    private static final String CNPJ = "11222333000181";

    private Jdbi jdbi;

    @BeforeEach
    void prepara() {
        DataSource ds = dataSourceNoSchema("restricoes_" + CONTADOR.incrementAndGet());
        Migracoes.aplicar(ds);
        jdbi = Jdbi.create(ds);
    }

    private long novoTenant(Handle h) {
        return h.createUpdate("insert into tenant (nome) values ('t')")
                .executeAndReturnGeneratedKeys("id").mapTo(Long.class).one();
    }

    private void novaEmpresa(Handle h, long tenant, String cnpj) {
        h.createUpdate("insert into empresa (tenant_id, cnpj, raiz_cnpj, razao_social, tipo, ambiente) "
                        + "values (:t, :c, substr(:c, 1, 8), 'x', 'EMPRESA', 'PRODUCAO')")
                .bind("t", tenant).bind("c", cnpj).execute();
    }

    private void novoLedger(Handle h, String ambiente, String status) {
        h.createUpdate("insert into ledger_nsu (fonte, cnpj_consultado, ambiente, nsu, status, recebido_em) "
                        + "values ('f', :c, :a, 1, :s, now())")
                .bind("c", CNPJ).bind("a", ambiente).bind("s", status).execute();
    }

    @Test
    void empresaDuplicadaNoMesmoTenantFalha() {
        jdbi.useHandle(h -> {
            long t = novoTenant(h);
            novaEmpresa(h, t, CNPJ);
            assertThatThrownBy(() -> novaEmpresa(h, t, CNPJ)).hasMessageContaining("empresa_tenant_id_cnpj_key");
        });
    }

    @Test
    void mesmoCnpjEmTenantsDiferentesEhPermitido() {
        jdbi.useHandle(h -> {
            novaEmpresa(h, novoTenant(h), CNPJ);
            novaEmpresa(h, novoTenant(h), CNPJ);
            assertThat(h.createQuery("select count(*) from empresa").mapTo(Long.class).one()).isEqualTo(2);
        });
    }

    @Test
    void estadoColetaRejeitaEstadoDesconhecido() {
        jdbi.useHandle(h -> assertThatThrownBy(() -> h.createUpdate(
                        "insert into estado_coleta (fonte, cnpj_consultado, ambiente, estado) "
                                + "values ('f', :c, 'PRODUCAO', 'INVENTADO')")
                .bind("c", CNPJ).execute()).hasMessageContaining("estado_coleta_estado_check"));
    }

    @Test
    void ledgerRejeitaStatusForaDeStatusNsu() {
        jdbi.useHandle(h -> assertThatThrownBy(() -> novoLedger(h, "PRODUCAO", "INVENTADO"))
                .hasMessageContaining("ledger_nsu_status_check"));
    }

    @Test
    void ambienteDiferenteEhChaveDiferente() {
        jdbi.useHandle(h -> {
            novoLedger(h, "PRODUCAO", "BAIXADO");
            novoLedger(h, "HOMOLOGACAO", "BAIXADO");
            assertThat(h.createQuery("select count(*) from ledger_nsu").mapTo(Long.class).one()).isEqualTo(2);
        });
    }
}
