package br.com.doc.ingest.infra.postgres;

import br.com.doc.ingest.domain.blob.BlobRef;
import br.com.doc.ingest.domain.catalogo.ArquivoFicha;
import br.com.doc.ingest.domain.catalogo.Catalogo;
import br.com.doc.ingest.domain.catalogo.Confianca;
import br.com.doc.ingest.domain.catalogo.Direcao;
import br.com.doc.ingest.domain.catalogo.FichaDocumento;
import br.com.doc.ingest.domain.catalogo.MetadadosDocumento;
import br.com.doc.ingest.domain.catalogo.OrigemArquivo;
import br.com.doc.ingest.domain.catalogo.ProvenienciaInformada;
import br.com.doc.ingest.domain.catalogo.RegistroArquivo;
import br.com.doc.ingest.domain.catalogo.ResultadoRegistro;
import br.com.doc.ingest.domain.catalogo.SituacaoDocumento;
import br.com.doc.ingest.domain.catalogo.TipoArquivo;
import br.com.doc.ingest.domain.catalogo.VinculoEmpresa;
import br.com.doc.ingest.domain.model.ChaveAcesso;
import br.com.doc.ingest.domain.model.Cnpj;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** {@code JdbiCatalogo} contra Postgres real, sempre como usuário sem superuser (a RLS vale). */
class JdbiCatalogoTest extends PostgresTestBase {

    private static final AtomicInteger CONTADOR = new AtomicInteger();
    private static final ChaveAcesso CHAVE = new ChaveAcesso("35260111222333000181550010000000011000000011");
    private static final Cnpj EMITENTE = new Cnpj("11222333000181");
    private static final String CPF_DESTINATARIO = "12345678909";
    private static final Instant AGORA = Instant.parse("2026-11-02T15:00:00Z");
    private static final Instant RECEBIDO_EM = Instant.parse("2026-11-02T12:00:00Z");
    private static final OffsetDateTime EMISSAO = OffsetDateTime.of(2026, 1, 15, 10, 0, 0, 0, ZoneOffset.ofHours(-3));
    private static final BlobRef BLOB = new BlobRef("doc/1/55/2026/01/chave/completo.xml.gz", "a".repeat(64));
    private static final MetadadosDocumento METADADOS = new MetadadosDocumento(
            Optional.of(1), Optional.of(123L), Optional.of(EMISSAO), Optional.of(EMITENTE),
            Optional.of(CPF_DESTINATARIO), Optional.of(new BigDecimal("1234.50")), Optional.of("4.00"));

    private Jdbi admin;
    private Catalogo catalogo;
    private long tenantA;
    private long tenantB;
    private long empresaA;
    private long empresaA2;
    private long ledgerNsu;

    @BeforeEach
    void prepara() {
        String schema = "jdbi_catalogo_" + CONTADOR.incrementAndGet();
        UsuarioAppCatalogo.garantirPapeis();
        DataSource ds = dataSourceNoSchema(schema);
        Migracoes.aplicar(ds);
        admin = Jdbi.create(ds);
        catalogo = new JdbiCatalogo(UsuarioAppCatalogo.jdbi(admin, schema), Clock.fixed(AGORA, ZoneOffset.UTC));
        tenantA = inserirTenant("Escritório A");
        tenantB = inserirTenant("Escritório B");
        empresaA = inserirEmpresa(tenantA, "11222333000181");
        empresaA2 = inserirEmpresa(tenantA, "11222333000262");
        ledgerNsu = inserirLedgerNsu();
    }

    private long inserirTenant(String nome) {
        return admin.withHandle(h -> h.createUpdate("insert into tenant (nome) values (:n)").bind("n", nome)
                .executeAndReturnGeneratedKeys("id").mapTo(Long.class).one());
    }

    private long inserirEmpresa(long tenant, String cnpj) {
        return admin.withHandle(h -> h.createUpdate("insert into empresa (tenant_id, cnpj, raiz_cnpj, razao_social, tipo, ambiente) "
                        + "values (:t, :c, substr(:c, 1, 8), 'x', 'EMPRESA', 'PRODUCAO')")
                .bind("t", tenant).bind("c", cnpj)
                .executeAndReturnGeneratedKeys("id").mapTo(Long.class).one());
    }

    private long inserirLedgerNsu() {
        return admin.withHandle(h -> h.createUpdate("insert into ledger_nsu (fonte, cnpj_consultado, ambiente, nsu, status, recebido_em) "
                        + "values ('nfe-an', '11222333000181', 'PRODUCAO', 1, 'BAIXADO', now())")
                .executeAndReturnGeneratedKeys("id").mapTo(Long.class).one());
    }

    private long contar(String tabela) {
        return admin.withHandle(h -> h.createQuery("select count(*) from " + tabela).mapTo(Long.class).one());
    }

    private ProvenienciaInformada oficial() {
        return new ProvenienciaInformada(
                OrigemArquivo.DFE_AN, Confianca.OFICIAL, Optional.of(ledgerNsu), Optional.empty(), RECEBIDO_EM);
    }

    private RegistroArquivo registro(long tenant, List<VinculoEmpresa> vinculos) {
        return new RegistroArquivo(tenant, CHAVE, METADADOS, SituacaoDocumento.AUTORIZADA, TipoArquivo.COMPLETO,
                Optional.empty(), Optional.empty(), BLOB, vinculos, oficial());
    }

    @Test
    void registrarNotaNovaCriaDocumentoArquivoVinculoEProveniencia() {
        var vinculo = new VinculoEmpresa(empresaA, Direcao.SAIDA);

        ResultadoRegistro resultado = catalogo.registrar(registro(tenantA, List.of(vinculo)));

        assertThat(contar("documento")).isEqualTo(1);
        assertThat(contar("arquivo")).isEqualTo(1);
        assertThat(contar("documento_empresa")).isEqualTo(1);
        assertThat(contar("proveniencia")).isEqualTo(1);
        assertThat(resultado.documentoId()).isPositive();
        assertThat(resultado.arquivoId()).isPositive();
        assertThat(resultado.documentoCriado()).isTrue();
        assertThat(resultado.documentoAlterado()).isFalse();
        assertThat(resultado.arquivoNovo()).isTrue();
        assertThat(resultado.provenienciaNova()).isTrue();
        assertThat(resultado.vinculosNovos()).containsExactly(vinculo);
    }

    @Test
    void consultarDevolveAFichaCompleta() {
        var vinculo = new VinculoEmpresa(empresaA, Direcao.SAIDA);
        ResultadoRegistro resultado = catalogo.registrar(registro(tenantA, List.of(vinculo)));

        FichaDocumento ficha = catalogo.consultar(tenantA, CHAVE).orElseThrow();

        assertThat(ficha.documentoId()).isEqualTo(resultado.documentoId());
        assertThat(ficha.tenantId()).isEqualTo(tenantA);
        assertThat(ficha.chave()).isEqualTo(CHAVE);
        assertThat(ficha.atualizadoEm()).isEqualTo(AGORA);
        assertThat(ficha.vinculos()).containsExactly(vinculo);

        assertThat(ficha.estado().situacao()).isEqualTo(SituacaoDocumento.AUTORIZADA);
        assertThat(ficha.estado().soDeclarado()).isFalse();
        var metadados = ficha.estado().metadados();
        assertThat(metadados.serie()).contains(1);
        assertThat(metadados.numero()).contains(123L);
        // timestamptz guarda o instante, não o fuso de origem
        assertThat(metadados.emissao().orElseThrow().toInstant()).isEqualTo(EMISSAO.toInstant());
        assertThat(metadados.emitenteCnpj()).contains(EMITENTE);
        assertThat(metadados.destinatarioDoc()).contains(CPF_DESTINATARIO);
        assertThat(metadados.valorTotal()).contains(new BigDecimal("1234.50"));
        assertThat(metadados.versaoLayout()).contains("4.00");

        assertThat(ficha.arquivos()).containsExactly(new ArquivoFicha(resultado.arquivoId(), TipoArquivo.COMPLETO,
                Optional.empty(), Optional.empty(), BLOB, RECEBIDO_EM, List.of(oficial())));
    }

    @Test
    void consultarDevolveCamposAusentesComoVazios() {
        var vazio = new MetadadosDocumento(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty());
        var evento = new RegistroArquivo(tenantA, CHAVE, vazio, SituacaoDocumento.SO_RESUMO, TipoArquivo.EVENTO,
                Optional.of("210210"), Optional.of(1), BLOB, List.of(), new ProvenienciaInformada(
                OrigemArquivo.UPLOAD, Confianca.DECLARADO, Optional.empty(), Optional.of(9L), RECEBIDO_EM));

        catalogo.registrar(evento);

        FichaDocumento ficha = catalogo.consultar(tenantA, CHAVE).orElseThrow();
        assertThat(ficha.estado().metadados()).isEqualTo(vazio);
        assertThat(ficha.estado().soDeclarado()).isTrue();
        ArquivoFicha arquivo = ficha.arquivos().getFirst();
        assertThat(arquivo.eventoTipo()).contains("210210");
        assertThat(arquivo.eventoSeq()).contains(1);
        assertThat(arquivo.proveniencias()).containsExactly(evento.proveniencia());
    }

    @Test
    void consultarChaveInexistenteDevolveVazio() {
        assertThat(catalogo.consultar(tenantA, CHAVE)).isEmpty();
    }

    @Test
    void tenantANaoConsultaDocumentoDeB() {
        catalogo.registrar(registro(tenantB, List.of()));

        assertThat(catalogo.consultar(tenantA, CHAVE)).isEmpty();
        assertThat(catalogo.consultar(tenantB, CHAVE)).isPresent();
    }

    @Test
    void documentoSemEmpresaFicaGuardadoSemVinculo() {
        ResultadoRegistro resultado = catalogo.registrar(registro(tenantA, List.of()));

        assertThat(contar("documento_empresa")).isZero();
        assertThat(resultado.vinculosNovos()).isEmpty();
        FichaDocumento ficha = catalogo.consultar(tenantA, CHAVE).orElseThrow();
        assertThat(ficha.vinculos()).isEmpty();
        assertThat(ficha.arquivos()).hasSize(1);
    }

    @Test
    void transferenciaEntreEmpresasDoTenantTemDoisVinculos() {
        var saida = new VinculoEmpresa(empresaA, Direcao.SAIDA);
        var entrada = new VinculoEmpresa(empresaA2, Direcao.ENTRADA);

        ResultadoRegistro resultado = catalogo.registrar(registro(tenantA, List.of(saida, entrada)));

        assertThat(contar("documento")).isEqualTo(1);
        assertThat(contar("documento_empresa")).isEqualTo(2);
        assertThat(resultado.vinculosNovos()).containsExactlyInAnyOrder(saida, entrada);
        assertThat(catalogo.consultar(tenantA, CHAVE).orElseThrow().vinculos())
                .containsExactlyInAnyOrder(saida, entrada);
    }
}
