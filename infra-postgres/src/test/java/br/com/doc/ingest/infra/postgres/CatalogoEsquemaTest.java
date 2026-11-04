package br.com.doc.ingest.infra.postgres;

import br.com.doc.ingest.domain.catalogo.Confianca;
import br.com.doc.ingest.domain.catalogo.Direcao;
import br.com.doc.ingest.domain.catalogo.OrigemArquivo;
import br.com.doc.ingest.domain.catalogo.SituacaoDocumento;
import br.com.doc.ingest.domain.catalogo.TipoArquivo;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Restrições das tabelas do catálogo (inv. 8 e 9, DA-27). Roda como superuser, em schema próprio. */
class CatalogoEsquemaTest extends PostgresTestBase {

    private static final AtomicInteger CONTADOR = new AtomicInteger();
    private static final String CHAVE = "35260111222333000181550010000000011000000011";
    private static final String CHAVE_2 = "35260111222333000181550010000000021000000022";
    private static final String CNPJ = "11222333000181";
    private static final String SHA = "a".repeat(64);
    private static final String SHA_2 = "b".repeat(64);

    private Jdbi jdbi;

    @BeforeEach
    void prepara() {
        DataSource ds = dataSourceNoSchema("catalogo_esquema_" + CONTADOR.incrementAndGet());
        Migracoes.aplicar(ds);
        jdbi = Jdbi.create(ds);
    }

    private long novoTenant(Handle h) {
        return h.createUpdate("insert into tenant (nome) values ('t')")
                .executeAndReturnGeneratedKeys("id").mapTo(Long.class).one();
    }

    private long novaEmpresa(Handle h, long tenant) {
        return h.createUpdate("insert into empresa (tenant_id, cnpj, raiz_cnpj, razao_social, tipo, ambiente) "
                        + "values (:t, :c, substr(:c, 1, 8), 'x', 'EMPRESA', 'PRODUCAO')")
                .bind("t", tenant).bind("c", CNPJ)
                .executeAndReturnGeneratedKeys("id").mapTo(Long.class).one();
    }

    private long novoLedger(Handle h, long nsu) {
        return h.createUpdate("insert into ledger_nsu (fonte, cnpj_consultado, ambiente, nsu, status, recebido_em) "
                        + "values ('f', :c, 'PRODUCAO', :n, 'BAIXADO', now())")
                .bind("c", CNPJ).bind("n", nsu)
                .executeAndReturnGeneratedKeys("id").mapTo(Long.class).one();
    }

    private long novoDocumento(Handle h, long tenant, String chave) {
        return novoDocumento(h, tenant, chave, "AUTORIZADA");
    }

    private long novoDocumento(Handle h, long tenant, String chave, String situacao) {
        return h.createUpdate("insert into documento (tenant_id, chave_acesso, modelo, situacao, so_declarado, atualizado_em) "
                        + "values (:t, :c, '55', :s, false, now())")
                .bind("t", tenant).bind("c", chave).bind("s", situacao)
                .executeAndReturnGeneratedKeys("id").mapTo(Long.class).one();
    }

    private long novoArquivo(Handle h, long tenant, long documento, String sha) {
        return novoArquivo(h, tenant, documento, sha, "COMPLETO");
    }

    private long novoArquivo(Handle h, long tenant, long documento, String sha, String tipo) {
        return h.createUpdate("insert into arquivo (tenant_id, documento_id, tipo, blob_ref, sha256, recebido_em) "
                        + "values (:t, :d, :tipo, 'doc/x.xml.gz', :s, now())")
                .bind("t", tenant).bind("d", documento).bind("tipo", tipo).bind("s", sha)
                .executeAndReturnGeneratedKeys("id").mapTo(Long.class).one();
    }

    private void novoVinculo(Handle h, long tenant, long documento, long empresa, String direcao) {
        h.createUpdate("insert into documento_empresa (tenant_id, documento_id, empresa_id, direcao) "
                        + "values (:t, :d, :e, :dir)")
                .bind("t", tenant).bind("d", documento).bind("e", empresa).bind("dir", direcao).execute();
    }

    private void novaProveniencia(Handle h, long tenant, long arquivo, String origem, String confianca,
                                  Long ledger, Long recebimento) {
        h.createUpdate("insert into proveniencia "
                        + "(tenant_id, arquivo_id, origem, confianca, ledger_nsu_id, recebimento_id, recebido_em) "
                        + "values (:t, :a, :o, :c, :l, :r, now())")
                .bind("t", tenant).bind("a", arquivo).bind("o", origem).bind("c", confianca)
                .bind("l", ledger).bind("r", recebimento).execute();
    }

    private long conta(Handle h, String tabela) {
        return h.createQuery("select count(*) from " + tabela).mapTo(Long.class).one();
    }

    @Test
    void segundoDocumentoMesmoTenantEChaveEhRecusado() {
        jdbi.useHandle(h -> {
            long t = novoTenant(h);
            novoDocumento(h, t, CHAVE);
            assertThatThrownBy(() -> novoDocumento(h, t, CHAVE))
                    .hasMessageContaining("documento_tenant_id_chave_acesso_key");
        });
    }

    @Test
    void mesmaChaveEmTenantsDiferentesEhPermitida() {
        jdbi.useHandle(h -> {
            novoDocumento(h, novoTenant(h), CHAVE);
            novoDocumento(h, novoTenant(h), CHAVE);
            assertThat(conta(h, "documento")).isEqualTo(2);
        });
    }

    @Test
    void arquivoMesmoDocumentoEShaEhRecusado() {
        jdbi.useHandle(h -> {
            long t = novoTenant(h);
            long d = novoDocumento(h, t, CHAVE);
            novoArquivo(h, t, d, SHA);
            assertThatThrownBy(() -> novoArquivo(h, t, d, SHA))
                    .hasMessageContaining("arquivo_documento_id_sha256_key");
        });
    }

    @Test
    void mesmoShaEmDocumentosDiferentesEhPermitido() {
        jdbi.useHandle(h -> {
            long t = novoTenant(h);
            novoArquivo(h, t, novoDocumento(h, t, CHAVE), SHA);
            novoArquivo(h, t, novoDocumento(h, t, CHAVE_2), SHA);
            assertThat(conta(h, "arquivo")).isEqualTo(2);
        });
    }

    @Test
    void vinculoDuplicadoEhRecusado() {
        jdbi.useHandle(h -> {
            long t = novoTenant(h);
            long d = novoDocumento(h, t, CHAVE);
            long e = novaEmpresa(h, t);
            novoVinculo(h, t, d, e, "SAIDA");
            assertThatThrownBy(() -> novoVinculo(h, t, d, e, "SAIDA"))
                    .hasMessageContaining("documento_empresa_pkey");
        });
    }

    @Test
    void mesmaEmpresaNasDuasDirecoesEhPermitida() {
        jdbi.useHandle(h -> {
            long t = novoTenant(h);
            long d = novoDocumento(h, t, CHAVE);
            long e = novaEmpresa(h, t);
            novoVinculo(h, t, d, e, "SAIDA");
            novoVinculo(h, t, d, e, "ENTRADA");
            assertThat(conta(h, "documento_empresa")).isEqualTo(2);
        });
    }

    @Test
    void arquivoDeDocumentoDeOutroTenantEhRecusado() {
        jdbi.useHandle(h -> {
            long a = novoTenant(h);
            long b = novoTenant(h);
            long documentoDeB = novoDocumento(h, b, CHAVE);
            assertThatThrownBy(() -> novoArquivo(h, a, documentoDeB, SHA))
                    .hasMessageContaining("arquivo_tenant_id_documento_id_fkey");
        });
    }

    @Test
    void vinculoComEmpresaDeOutroTenantEhRecusado() {
        jdbi.useHandle(h -> {
            long a = novoTenant(h);
            long b = novoTenant(h);
            long documentoDeA = novoDocumento(h, a, CHAVE);
            long empresaDeB = novaEmpresa(h, b);
            assertThatThrownBy(() -> novoVinculo(h, a, documentoDeA, empresaDeB, "ENTRADA"))
                    .hasMessageContaining("documento_empresa_tenant_id_empresa_id_fkey");
        });
    }

    @Test
    void vinculoComDocumentoDeOutroTenantEhRecusado() {
        jdbi.useHandle(h -> {
            long a = novoTenant(h);
            long b = novoTenant(h);
            long documentoDeB = novoDocumento(h, b, CHAVE);
            long empresaDeA = novaEmpresa(h, a);
            assertThatThrownBy(() -> novoVinculo(h, a, documentoDeB, empresaDeA, "ENTRADA"))
                    .hasMessageContaining("documento_empresa_tenant_id_documento_id_fkey");
        });
    }

    @Test
    void provenienciaDeArquivoDeOutroTenantEhRecusada() {
        jdbi.useHandle(h -> {
            long a = novoTenant(h);
            long b = novoTenant(h);
            long arquivoDeB = novoArquivo(h, b, novoDocumento(h, b, CHAVE), SHA);
            assertThatThrownBy(() -> novaProveniencia(h, a, arquivoDeB, "UPLOAD", "DECLARADO", null, 1L))
                    .hasMessageContaining("proveniencia_tenant_id_arquivo_id_fkey");
        });
    }

    @Test
    void provenienciaOficialSoComDfeAn() {
        jdbi.useHandle(h -> {
            long t = novoTenant(h);
            long arq = novoArquivo(h, t, novoDocumento(h, t, CHAVE), SHA);
            long ledger = novoLedger(h, 1);
            assertThatThrownBy(() -> novaProveniencia(h, t, arq, "UPLOAD", "OFICIAL", null, 1L))
                    .hasMessageContaining("proveniencia_oficial_so_dfe_an");
            assertThatThrownBy(() -> novaProveniencia(h, t, arq, "DFE_AN", "DECLARADO", ledger, null))
                    .hasMessageContaining("proveniencia_oficial_so_dfe_an");
            novaProveniencia(h, t, arq, "DFE_AN", "OFICIAL", ledger, null);
            assertThat(conta(h, "proveniencia")).isEqualTo(1);
        });
    }

    @Test
    void provenienciaDfeAnExigeLedger() {
        jdbi.useHandle(h -> {
            long t = novoTenant(h);
            long arq = novoArquivo(h, t, novoDocumento(h, t, CHAVE), SHA);
            long ledger = novoLedger(h, 1);
            assertThatThrownBy(() -> novaProveniencia(h, t, arq, "DFE_AN", "OFICIAL", null, null))
                    .hasMessageContaining("proveniencia_dfe_an_exige_ledger");
            assertThatThrownBy(() -> novaProveniencia(h, t, arq, "DFE_AN", "OFICIAL", ledger, 1L))
                    .hasMessageContaining("proveniencia_dfe_an_exige_ledger");
        });
    }

    @Test
    void provenienciaApontaParaLinhaExistenteDoLedger() {
        jdbi.useHandle(h -> {
            long t = novoTenant(h);
            long arq = novoArquivo(h, t, novoDocumento(h, t, CHAVE), SHA);
            assertThatThrownBy(() -> novaProveniencia(h, t, arq, "DFE_AN", "OFICIAL", 999_999L, null))
                    .hasMessageContaining("proveniencia_ledger_nsu_id_fkey");
        });
    }

    @Test
    void provenienciaPushExigeRecebimento() {
        jdbi.useHandle(h -> {
            long t = novoTenant(h);
            long arq = novoArquivo(h, t, novoDocumento(h, t, CHAVE), SHA);
            long ledger = novoLedger(h, 1);
            for (String origem : new String[] {"AGENTE", "EMAIL", "UPLOAD"}) {
                assertThatThrownBy(() -> novaProveniencia(h, t, arq, origem, "VALIDADO_ASSINATURA", null, null))
                        .hasMessageContaining("proveniencia_push_exige_recebimento");
                assertThatThrownBy(() -> novaProveniencia(h, t, arq, origem, "VALIDADO_ASSINATURA", ledger, 1L))
                        .hasMessageContaining("proveniencia_push_exige_recebimento");
            }
        });
    }

    @Test
    void provenienciaRepetidaEhRecusada() {
        jdbi.useHandle(h -> {
            long t = novoTenant(h);
            long arq = novoArquivo(h, t, novoDocumento(h, t, CHAVE), SHA);
            long ledger = novoLedger(h, 1);
            novaProveniencia(h, t, arq, "DFE_AN", "OFICIAL", ledger, null);
            novaProveniencia(h, t, arq, "UPLOAD", "DECLARADO", null, 7L);

            // Os NULLs de recebimento_id e ledger_nsu_id contam como iguais.
            assertThatThrownBy(() -> novaProveniencia(h, t, arq, "DFE_AN", "OFICIAL", ledger, null))
                    .hasMessageContaining("proveniencia_sem_repeticao");
            assertThatThrownBy(() -> novaProveniencia(h, t, arq, "UPLOAD", "DECLARADO", null, 7L))
                    .hasMessageContaining("proveniencia_sem_repeticao");
        });
    }

    @Test
    void mesmoArquivoPodeGanharProvenienciaDeOutraOrigemConfiancaOuReferencia() {
        jdbi.useHandle(h -> {
            long t = novoTenant(h);
            long arq = novoArquivo(h, t, novoDocumento(h, t, CHAVE), SHA);
            novaProveniencia(h, t, arq, "DFE_AN", "OFICIAL", novoLedger(h, 1), null);
            novaProveniencia(h, t, arq, "DFE_AN", "OFICIAL", novoLedger(h, 2), null);
            novaProveniencia(h, t, arq, "UPLOAD", "VALIDADO_ASSINATURA", null, 7L);
            novaProveniencia(h, t, arq, "UPLOAD", "CONFIRMADO_SEFAZ", null, 7L);
            novaProveniencia(h, t, arq, "UPLOAD", "CONFIRMADO_SEFAZ", null, 8L);
            novaProveniencia(h, t, arq, "AGENTE", "CONFIRMADO_SEFAZ", null, 8L);
            assertThat(conta(h, "proveniencia")).isEqualTo(6);
        });
    }

    @Test
    void enumsInvalidosSaoRecusados() {
        jdbi.useHandle(h -> {
            long t = novoTenant(h);
            long d = novoDocumento(h, t, CHAVE);
            long e = novaEmpresa(h, t);
            long arq = novoArquivo(h, t, d, SHA);

            assertThatThrownBy(() -> novoDocumento(h, t, CHAVE_2, "INVENTADA"))
                    .hasMessageContaining("documento_situacao_check");
            assertThatThrownBy(() -> novoArquivo(h, t, d, SHA_2, "INVENTADO"))
                    .hasMessageContaining("arquivo_tipo_check");
            assertThatThrownBy(() -> novoVinculo(h, t, d, e, "INVENTADA"))
                    .hasMessageContaining("documento_empresa_direcao_check");
            assertThatThrownBy(() -> novaProveniencia(h, t, arq, "INVENTADA", "DECLARADO", null, 1L))
                    .hasMessageContaining("proveniencia_origem_check");
            assertThatThrownBy(() -> novaProveniencia(h, t, arq, "UPLOAD", "INVENTADA", null, 1L))
                    .hasMessageContaining("proveniencia_confianca_check");
        });
    }

    @Test
    void todosOsValoresDosEnumsDoDominioSaoAceitos() {
        jdbi.useHandle(h -> {
            long t = novoTenant(h);
            long d = novoDocumento(h, t, CHAVE);
            long e = novaEmpresa(h, t);
            int i = 0;
            for (var situacao : SituacaoDocumento.values()) {
                novoDocumento(h, t, "%044d".formatted(++i), situacao.name());
            }
            for (var tipo : TipoArquivo.values()) {
                novoArquivo(h, t, d, "%064x".formatted(++i), tipo.name());
            }
            for (var direcao : Direcao.values()) {
                novoVinculo(h, t, d, e, direcao.name());
            }
            long arq = novoArquivo(h, t, d, SHA);
            for (var confianca : Confianca.values()) {
                if (confianca != Confianca.OFICIAL) {
                    for (var origem : OrigemArquivo.values()) {
                        if (origem != OrigemArquivo.DFE_AN) {
                            assertThatCode(() -> novaProveniencia(h, t, arq, origem.name(), confianca.name(), null, 1L))
                                    .doesNotThrowAnyException();
                        }
                    }
                }
            }
        });
    }

    @Test
    void shaFormatoInvalidoEhRecusado() {
        jdbi.useHandle(h -> {
            long t = novoTenant(h);
            long d = novoDocumento(h, t, CHAVE);
            assertThatThrownBy(() -> novoArquivo(h, t, d, "A".repeat(64)))
                    .hasMessageContaining("arquivo_sha256_check");
            assertThatThrownBy(() -> novoArquivo(h, t, d, "abc"))
                    .hasMessageContaining("arquivo_sha256_check");
        });
    }
}
