package br.com.doc.ingest.infra.postgres;

import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V13 (E1-S07, NT Conjunta 2025.001): colunas de CNPJ e chave de acesso aceitam o formato alfanumérico
 * e recusam o que estiver fora dele. Só formato: o DV é conferido no domínio, não no banco.
 */
class CnpjAlfanumericoEsquemaTest extends PostgresTestBase {

    private static final AtomicInteger CONTADOR = new AtomicInteger();
    private static final String CNPJ = "12ABC34501DE35";
    private static final String CHAVE = "352604" + CNPJ + "55001000000001100000001" + "3";

    private Jdbi jdbi;

    @BeforeEach
    void prepara() {
        DataSource ds = dataSourceNoSchema("cnpj_alfa_" + CONTADOR.incrementAndGet());
        Migracoes.aplicar(ds);
        jdbi = Jdbi.create(ds);
    }

    private long novoTenant(Handle h) {
        return h.createUpdate("insert into tenant (nome) values ('t')")
                .executeAndReturnGeneratedKeys("id").mapTo(Long.class).one();
    }

    private void novaEmpresa(Handle h, long tenant, String cnpj, String raiz) {
        h.createUpdate("insert into empresa (tenant_id, cnpj, raiz_cnpj, razao_social, tipo, ambiente) "
                        + "values (:t, :c, :r, 'x', 'EMPRESA', 'PRODUCAO')")
                .bind("t", tenant).bind("c", cnpj).bind("r", raiz).execute();
    }

    private void novoEstado(Handle h, String cnpj) {
        h.createUpdate("insert into estado_coleta (fonte, cnpj_consultado, ambiente) values ('f', :c, 'PRODUCAO')")
                .bind("c", cnpj).execute();
    }

    private void novaTentativa(Handle h, String cnpj) {
        h.createUpdate("insert into ledger_tentativa (fonte, cnpj_consultado, ambiente, iniciada_em) "
                        + "values ('f', :c, 'PRODUCAO', now())")
                .bind("c", cnpj).execute();
    }

    private void novoLedger(Handle h, String cnpj, long nsu, String chave) {
        h.createUpdate("insert into ledger_nsu (fonte, cnpj_consultado, ambiente, nsu, status, chave_acesso, recebido_em) "
                        + "values ('f', :c, 'PRODUCAO', :n, 'BAIXADO', :k, now())")
                .bind("c", cnpj).bind("n", nsu).bind("k", chave).execute();
    }

    private void novoDocumento(Handle h, long tenant, String chave, String emitente, String destinatario) {
        h.createUpdate("insert into documento (tenant_id, chave_acesso, modelo, emitente_cnpj, destinatario_doc, "
                        + "situacao, so_declarado, atualizado_em) values (:t, :k, '55', :e, :d, 'AUTORIZADA', false, now())")
                .bind("t", tenant).bind("k", chave).bind("e", emitente).bind("d", destinatario).execute();
    }

    private long conta(Handle h, String tabela) {
        return h.createQuery("select count(*) from " + tabela).mapTo(Long.class).one();
    }

    @Test
    void colunasAceitamAlfanumerico() {
        jdbi.useHandle(h -> {
            long t = novoTenant(h);
            novaEmpresa(h, t, CNPJ, CNPJ.substring(0, 8));
            novoEstado(h, CNPJ);
            novaTentativa(h, CNPJ);
            novoLedger(h, CNPJ, 1, CHAVE);
            novoDocumento(h, t, CHAVE, CNPJ, CNPJ);
            novoDocumento(h, t, "3".repeat(44), "11222333000181", "12345678909");

            assertThat(conta(h, "empresa")).isEqualTo(1);
            assertThat(conta(h, "estado_coleta")).isEqualTo(1);
            assertThat(conta(h, "ledger_tentativa")).isEqualTo(1);
            assertThat(conta(h, "ledger_nsu")).isEqualTo(1);
            assertThat(conta(h, "documento")).isEqualTo(2);
        });
    }

    @Test
    void minusculaEhRecusada() {
        jdbi.useHandle(h -> {
            long t = novoTenant(h);
            String minusculo = CNPJ.toLowerCase();
            assertThatThrownBy(() -> novaEmpresa(h, t, minusculo, CNPJ.substring(0, 8)))
                    .hasMessageContaining("empresa_cnpj_formato");
            assertThatThrownBy(() -> novaEmpresa(h, t, CNPJ, minusculo.substring(0, 8)))
                    .hasMessageContaining("empresa_raiz_cnpj_formato");
            assertThatThrownBy(() -> novoDocumento(h, t, CHAVE.toLowerCase(), null, null))
                    .hasMessageContaining("documento_chave_acesso_check");
            assertThatThrownBy(() -> novoLedger(h, CNPJ, 1, CHAVE.toLowerCase()))
                    .hasMessageContaining("ledger_nsu_chave_acesso_formato");
        });
    }

    @Test
    void chaveComLetraForaDoLugarEhRecusada() {
        jdbi.useHandle(h -> {
            long t = novoTenant(h);
            String letraNaUf = "A" + CHAVE.substring(1);
            String letraNoModelo = CHAVE.substring(0, 20) + "5A" + CHAVE.substring(22);
            String letraNoDv = CHAVE.substring(0, 43) + "X";
            for (String chave : new String[] {letraNaUf, letraNoModelo, letraNoDv}) {
                assertThatThrownBy(() -> novoDocumento(h, t, chave, null, null))
                        .hasMessageContaining("documento_chave_acesso_check");
                assertThatThrownBy(() -> novoLedger(h, CNPJ, 1, chave))
                        .hasMessageContaining("ledger_nsu_chave_acesso_formato");
            }
        });
    }

    @Test
    void cnpjFormatoErradoEhRecusado() {
        jdbi.useHandle(h -> {
            long t = novoTenant(h);
            String letraNoDv = "12ABC34501DE3A";
            String simbolo = "12ABC-4501DE35";
            for (String cnpj : new String[] {letraNoDv, simbolo}) {
                assertThatThrownBy(() -> novaEmpresa(h, t, cnpj, CNPJ.substring(0, 8)))
                        .hasMessageContaining("empresa_cnpj_formato");
                assertThatThrownBy(() -> novoEstado(h, cnpj))
                        .hasMessageContaining("estado_coleta_cnpj_consultado_formato");
                assertThatThrownBy(() -> novaTentativa(h, cnpj))
                        .hasMessageContaining("ledger_tentativa_cnpj_consultado_formato");
                assertThatThrownBy(() -> novoLedger(h, cnpj, 1, null))
                        .hasMessageContaining("ledger_nsu_cnpj_consultado_formato");
                assertThatThrownBy(() -> novoDocumento(h, t, CHAVE, cnpj, null))
                        .hasMessageContaining("documento_emitente_cnpj_formato");
                assertThatThrownBy(() -> novoDocumento(h, t, CHAVE, null, cnpj))
                        .hasMessageContaining("documento_destinatario_doc_check");
            }
            assertThatThrownBy(() -> novaEmpresa(h, t, CNPJ, "12AB-345"))
                    .hasMessageContaining("empresa_raiz_cnpj_formato");
            assertThatThrownBy(() -> novaEmpresa(h, t, CNPJ, "12AB"))
                    .hasMessageContaining("empresa_raiz_cnpj_formato");
        });
    }

    @Test
    void ledgerAceitaChaveNula() {
        jdbi.useHandle(h -> {
            novoLedger(h, CNPJ, 1, null);
            assertThat(conta(h, "ledger_nsu")).isEqualTo(1);
        });
    }
}
