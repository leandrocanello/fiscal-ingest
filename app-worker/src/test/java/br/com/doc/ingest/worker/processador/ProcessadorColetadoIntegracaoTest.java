package br.com.doc.ingest.worker.processador;

import br.com.doc.ingest.adapter.fake.BlobStoreEmMemoria;
import br.com.doc.ingest.adapter.nfe.doczip.ParserDfeNfe;
import br.com.doc.ingest.domain.blob.Sha256;
import br.com.doc.ingest.domain.catalogo.ArquivoFicha;
import br.com.doc.ingest.domain.catalogo.Catalogo;
import br.com.doc.ingest.domain.catalogo.Confianca;
import br.com.doc.ingest.domain.catalogo.Direcao;
import br.com.doc.ingest.domain.catalogo.FichaDocumento;
import br.com.doc.ingest.domain.catalogo.OrigemArquivo;
import br.com.doc.ingest.domain.catalogo.ResultadoRegistro;
import br.com.doc.ingest.domain.catalogo.SituacaoDocumento;
import br.com.doc.ingest.domain.catalogo.TipoArquivo;
import br.com.doc.ingest.domain.catalogo.VinculoEmpresa;
import br.com.doc.ingest.domain.model.Ambiente;
import br.com.doc.ingest.domain.model.ChaveAcesso;
import br.com.doc.ingest.domain.model.Cnpj;
import br.com.doc.ingest.domain.model.FonteId;
import br.com.doc.ingest.domain.model.Nsu;
import br.com.doc.ingest.infra.postgres.JdbiCadastroEmpresas;
import br.com.doc.ingest.infra.postgres.JdbiCatalogo;
import br.com.doc.ingest.infra.postgres.JdbiQuarentena;
import br.com.doc.ingest.infra.postgres.Migracoes;
import br.com.doc.ingest.infra.postgres.PostgresTestBase;
import br.com.doc.ingest.infra.postgres.UsuarioAppCatalogo;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Processor de ponta a ponta (E3-S07): {@link ParserDfeNfe} real, catálogo, cadastro e quarentena no Postgres
 * (Testcontainers), sempre como usuário sem superuser (a RLS vale). XML e CNPJs sintéticos.
 */
class ProcessadorColetadoIntegracaoTest extends PostgresTestBase {

    private static final AtomicInteger CONTADOR = new AtomicInteger();
    private static final Instant AGORA = Instant.parse("2026-11-13T12:00:00Z");
    private static final FonteId FONTE = new FonteId("NFE_AN");

    /** Escritório do tenant: consulta com o próprio certificado e aparece no {@code autXML} dos clientes. */
    private static final Cnpj ESCRITORIO = new Cnpj("44444444000144");
    /** Cliente do escritório que emite a nota. */
    private static final Cnpj CLIENTE = new Cnpj("11111111000191");
    /** Empresa destinatária, consultando com o próprio certificado. */
    private static final Cnpj DESTINATARIA = new Cnpj("55555555000155");
    /** Contraparte fora do tenant. */
    private static final Cnpj TERCEIRO = new Cnpj("66666666000166");

    private Jdbi admin;
    private Catalogo catalogo;
    private ProcessadorColetado processador;
    private final BlobStoreEmMemoria blobs = new BlobStoreEmMemoria();
    private long tenant;
    private long nsu;

    @BeforeEach
    void prepara() {
        String schema = "processador_" + CONTADOR.incrementAndGet();
        UsuarioAppCatalogo.garantirPapeis();
        DataSource ds = dataSourceNoSchema(schema);
        Migracoes.aplicar(ds);
        admin = Jdbi.create(ds);
        UsuarioAppCatalogo.ligarRlsDeEmpresa(admin, schema);
        Jdbi app = UsuarioAppCatalogo.jdbi(admin, schema);
        Clock clock = Clock.fixed(AGORA, ZoneOffset.UTC);
        catalogo = new JdbiCatalogo(app, clock);
        processador = new ProcessadorColetado(blobs, new ParserDfeNfe(), catalogo, new JdbiCadastroEmpresas(app),
                new JdbiQuarentena(app, clock));
        tenant = novoTenant();
    }

    // ===== cenários =====

    @Test
    void resumoECompletoViramUmDocumento() {
        long destinataria = empresa(tenant, DESTINATARIA, "EMPRESA");
        String chave = chave(TERCEIRO, 1);

        var resumo = processar(DESTINATARIA, "resNFe_v1.01.xsd", resNFe(chave, TERCEIRO));
        assertThat(ficha(chave).estado().situacao()).isEqualTo(SituacaoDocumento.SO_RESUMO);

        var completo = processar(DESTINATARIA, "procNFe_v4.00.xsd",
                procNFe(chave, TERCEIRO, DESTINATARIA, List.of(), "100"));

        assertThat(completo.documentoId()).isEqualTo(resumo.documentoId());
        assertThat(completo.documentoCriado()).isFalse();
        FichaDocumento ficha = ficha(chave);
        assertThat(ficha.estado().situacao()).isEqualTo(SituacaoDocumento.AUTORIZADA);
        assertThat(ficha.arquivos()).extracting(ArquivoFicha::tipo)
                .containsExactlyInAnyOrder(TipoArquivo.RESUMO, TipoArquivo.COMPLETO);
        assertThat(ficha.vinculos()).containsExactly(new VinculoEmpresa(destinataria, Direcao.ENTRADA));
        assertThat(contar("documento")).isEqualTo(1);
        assertThat(quarentenasAbertas()).isZero();
    }

    @Test
    void saidaPeloAutXmlDoEscritorioVaiParaAEmpresaEmitente() {
        empresa(tenant, ESCRITORIO, "ESCRITORIO");
        long cliente = empresa(tenant, CLIENTE, "EMPRESA");
        String chave = chave(CLIENTE, 2);

        processar(ESCRITORIO, "procNFe_v4.00.xsd", procNFe(chave, CLIENTE, TERCEIRO, List.of(ESCRITORIO), "100"));

        // O escritório só está no autXML: não ganha vínculo (D2); a nota é saída do cliente.
        assertThat(ficha(chave).vinculos()).containsExactly(new VinculoEmpresa(cliente, Direcao.SAIDA));
        assertThat(quarentenasAbertas()).isZero();
    }

    @Test
    void clienteNaoCadastradoPeloAutXmlFicaEmQuarentena() {
        empresa(tenant, ESCRITORIO, "ESCRITORIO");
        String chave = chave(CLIENTE, 3);

        var r = processar(ESCRITORIO, "procNFe_v4.00.xsd",
                procNFe(chave, CLIENTE, TERCEIRO, List.of(ESCRITORIO), "100"));

        assertThat(ficha(chave).vinculos()).isEmpty();
        assertThat(quarentenasAbertasDoArquivo(r.arquivoId())).isEqualTo(1);
    }

    @Test
    void cadastrarEReprocessarFechaQuarentena() {
        empresa(tenant, ESCRITORIO, "ESCRITORIO");
        String chave = chave(CLIENTE, 4);
        ItemColetado item = item(ESCRITORIO, "procNFe_v4.00.xsd",
                procNFe(chave, CLIENTE, TERCEIRO, List.of(ESCRITORIO), "100"));
        processador.processar(item);
        assertThat(quarentenasAbertas()).isEqualTo(1);

        long cliente = empresa(tenant, CLIENTE, "EMPRESA");
        processador.processar(item);

        assertThat(ficha(chave).vinculos()).containsExactly(new VinculoEmpresa(cliente, Direcao.SAIDA));
        assertThat(quarentenasAbertas()).isZero();
        assertThat(contar("quarentena")).isEqualTo(1);
    }

    @Test
    void provenienciaDaColetaEhOficialComLedger() {
        empresa(tenant, DESTINATARIA, "EMPRESA");
        String chave = chave(TERCEIRO, 5);
        ItemColetado item = item(DESTINATARIA, "resNFe_v1.01.xsd", resNFe(chave, TERCEIRO));

        processador.processar(item);

        assertThat(ficha(chave).arquivos()).singleElement().satisfies(a -> {
            assertThat(a.blob()).isEqualTo(item.blob());
            assertThat(a.proveniencias()).singleElement().satisfies(p -> {
                assertThat(p.origem()).isEqualTo(OrigemArquivo.DFE_AN);
                assertThat(p.confianca()).isEqualTo(Confianca.OFICIAL);
                assertThat(p.ledgerNsuId()).contains(item.ledgerNsuId());
                assertThat(p.recebimentoId()).isEmpty();
            });
        });
    }

    @Test
    void reentregaEhNoOp() {
        empresa(tenant, ESCRITORIO, "ESCRITORIO");
        String chave = chave(CLIENTE, 6);
        ItemColetado item = item(ESCRITORIO, "procNFe_v4.00.xsd",
                procNFe(chave, CLIENTE, TERCEIRO, List.of(ESCRITORIO), "100"));
        processador.processar(item);
        long[] antes = {contar("documento"), contar("arquivo"), contar("proveniencia"), contar("documento_empresa"),
                contar("quarentena")};

        var r = unico(processador.processar(item));

        assertThat(r.documentoCriado()).isFalse();
        assertThat(r.documentoAlterado()).isFalse();
        assertThat(r.arquivoNovo()).isFalse();
        assertThat(r.provenienciaNova()).isFalse();
        assertThat(r.vinculosNovos()).isEmpty();
        assertThat(new long[] {contar("documento"), contar("arquivo"), contar("proveniencia"),
                contar("documento_empresa"), contar("quarentena")}).containsExactly(antes);
    }

    @Test
    void transferenciaEntreEmpresasTemDoisVinculos() {
        long cliente = empresa(tenant, CLIENTE, "EMPRESA");
        long destinataria = empresa(tenant, DESTINATARIA, "EMPRESA");
        String chave = chave(CLIENTE, 7);

        processar(DESTINATARIA, "procNFe_v4.00.xsd", procNFe(chave, CLIENTE, DESTINATARIA, List.of(), "100"));

        assertThat(ficha(chave).vinculos()).containsExactlyInAnyOrder(
                new VinculoEmpresa(cliente, Direcao.SAIDA), new VinculoEmpresa(destinataria, Direcao.ENTRADA));
        assertThat(contar("documento")).isEqualTo(1);
        assertThat(quarentenasAbertas()).isZero();
    }

    @Test
    void cancelamentoAtualizaSituacao() {
        empresa(tenant, DESTINATARIA, "EMPRESA");
        String chave = chave(TERCEIRO, 8);
        processar(DESTINATARIA, "procNFe_v4.00.xsd", procNFe(chave, TERCEIRO, DESTINATARIA, List.of(), "100"));

        var evento = processar(DESTINATARIA, "procEventoNFe_v1.00.xsd", procEvento(chave, TERCEIRO, "110111"));

        FichaDocumento ficha = ficha(chave);
        assertThat(evento.documentoCriado()).isFalse();
        assertThat(ficha.estado().situacao()).isEqualTo(SituacaoDocumento.CANCELADA);
        assertThat(ficha.arquivos()).extracting(ArquivoFicha::tipo)
                .containsExactlyInAnyOrder(TipoArquivo.COMPLETO, TipoArquivo.EVENTO);
        assertThat(quarentenasAbertas()).isZero();
    }

    @Test
    void eventoAntesDoXmlFechaQuarentenaQuandoANotaChega() {
        long destinataria = empresa(tenant, DESTINATARIA, "EMPRESA");
        String chave = chave(TERCEIRO, 9);

        // O evento só vincula o emitente da chave (D1), que não é do tenant: fica em quarentena.
        var evento = processar(DESTINATARIA, "procEventoNFe_v1.00.xsd", procEvento(chave, TERCEIRO, "210210"));
        assertThat(ficha(chave).vinculos()).isEmpty();
        assertThat(quarentenasAbertasDoArquivo(evento.arquivoId())).isEqualTo(1);

        processar(DESTINATARIA, "procNFe_v4.00.xsd", procNFe(chave, TERCEIRO, DESTINATARIA, List.of(), "100"));

        assertThat(ficha(chave).vinculos()).containsExactly(new VinculoEmpresa(destinataria, Direcao.ENTRADA));
        assertThat(quarentenasAbertas()).isZero();
        assertThat(contar("quarentena")).isEqualTo(1);
        assertThat(contar("documento")).isEqualTo(1);
    }

    @Test
    void notaNaoVazaParaTenantQueNaoConsultou() {
        empresa(tenant, DESTINATARIA, "EMPRESA");
        long outro = novoTenant();
        empresa(outro, TERCEIRO, "EMPRESA");
        String chave = chave(TERCEIRO, 10);

        var r = processador.processar(item(DESTINATARIA, "procNFe_v4.00.xsd",
                procNFe(chave, TERCEIRO, DESTINATARIA, List.of(), "100")));

        // O tenant B tem o emitente, mas não o CNPJ consultado: a coleta não é dele (§8.1, inv. 10).
        assertThat(((ResultadoProcessamento.Registrado) r).resultados()).hasSize(1);
        assertThat(catalogo.consultar(outro, new ChaveAcesso(chave))).isEmpty();
        assertThat(documentosDoTenant(outro)).isZero();
        assertThat(documentosDoTenant(tenant)).isEqualTo(1);
    }

    @Test
    void cnpjEmDoisTenantsViraDoisDocumentos() {
        long destinatariaA = empresa(tenant, DESTINATARIA, "EMPRESA");
        long outro = novoTenant();
        long destinatariaB = empresa(outro, DESTINATARIA, "EMPRESA");
        String chave = chave(TERCEIRO, 11);

        var r = processador.processar(item(DESTINATARIA, "procNFe_v4.00.xsd",
                procNFe(chave, TERCEIRO, DESTINATARIA, List.of(), "100")));

        var resultados = ((ResultadoProcessamento.Registrado) r).resultados();
        assertThat(resultados).hasSize(2).allMatch(ResultadoRegistro::documentoCriado);
        assertThat(resultados.get(0).documentoId()).isNotEqualTo(resultados.get(1).documentoId());
        assertThat(ficha(chave).vinculos()).containsExactly(new VinculoEmpresa(destinatariaA, Direcao.ENTRADA));
        assertThat(catalogo.consultar(outro, new ChaveAcesso(chave)).orElseThrow().vinculos())
                .containsExactly(new VinculoEmpresa(destinatariaB, Direcao.ENTRADA));
        assertThat(documentosDoTenant(tenant)).isEqualTo(1);
        assertThat(documentosDoTenant(outro)).isEqualTo(1);
        assertThat(quarentenasAbertas()).isZero();
    }

    // ===== apoio =====

    private ResultadoRegistro processar(Cnpj consultado, String schema, String xml) {
        return unico(processador.processar(item(consultado, schema, xml)));
    }

    private static ResultadoRegistro unico(ResultadoProcessamento resultado) {
        assertThat(resultado).isInstanceOf(ResultadoProcessamento.Registrado.class);
        return ((ResultadoProcessamento.Registrado) resultado).resultados().getFirst();
    }

    /** Grava o bruto e o NSU no ledger, como o fetcher faria antes do processor (inv. 5). */
    private ItemColetado item(Cnpj consultado, String schema, String xml) {
        byte[] bytes = xml.getBytes(StandardCharsets.UTF_8);
        String sha = Sha256.hex(bytes);
        var blob = blobs.gravar("raw/pull/nfe/" + sha + ".xml", bytes, sha);
        long nsuAtual = ++nsu;
        long ledgerId = admin.withHandle(h -> h.createUpdate("insert into ledger_nsu "
                        + "(fonte, cnpj_consultado, ambiente, nsu, status, recebido_em) "
                        + "values (:f, :c, 'PRODUCAO', :n, 'BAIXADO', now())")
                .bind("f", FONTE.id()).bind("c", consultado.valor()).bind("n", nsuAtual)
                .executeAndReturnGeneratedKeys("id").mapTo(Long.class).one());
        return new ItemColetado(FONTE, consultado, Ambiente.PRODUCAO, new Nsu(nsuAtual), ledgerId, blob, schema, AGORA);
    }

    private long novoTenant() {
        return admin.withHandle(h -> h.createUpdate("insert into tenant (nome) values ('t')")
                .executeAndReturnGeneratedKeys("id").mapTo(Long.class).one());
    }

    private long empresa(long tenantId, Cnpj cnpj, String tipo) {
        return admin.withHandle(h -> h.createUpdate("insert into empresa "
                        + "(tenant_id, cnpj, raiz_cnpj, razao_social, tipo, ambiente, ativo) "
                        + "values (:t, :c, :r, 'Empresa Sintetica', :tipo, 'PRODUCAO', true)")
                .bind("t", tenantId).bind("c", cnpj.valor()).bind("r", cnpj.valor().substring(0, 8))
                .bind("tipo", tipo)
                .executeAndReturnGeneratedKeys("id").mapTo(Long.class).one());
    }

    private FichaDocumento ficha(String chave) {
        return catalogo.consultar(tenant, new ChaveAcesso(chave)).orElseThrow();
    }

    private long contar(String tabela) {
        return admin.withHandle(h -> h.createQuery("select count(*) from " + tabela).mapTo(Long.class).one());
    }

    private long documentosDoTenant(long tenantId) {
        return admin.withHandle(h -> h.createQuery("select count(*) from documento where tenant_id = :t")
                .bind("t", tenantId).mapTo(Long.class).one());
    }

    private long quarentenasAbertas() {
        return admin.withHandle(h -> h.createQuery("select count(*) from quarentena where resolvido_em is null")
                .mapTo(Long.class).one());
    }

    private long quarentenasAbertasDoArquivo(long arquivoId) {
        return admin.withHandle(h -> h.createQuery("select count(*) from quarentena "
                        + "where resolvido_em is null and arquivo_id = :a and motivo = 'SEM_EMPRESA'")
                .bind("a", arquivoId).mapTo(Long.class).one());
    }

    // ===== XML sintético =====

    /** Chave sintética: UF 35, AAMM 2610, CNPJ do emitente (posições 7–20), mod 55, série 001. */
    static String chave(Cnpj emitente, int numero) {
        return "352610" + emitente.valor() + "55001" + String.format("%09d", numero) + "1" + "12345678" + "0";
    }

    static String resNFe(String chave, Cnpj emitente) {
        return "<resNFe xmlns=\"http://www.portalfiscal.inf.br/nfe\" versao=\"1.01\">"
                + "<chNFe>" + chave + "</chNFe><CNPJ>" + emitente.valor() + "</CNPJ><xNome>Emitente</xNome>"
                + "<dhEmi>2026-10-20T10:30:00-03:00</dhEmi><tpNF>1</tpNF><vNF>1234.56</vNF>"
                + "<cSitNFe>1</cSitNFe></resNFe>";
    }

    static String procNFe(String chave, Cnpj emitente, Cnpj destinatario, List<Cnpj> autorizados,
                                  String cStat) {
        StringBuilder autXml = new StringBuilder();
        autorizados.forEach(c -> autXml.append("<autXML><CNPJ>").append(c.valor()).append("</CNPJ></autXML>"));
        return "<nfeProc xmlns=\"http://www.portalfiscal.inf.br/nfe\" versao=\"4.00\"><NFe><infNFe Id=\"NFe" + chave
                + "\" versao=\"4.00\">"
                + "<ide><cUF>35</cUF><mod>55</mod><serie>1</serie><nNF>" + Integer.parseInt(chave.substring(25, 34))
                + "</nNF><dhEmi>2026-10-20T10:30:00-03:00</dhEmi></ide>"
                + "<emit><CNPJ>" + emitente.valor() + "</CNPJ><xNome>Emitente</xNome></emit>"
                + "<dest><CNPJ>" + destinatario.valor() + "</CNPJ><xNome>Destinatario</xNome></dest>"
                + "<total><ICMSTot><vNF>1234.56</vNF></ICMSTot></total>"
                + autXml
                + "</infNFe></NFe><protNFe versao=\"4.00\"><infProt><chNFe>" + chave + "</chNFe>"
                + "<cStat>" + cStat + "</cStat></infProt></protNFe></nfeProc>";
    }

    /** Evento autorizado; o autor é o próprio emitente (o parser não usa o autor, inv. 10). */
    static String procEvento(String chave, Cnpj autor, String tpEvento) {
        return "<procEventoNFe xmlns=\"http://www.portalfiscal.inf.br/nfe\" versao=\"1.00\"><evento versao=\"1.00\">"
                + "<infEvento Id=\"ID" + tpEvento + chave + "01\"><cOrgao>91</cOrgao><CNPJ>" + autor.valor()
                + "</CNPJ><chNFe>" + chave + "</chNFe><dhEvento>2026-10-21T09:00:00-03:00</dhEvento>"
                + "<tpEvento>" + tpEvento + "</tpEvento><nSeqEvento>1</nSeqEvento>"
                + "<verEvento>1.00</verEvento></infEvento></evento>"
                + "<retEvento versao=\"1.00\"><infEvento><cStat>135</cStat></infEvento></retEvento></procEventoNFe>";
    }
}
