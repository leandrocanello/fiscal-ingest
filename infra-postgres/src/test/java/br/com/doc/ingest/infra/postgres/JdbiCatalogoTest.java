package br.com.doc.ingest.infra.postgres;

import br.com.doc.ingest.domain.blob.BlobRef;
import br.com.doc.ingest.domain.catalogo.ArquivoFicha;
import br.com.doc.ingest.domain.catalogo.Catalogo;
import br.com.doc.ingest.domain.catalogo.Confianca;
import br.com.doc.ingest.domain.catalogo.Direcao;
import br.com.doc.ingest.domain.catalogo.FalhaCatalogoException;
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
import java.io.PrintWriter;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/** {@code JdbiCatalogo} contra Postgres real, sempre como usuário sem superuser (a RLS vale). */
class JdbiCatalogoTest extends PostgresTestBase {

    private static final AtomicInteger CONTADOR = new AtomicInteger();
    private static final ChaveAcesso CHAVE = new ChaveAcesso("35260111222333000181550010000000011000000011");
    private static final Cnpj EMITENTE = new Cnpj("11222333000181");
    private static final String CPF_DESTINATARIO = "12345678909";
    private static final Instant AGORA = Instant.parse("2026-11-02T15:00:00Z");
    private static final Instant DEPOIS = AGORA.plusSeconds(3600);
    private static final Instant RECEBIDO_EM = Instant.parse("2026-11-02T12:00:00Z");
    private static final OffsetDateTime EMISSAO = OffsetDateTime.of(2026, 1, 15, 10, 0, 0, 0, ZoneOffset.ofHours(-3));
    private static final BlobRef BLOB = new BlobRef("doc/1/55/2026/01/chave/completo.xml.gz", "a".repeat(64));
    private static final BlobRef BLOB_RESUMO = new BlobRef("doc/1/55/2026/01/chave/resumo.xml.gz", "b".repeat(64));
    private static final BlobRef BLOB_EVENTO = new BlobRef("doc/1/55/2026/01/chave/evento.xml.gz", "c".repeat(64));
    private static final long RECEBIMENTO = 9L;
    private static final MetadadosDocumento METADADOS = new MetadadosDocumento(
            Optional.of(1), Optional.of(123L), Optional.of(EMISSAO), Optional.of(EMITENTE),
            Optional.of(CPF_DESTINATARIO), Optional.of(new BigDecimal("1234.50")), Optional.of("4.00"));
    private static final MetadadosDocumento VAZIO = new MetadadosDocumento(Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());

    private Jdbi admin;
    private Catalogo catalogo;
    private Catalogo catalogoDepois;
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
        Jdbi app = UsuarioAppCatalogo.jdbi(admin, schema);
        catalogo = new JdbiCatalogo(app, Clock.fixed(AGORA, ZoneOffset.UTC));
        catalogoDepois = new JdbiCatalogo(app, Clock.fixed(DEPOIS, ZoneOffset.UTC));
        tenantA = inserirTenant("Escritório A");
        tenantB = inserirTenant("Escritório B");
        empresaA = inserirEmpresa(tenantA, "11222333000181");
        empresaA2 = inserirEmpresa(tenantA, "11222333000262");
        ledgerNsu = inserirLedgerNsu("11222333000181");
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

    private long inserirLedgerNsu(String cnpjConsultado) {
        return admin.withHandle(h -> h.createUpdate("insert into ledger_nsu (fonte, cnpj_consultado, ambiente, nsu, status, recebido_em) "
                        + "values ('nfe-an', :c, 'PRODUCAO', 1, 'BAIXADO', now())")
                .bind("c", cnpjConsultado)
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

    private ProvenienciaInformada oficialDoLedger(long ledger) {
        return new ProvenienciaInformada(
                OrigemArquivo.DFE_AN, Confianca.OFICIAL, Optional.of(ledger), Optional.empty(), RECEBIDO_EM);
    }

    private static ProvenienciaInformada upload(Confianca confianca) {
        return new ProvenienciaInformada(
                OrigemArquivo.UPLOAD, confianca, Optional.empty(), Optional.of(RECEBIMENTO), RECEBIDO_EM);
    }

    private RegistroArquivo completoDeA(ProvenienciaInformada proveniencia) {
        return new RegistroArquivo(tenantA, CHAVE, METADADOS, SituacaoDocumento.AUTORIZADA, TipoArquivo.COMPLETO,
                Optional.empty(), Optional.empty(), BLOB, List.of(), proveniencia);
    }

    private RegistroArquivo eventoDeA(SituacaoDocumento situacao) {
        return new RegistroArquivo(tenantA, CHAVE, VAZIO, situacao, TipoArquivo.EVENTO, Optional.of("110111"),
                Optional.of(1), BLOB_EVENTO, List.of(), oficial());
    }

    private FichaDocumento fichaDeA() {
        return catalogo.consultar(tenantA, CHAVE).orElseThrow();
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

    @Test
    void reRegistrarEhNoOp() {
        var vinculo = new VinculoEmpresa(empresaA, Direcao.SAIDA);
        ResultadoRegistro primeiro = catalogo.registrar(registro(tenantA, List.of(vinculo)));

        ResultadoRegistro segundo = catalogoDepois.registrar(registro(tenantA, List.of(vinculo)));

        assertThat(contar("documento")).isEqualTo(1);
        assertThat(contar("arquivo")).isEqualTo(1);
        assertThat(contar("documento_empresa")).isEqualTo(1);
        assertThat(contar("proveniencia")).isEqualTo(1);
        assertThat(segundo).isEqualTo(new ResultadoRegistro(
                primeiro.documentoId(), primeiro.arquivoId(), false, false, false, false, List.of()));
        assertThat(fichaDeA().atualizadoEm()).isEqualTo(AGORA);
    }

    @Test
    void mesmaNotaPorDoisCanaisViraUmDocumentoComDuasProveniencias() {
        ResultadoRegistro an = catalogo.registrar(completoDeA(oficial()));

        ResultadoRegistro push = catalogoDepois.registrar(completoDeA(upload(Confianca.VALIDADO_ASSINATURA)));

        assertThat(contar("documento")).isEqualTo(1);
        assertThat(contar("arquivo")).isEqualTo(1);
        assertThat(push.documentoId()).isEqualTo(an.documentoId());
        assertThat(push.arquivoId()).isEqualTo(an.arquivoId());
        assertThat(push.documentoCriado()).isFalse();
        assertThat(push.arquivoNovo()).isFalse();
        assertThat(push.provenienciaNova()).isTrue();
        assertThat(fichaDeA().arquivos().getFirst().proveniencias())
                .containsExactlyInAnyOrder(oficial(), upload(Confianca.VALIDADO_ASSINATURA));
    }

    @Test
    void mesmaNotaPorDoisCnpjsConsultadosTemDuasProveniencias() {
        long ledgerEscritorio = inserirLedgerNsu("99888777000100");
        catalogo.registrar(completoDeA(oficial()));

        ResultadoRegistro segundo = catalogo.registrar(completoDeA(oficialDoLedger(ledgerEscritorio)));

        assertThat(contar("documento")).isEqualTo(1);
        assertThat(contar("arquivo")).isEqualTo(1);
        assertThat(segundo.provenienciaNova()).isTrue();
        assertThat(fichaDeA().arquivos().getFirst().proveniencias())
                .containsExactlyInAnyOrder(oficial(), oficialDoLedger(ledgerEscritorio));
    }

    @Test
    void resumoDepoisCompletoViraDoisArquivosNoMesmoDocumento() {
        var resumo = new RegistroArquivo(tenantA, CHAVE, METADADOS, SituacaoDocumento.SO_RESUMO, TipoArquivo.RESUMO,
                Optional.empty(), Optional.empty(), BLOB_RESUMO, List.of(), oficial());
        ResultadoRegistro primeiro = catalogo.registrar(resumo);

        ResultadoRegistro segundo = catalogoDepois.registrar(completoDeA(oficial()));

        assertThat(contar("documento")).isEqualTo(1);
        assertThat(contar("arquivo")).isEqualTo(2);
        assertThat(segundo.documentoId()).isEqualTo(primeiro.documentoId());
        assertThat(segundo.arquivoId()).isNotEqualTo(primeiro.arquivoId());
        assertThat(segundo.arquivoNovo()).isTrue();
        assertThat(segundo.documentoAlterado()).isTrue();
        FichaDocumento ficha = fichaDeA();
        assertThat(ficha.estado().situacao()).isEqualTo(SituacaoDocumento.AUTORIZADA);
        assertThat(ficha.atualizadoEm()).isEqualTo(DEPOIS);
        assertThat(ficha.arquivos()).extracting(ArquivoFicha::tipo)
                .containsExactlyInAnyOrder(TipoArquivo.RESUMO, TipoArquivo.COMPLETO);
    }

    @Test
    void mesmoShaMantemPrimeiroBlobRef() {
        catalogo.registrar(completoDeA(oficial()));
        var outraChave = new BlobRef("raw/push/1/2026/11/02/" + BLOB.sha256() + ".bin", BLOB.sha256());
        var mesmoSha = new RegistroArquivo(tenantA, CHAVE, METADADOS, SituacaoDocumento.AUTORIZADA, TipoArquivo.COMPLETO,
                Optional.empty(), Optional.empty(), outraChave, List.of(), upload(Confianca.VALIDADO_ASSINATURA));

        ResultadoRegistro segundo = catalogo.registrar(mesmoSha);

        assertThat(segundo.arquivoNovo()).isFalse();
        assertThat(contar("arquivo")).isEqualTo(1);
        assertThat(fichaDeA().arquivos().getFirst().blob()).isEqualTo(BLOB);
    }

    @Test
    void eventoAntesDoXmlCriaDocumentoEOXmlPreencheDepois() {
        ResultadoRegistro evento = catalogo.registrar(eventoDeA(SituacaoDocumento.SO_RESUMO));
        assertThat(evento.documentoCriado()).isTrue();
        assertThat(fichaDeA().estado().metadados()).isEqualTo(VAZIO);

        ResultadoRegistro xml = catalogoDepois.registrar(completoDeA(oficial()));

        assertThat(xml.documentoId()).isEqualTo(evento.documentoId());
        assertThat(xml.documentoCriado()).isFalse();
        assertThat(xml.documentoAlterado()).isTrue();
        FichaDocumento ficha = fichaDeA();
        assertThat(ficha.estado().situacao()).isEqualTo(SituacaoDocumento.AUTORIZADA);
        var metadados = ficha.estado().metadados();
        assertThat(metadados.serie()).contains(1);
        assertThat(metadados.numero()).contains(123L);
        assertThat(metadados.emissao().orElseThrow().toInstant()).isEqualTo(EMISSAO.toInstant());
        assertThat(metadados.emitenteCnpj()).contains(EMITENTE);
        assertThat(metadados.destinatarioDoc()).contains(CPF_DESTINATARIO);
        assertThat(metadados.valorTotal()).contains(new BigDecimal("1234.50"));
        assertThat(metadados.versaoLayout()).contains("4.00");
        assertThat(ficha.atualizadoEm()).isEqualTo(DEPOIS);
        assertThat(ficha.arquivos()).hasSize(2);
    }

    @Test
    void situacaoNaoRegrideComXmlTardio() {
        catalogo.registrar(eventoDeA(SituacaoDocumento.CANCELADA));

        catalogoDepois.registrar(completoDeA(oficial()));

        FichaDocumento ficha = fichaDeA();
        assertThat(ficha.estado().situacao()).isEqualTo(SituacaoDocumento.CANCELADA);
        assertThat(ficha.estado().metadados().numero()).contains(123L);
    }

    @Test
    void declaradoNaoAlteraDocumentoOficialMasGuardaArquivoEProveniencia() {
        catalogo.registrar(completoDeA(oficial()));
        FichaDocumento antes = fichaDeA();
        var vinculo = new VinculoEmpresa(empresaA, Direcao.SAIDA);
        var declarado = new RegistroArquivo(tenantA, CHAVE, VAZIO, SituacaoDocumento.CANCELADA, TipoArquivo.EVENTO,
                Optional.of("110111"), Optional.of(1), BLOB_EVENTO, List.of(vinculo), upload(Confianca.DECLARADO));

        ResultadoRegistro resultado = catalogoDepois.registrar(declarado);

        assertThat(resultado.documentoAlterado()).isFalse();
        assertThat(resultado.arquivoNovo()).isTrue();
        assertThat(resultado.provenienciaNova()).isTrue();
        assertThat(resultado.vinculosNovos()).containsExactly(vinculo);
        FichaDocumento depois = fichaDeA();
        assertThat(depois.estado()).isEqualTo(antes.estado());
        assertThat(depois.atualizadoEm()).isEqualTo(AGORA);
        assertThat(depois.arquivos()).hasSize(2);
        assertThat(depois.vinculos()).containsExactly(vinculo);
    }

    @Test
    void oficialSubstituiDocumentoDeclarado() {
        var outrosMetadados = new MetadadosDocumento(Optional.of(7), Optional.of(999L), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.of("3.10"));
        catalogo.registrar(new RegistroArquivo(tenantA, CHAVE, outrosMetadados, SituacaoDocumento.CANCELADA,
                TipoArquivo.COMPLETO, Optional.empty(), Optional.empty(), BLOB_RESUMO, List.of(),
                upload(Confianca.DECLARADO)));
        assertThat(fichaDeA().estado().soDeclarado()).isTrue();

        ResultadoRegistro oficial = catalogoDepois.registrar(completoDeA(oficial()));

        assertThat(oficial.documentoAlterado()).isTrue();
        FichaDocumento ficha = fichaDeA();
        assertThat(ficha.estado().soDeclarado()).isFalse();
        assertThat(ficha.estado().situacao()).isEqualTo(SituacaoDocumento.AUTORIZADA);
        assertThat(ficha.estado().metadados().serie()).contains(1);
        assertThat(ficha.estado().metadados().numero()).contains(123L);
        assertThat(ficha.estado().metadados().versaoLayout()).contains("4.00");
        assertThat(ficha.atualizadoEm()).isEqualTo(DEPOIS);
    }

    @Test
    void confirmacaoSefazDepoisDoUploadAcrescentaProveniencia() {
        catalogo.registrar(completoDeA(upload(Confianca.VALIDADO_ASSINATURA)));

        ResultadoRegistro confirmacao = catalogoDepois.registrar(completoDeA(upload(Confianca.CONFIRMADO_SEFAZ)));

        assertThat(confirmacao.arquivoNovo()).isFalse();
        assertThat(confirmacao.provenienciaNova()).isTrue();
        assertThat(confirmacao.documentoAlterado()).isFalse();
        assertThat(contar("proveniencia")).isEqualTo(2);
        assertThat(fichaDeA().arquivos().getFirst().proveniencias()).containsExactlyInAnyOrder(
                upload(Confianca.VALIDADO_ASSINATURA), upload(Confianca.CONFIRMADO_SEFAZ));
    }

    @Test
    void falhaNoMeioDesfazTudo() {
        var vinculo = new VinculoEmpresa(empresaA, Direcao.SAIDA);
        admin.useHandle(h -> {
            h.execute("""
                    create function recusar_proveniencia() returns trigger language plpgsql as $$
                    begin raise exception 'proveniencia recusada pelo teste'; end $$
                    """);
            h.execute("""
                    create trigger recusa_proveniencia before insert on proveniencia
                    for each row execute function recusar_proveniencia()
                    """);
        });

        var falha = catchThrowableOfType(FalhaCatalogoException.class,
                () -> catalogo.registrar(registro(tenantA, List.of(vinculo))));

        assertThat(falha).isNotNull();
        assertThat(contar("documento")).isZero();
        assertThat(contar("arquivo")).isZero();
        assertThat(contar("documento_empresa")).isZero();
        assertThat(contar("proveniencia")).isZero();

        admin.useHandle(h -> h.execute("drop trigger recusa_proveniencia on proveniencia"));

        ResultadoRegistro reprocessado = catalogo.registrar(registro(tenantA, List.of(vinculo)));

        assertThat(reprocessado.documentoCriado()).isTrue();
        assertThat(contar("documento")).isEqualTo(1);
        assertThat(contar("arquivo")).isEqualTo(1);
        assertThat(contar("documento_empresa")).isEqualTo(1);
        assertThat(contar("proveniencia")).isEqualTo(1);
    }

    @Test
    void falhaDeSqlNaoExpoeDestinatario() {
        admin.useHandle(h -> h.execute("alter table documento add constraint recusa_destinatario "
                + "check (destinatario_doc <> '" + CPF_DESTINATARIO + "')"));

        var falha = catchThrowableOfType(FalhaCatalogoException.class,
                () -> catalogo.registrar(registro(tenantA, List.of())));

        assertThat(falha.codigo()).isEqualTo("23514");
        assertThat(falha.getCause()).isNull();
        assertThat(falha.getSuppressed()).isEmpty();
        // a pilha impressa cobre mensagem, causas e suprimidas: é o que iria para um log
        assertThat(pilhaImpressa(falha))
                .doesNotContain(CPF_DESTINATARIO, EMITENTE.valor(), CHAVE.valor(), BLOB.chave(), "Failing row");
    }

    @Test
    void falhaAoConsultarTambemEhTraduzida() {
        admin.useHandle(h -> h.execute("revoke select on documento from fiscal_app"));

        var falha = catchThrowableOfType(FalhaCatalogoException.class, () -> catalogo.consultar(tenantA, CHAVE));

        assertThat(falha.codigo()).isEqualTo("42501");
        assertThat(falha.getCause()).isNull();
        assertThat(pilhaImpressa(falha)).doesNotContain(CHAVE.valor());
    }

    private static String pilhaImpressa(Throwable falha) {
        var saida = new StringWriter();
        falha.printStackTrace(new PrintWriter(saida));
        return saida.toString();
    }
}
