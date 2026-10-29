package br.com.doc.ingest.adapter.nfe;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.doc.ingest.adapter.fake.BlobStoreEmMemoria;
import br.com.doc.ingest.adapter.nfe.soap.ContratoDistDfe;
import br.com.doc.ingest.adapter.nfe.soap.TipoAmbiente;
import br.com.doc.ingest.adapter.nfe.transporte.TransporteHttpJdk;
import br.com.doc.ingest.domain.adapter.BuscarLoteResultado;
import br.com.doc.ingest.domain.adapter.BuscarUmResultado;
import br.com.doc.ingest.domain.adapter.Capacidades;
import br.com.doc.ingest.domain.adapter.ContextoConsulta;
import br.com.doc.ingest.domain.adapter.Credencial;
import br.com.doc.ingest.domain.adapter.Cursor;
import br.com.doc.ingest.domain.adapter.Desfecho;
import br.com.doc.ingest.domain.blob.BlobRef;
import br.com.doc.ingest.domain.blob.BlobStore;
import br.com.doc.ingest.domain.blob.Sha256;
import br.com.doc.ingest.domain.model.Cnpj;
import br.com.doc.ingest.domain.model.Documento;
import br.com.doc.ingest.domain.model.FonteId;
import br.com.doc.ingest.domain.model.Nsu;
import com.github.tomakehurst.wiremock.WireMockServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** buscarLote contra WireMock em localhost (inv. 14). Fixtures sintéticas. */
@DisplayName("NfeNacionalAdapter.buscarLote com WireMock")
class NfeNacionalAdapterTest {

    private static final String CAMINHO = "/ws/distdfe";
    private static final FonteId FONTE = new FonteId("NFE_AN");
    private static final Cnpj CNPJ = new Cnpj("11111111000191");
    private static final Instant AGORA = Instant.parse("2026-10-28T15:00:00Z");
    private static final String NFE_NS = "http://www.portalfiscal.inf.br/nfe";

    private static final String XML_RES = "<resNFe xmlns=\"" + NFE_NS + "\"><xNome>Sintetico A</xNome></resNFe>";
    private static final String XML_PROC = "<nfeProc xmlns=\"" + NFE_NS + "\"><NFe>sintetica</NFe></nfeProc>";
    private static final String XML_EVT = "<procEventoNFe xmlns=\"" + NFE_NS + "\"><evento>210210</evento></procEventoNFe>";

    private WireMockServer servidor;
    private BlobStoreEmMemoria blobs;
    private NfeNacionalAdapter adapter;

    @BeforeEach
    void subir() {
        servidor = new WireMockServer(options().dynamicPort());
        servidor.start();
        blobs = new BlobStoreEmMemoria();
        adapter = adapterCom(blobs);
    }

    private NfeNacionalAdapter adapterCom(BlobStore store) {
        return adapterCom(store, Duration.ofSeconds(5));
    }

    private NfeNacionalAdapter adapterCom(BlobStore store, Duration timeout) {
        URI endpoint = URI.create("http://localhost:" + servidor.port() + CAMINHO);
        NfeNacionalAdapter novo = new NfeNacionalAdapter(FONTE, TipoAmbiente.PRODUCAO, endpoint, store,
                Clock.fixed(AGORA, ZoneOffset.UTC),
                credencial -> new TransporteHttpJdk(HttpClient.newHttpClient(), timeout, 1024 * 1024));
        novo.autenticar(new Credencial(new byte[] {1}, new char[0]));
        return novo;
    }

    /** BlobStore espião: registra a ordem das chaves gravadas e delega ao store em memória. */
    private static final class BlobStoreEspiao implements BlobStore {
        private final BlobStore alvo;
        private final List<String> chavesGravadas = new ArrayList<>();

        BlobStoreEspiao(BlobStore alvo) {
            this.alvo = alvo;
        }

        @Override
        public BlobRef gravar(String chave, byte[] conteudo, String sha256) {
            chavesGravadas.add(chave);
            return alvo.gravar(chave, conteudo, sha256);
        }

        @Override
        public Optional<byte[]> ler(BlobRef ref) {
            return alvo.ler(ref);
        }
    }

    @AfterEach
    void parar() {
        servidor.stop();
    }

    private void responder(String corpo) {
        servidor.stubFor(post(urlEqualTo(CAMINHO)).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/soap+xml; charset=utf-8").withBody(corpo)));
    }

    private static String gzipBase64(String xml) {
        var saida = new ByteArrayOutputStream();
        try (var gz = new GZIPOutputStream(saida)) {
            gz.write(xml.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return Base64.getEncoder().encodeToString(saida.toByteArray());
    }

    private static String docZip(int nsu, String schema, String xml) {
        return "<docZip NSU=\"" + String.format("%015d", nsu) + "\" schema=\"" + schema + "\">"
                + gzipBase64(xml) + "</docZip>";
    }

    private static String resposta(String cStat, int ultNsu, int maxNsu, String lote) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<soap:Envelope xmlns:soap=\"" + ContratoDistDfe.NS_SOAP12 + "\"><soap:Body>"
                + "<nfeDistDFeInteresseResponse xmlns=\"" + ContratoDistDfe.NS_WSDL + "\"><nfeDistDFeInteresseResult>"
                + "<retDistDFeInt versao=\"1.01\" xmlns=\"" + NFE_NS + "\">"
                + "<tpAmb>1</tpAmb><verAplic>1.0</verAplic><cStat>" + cStat + "</cStat>"
                + "<xMotivo>Documento localizado</xMotivo><dhResp>2026-10-28T12:00:00-03:00</dhResp>"
                + "<ultNSU>" + String.format("%015d", ultNsu) + "</ultNSU>"
                + "<maxNSU>" + String.format("%015d", maxNsu) + "</maxNSU>"
                + (lote.isEmpty() ? "" : "<loteDistDFeInt>" + lote + "</loteDistDFeInt>")
                + "</retDistDFeInt></nfeDistDFeInteresseResult></nfeDistDFeInteresseResponse>"
                + "</soap:Body></soap:Envelope>";
    }

    private static UUID idEsperado(int nsu) {
        return UUID.nameUUIDFromBytes(("NFE_AN|PRODUCAO|11111111000191|" + nsu).getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("138 com 3 docZips: Ok, 3 documentos com BlobRef, cursor no ultNSU e envelope gravado")
    void lote138ComDocZips() {
        String corpo = resposta("138", 3, 3,
                docZip(1, "resNFe_v1.01.xsd", XML_RES)
                        + docZip(2, "procNFe_v4.00.xsd", XML_PROC)
                        + docZip(3, "procEventoNFe_v1.00.xsd", XML_EVT));
        responder(corpo);

        BuscarLoteResultado r = adapter.buscarLote(new ContextoConsulta(FONTE, CNPJ), Cursor.inicial());

        assertThat(r.desfecho()).isEqualTo(new Desfecho.Ok());
        assertThat(r.proximoCursor()).isEqualTo(new Cursor("3"));
        assertThat(r.ultNsu()).isEqualTo(new Nsu(3));
        assertThat(r.temMais()).isFalse();

        List<Documento> docs = r.documentos();
        assertThat(docs).extracting(Documento::nsu).containsExactly(new Nsu(1), new Nsu(2), new Nsu(3));
        assertThat(docs).extracting(Documento::tipoConteudo).containsExactly("RES_NFE", "PROC_NFE", "PROC_EVENTO_NFE");
        assertThat(docs).extracting(Documento::id).containsExactly(idEsperado(1), idEsperado(2), idEsperado(3));
        assertThat(docs).allSatisfy(d -> {
            assertThat(d.fonte()).isEqualTo(FONTE);
            assertThat(d.cnpjDestinatario()).isEqualTo(CNPJ);
            assertThat(d.recebidoEm()).isEqualTo(AGORA);
            assertThat(d.chave()).isEmpty();
            assertThat(d.blobRef()).isPresent();
        });

        String[] xmls = {XML_RES, XML_PROC, XML_EVT};
        for (int i = 0; i < 3; i++) {
            byte[] esperado = xmls[i].getBytes(StandardCharsets.UTF_8);
            String sha = Sha256.hex(esperado);
            String chave = ChavesBlobPull.documento(FONTE, TipoAmbiente.PRODUCAO, CNPJ, new Nsu(i + 1), sha);
            assertThat(docs.get(i).blobRef()).contains(chave);
            assertThat(blobs.ler(new BlobRef(chave, sha))).hasValueSatisfying(b -> assertThat(b).isEqualTo(esperado));
        }

        byte[] bruto = corpo.getBytes(StandardCharsets.UTF_8);
        String shaEnvelope = Sha256.hex(bruto);
        BlobRef refEnvelope = new BlobRef(
                ChavesBlobPull.envelope(FONTE, TipoAmbiente.PRODUCAO, CNPJ, AGORA, shaEnvelope), shaEnvelope);
        assertThat(r.envelope()).contains(refEnvelope);
        assertThat(blobs.ler(refEnvelope)).hasValueSatisfying(b -> assertThat(b).isEqualTo(bruto));
    }

    @Test
    @DisplayName("ultNSU < maxNSU: temMais e cursor no ultNSU da resposta")
    void temMaisQuandoUltNsuMenorQueMaxNsu() {
        responder(resposta("138", 2, 9,
                docZip(1, "resNFe_v1.01.xsd", XML_RES) + docZip(2, "procNFe_v4.00.xsd", XML_PROC)));

        BuscarLoteResultado r = adapter.buscarLote(new ContextoConsulta(FONTE, CNPJ), Cursor.inicial());

        assertThat(r.desfecho()).isEqualTo(new Desfecho.Ok());
        assertThat(r.temMais()).isTrue();
        assertThat(r.ultNsu()).isEqualTo(new Nsu(2));
        assertThat(r.proximoCursor()).isEqualTo(new Cursor("2"));
        assertThat(r.documentos()).hasSize(2);
    }

    @Test
    @DisplayName("inv. 5: o envelope bruto é gravado antes de qualquer docZip")
    void envelopeGravadoAntesDoParse() {
        String corpo = resposta("138", 2, 2,
                docZip(1, "resNFe_v1.01.xsd", XML_RES) + docZip(2, "procNFe_v4.00.xsd", XML_PROC));
        responder(corpo);
        BlobStoreEspiao espiao = new BlobStoreEspiao(blobs);

        BuscarLoteResultado r = adapterCom(espiao).buscarLote(new ContextoConsulta(FONTE, CNPJ), Cursor.inicial());

        String chaveEnvelope = r.envelope().orElseThrow().chave();
        List<String> chavesDocs = r.documentos().stream().map(d -> d.blobRef().orElseThrow()).toList();
        assertThat(espiao.chavesGravadas).hasSize(3);
        assertThat(espiao.chavesGravadas.get(0)).isEqualTo(chaveEnvelope).endsWith(".envelope.xml");
        assertThat(espiao.chavesGravadas.subList(1, 3)).containsExactlyElementsOf(chavesDocs);
    }

    @Test
    @DisplayName("schema desconhecido: documento DESCONHECIDO guardado byte a byte")
    void docZipDesconhecidoGuardadoByteAByte() {
        String xmlDesconhecido = "<xyzNFe xmlns=\"" + NFE_NS + "\"><campo>\u00e7\u00e3o sintética</campo></xyzNFe>";
        responder(resposta("138", 7, 7, docZip(7, "xyzNFe_v9.99.xsd", xmlDesconhecido)));

        BuscarLoteResultado r = adapter.buscarLote(new ContextoConsulta(FONTE, CNPJ), Cursor.inicial());

        assertThat(r.documentos()).singleElement().satisfies(d -> {
            assertThat(d.tipoConteudo()).isEqualTo("DESCONHECIDO");
            assertThat(d.nsu()).isEqualTo(new Nsu(7));
            byte[] esperado = xmlDesconhecido.getBytes(StandardCharsets.UTF_8);
            String sha = Sha256.hex(esperado);
            assertThat(d.blobRef()).contains(
                    ChavesBlobPull.documento(FONTE, TipoAmbiente.PRODUCAO, CNPJ, new Nsu(7), sha));
            assertThat(blobs.ler(new BlobRef(d.blobRef().orElseThrow(), sha)))
                    .hasValueSatisfying(b -> assertThat(b).isEqualTo(esperado));
        });
    }

    @Test
    @DisplayName("inv. 7: reconsultar o mesmo lote dá as mesmas chaves, sem conflito de blob")
    void reconsultaMesmoLoteEhIdempotente() {
        responder(resposta("138", 3, 3,
                docZip(1, "resNFe_v1.01.xsd", XML_RES)
                        + docZip(2, "procNFe_v4.00.xsd", XML_PROC)
                        + docZip(3, "procEventoNFe_v1.00.xsd", XML_EVT)));
        ContextoConsulta ctx = new ContextoConsulta(FONTE, CNPJ);

        BuscarLoteResultado primeira = adapter.buscarLote(ctx, Cursor.inicial());
        BuscarLoteResultado segunda = adapter.buscarLote(ctx, Cursor.inicial());

        assertThat(segunda.envelope()).isEqualTo(primeira.envelope());
        assertThat(segunda.documentos()).isEqualTo(primeira.documentos());
    }

    private BuscarLoteResultado buscarComCursor(String cStat, int ultNsu, int maxNsu, String cursor) {
        responder(resposta(cStat, ultNsu, maxNsu, ""));
        return adapter.buscarLote(new ContextoConsulta(FONTE, CNPJ), new Cursor(cursor));
    }

    private void assertEnvelopeGravado(BuscarLoteResultado r) {
        assertThat(r.envelope()).isPresent();
        assertThat(blobs.ler(r.envelope().orElseThrow())).isPresent();
    }

    private void assertNaoAvancou(BuscarLoteResultado r, String cursorEntrada) {
        assertThat(r.documentos()).isEmpty();
        assertThat(r.proximoCursor()).isEqualTo(new Cursor(cursorEntrada));
        assertThat(r.ultNsu()).isEqualTo(new Nsu(Long.parseLong(cursorEntrada)));
        assertThat(r.temMais()).isFalse();
        assertEnvelopeGravado(r);
    }

    @Test
    @DisplayName("137: Vazio, cursor no ultNSU da resposta, sem documentos e sem temMais")
    void vazio137AvancaParaUltNsuSemDocumentos() {
        BuscarLoteResultado r = buscarComCursor("137", 12, 12, "10");

        assertThat(r.desfecho()).isEqualTo(new Desfecho.Vazio());
        assertThat(r.documentos()).isEmpty();
        assertThat(r.proximoCursor()).isEqualTo(new Cursor("12"));
        assertThat(r.ultNsu()).isEqualTo(new Nsu(12));
        assertThat(r.temMais()).isFalse();
        assertEnvelopeGravado(r);
    }

    @Test
    @DisplayName("inv. 1: 656 com ultNSU maior na resposta não avança o cursor")
    void limiteAtingidoNaoAvanca() {
        BuscarLoteResultado r = buscarComCursor("656", 50, 80, "10");

        assertThat(r.desfecho()).isEqualTo(new Desfecho.LimiteAtingido(Duration.ofHours(1)));
        assertNaoAvancou(r, "10");
    }

    @Test
    @DisplayName("108: IndisponivelTemporario sem avançar")
    void paralisado108() {
        BuscarLoteResultado r = buscarComCursor("108", 20, 20, "10");

        assertThat(r.desfecho()).isEqualTo(new Desfecho.IndisponivelTemporario(Duration.ofMinutes(5)));
        assertNaoAvancou(r, "10");
    }

    @Test
    @DisplayName("109: ServicoFora sem avançar")
    void paralisado109() {
        BuscarLoteResultado r = buscarComCursor("109", 20, 20, "10");

        assertThat(r.desfecho()).isInstanceOf(Desfecho.ServicoFora.class);
        assertNaoAvancou(r, "10");
    }

    @Test
    @DisplayName("cStat desconhecido: IndisponivelTemporario(5 min) sem avançar")
    void cStatDesconhecido() {
        BuscarLoteResultado r = buscarComCursor("999", 20, 20, "10");

        assertThat(r.desfecho()).isEqualTo(new Desfecho.IndisponivelTemporario(Duration.ofMinutes(5)));
        assertNaoAvancou(r, "10");
    }

    private static final Desfecho TEMPORARIO = new Desfecho.IndisponivelTemporario(Duration.ofMinutes(5));

    private void responderStatus(int status, String corpo) {
        servidor.stubFor(post(urlEqualTo(CAMINHO)).willReturn(aResponse().withStatus(status)
                .withHeader("Content-Type", "application/soap+xml; charset=utf-8").withBody(corpo)));
    }

    private void assertSemAvanco(BuscarLoteResultado r, String cursorEntrada) {
        assertThat(r.documentos()).isEmpty();
        assertThat(r.proximoCursor()).isEqualTo(new Cursor(cursorEntrada));
        assertThat(r.ultNsu()).isEqualTo(new Nsu(Long.parseLong(cursorEntrada)));
        assertThat(r.temMais()).isFalse();
    }

    @Test
    @DisplayName("timeout: IndisponivelTemporario, sem envelope e sem avançar")
    void timeout() {
        servidor.stubFor(post(urlEqualTo(CAMINHO)).willReturn(aResponse().withStatus(200)
                .withFixedDelay(2_000).withBody(resposta("138", 1, 1, docZip(1, "resNFe_v1.01.xsd", XML_RES)))));
        NfeNacionalAdapter lento = adapterCom(blobs, Duration.ofMillis(200));

        BuscarLoteResultado r = lento.buscarLote(new ContextoConsulta(FONTE, CNPJ), new Cursor("10"));

        assertThat(r.desfecho()).isEqualTo(TEMPORARIO);
        assertSemAvanco(r, "10");
        assertThat(r.envelope()).isEmpty();
    }

    @Test
    @DisplayName("HTTP 500 com SOAP Fault: IndisponivelTemporario, envelope gravado, sem avançar")
    void erro500ComSoapFault() {
        responderStatus(500, "<soap:Envelope xmlns:soap=\"" + ContratoDistDfe.NS_SOAP12 + "\"><soap:Body>"
                + "<soap:Fault><soap:Code><soap:Value>soap:Receiver</soap:Value></soap:Code>"
                + "<soap:Reason><soap:Text xml:lang=\"pt\">falha interna</soap:Text></soap:Reason></soap:Fault>"
                + "</soap:Body></soap:Envelope>");

        BuscarLoteResultado r = adapter.buscarLote(new ContextoConsulta(FONTE, CNPJ), new Cursor("10"));

        assertThat(r.desfecho()).isEqualTo(TEMPORARIO);
        assertNaoAvancou(r, "10");
    }

    @Test
    @DisplayName("HTTP 403: ErroAutenticacao, envelope gravado, sem avançar")
    void erro403() {
        responderStatus(403, "proibido");

        BuscarLoteResultado r = adapter.buscarLote(new ContextoConsulta(FONTE, CNPJ), new Cursor("10"));

        assertThat(r.desfecho()).isInstanceOf(Desfecho.ErroAutenticacao.class);
        assertNaoAvancou(r, "10");
    }

    @Test
    @DisplayName("corpo malformado: IndisponivelTemporario sem vazar conteúdo, envelope gravado")
    void corpoMalformado() {
        responder("<soap:Envelope SEGREDO-XYZ <<< nada disso é XML");

        BuscarLoteResultado r = adapter.buscarLote(new ContextoConsulta(FONTE, CNPJ), new Cursor("10"));

        assertThat(r.desfecho()).isEqualTo(TEMPORARIO);
        assertThat(r.toString()).doesNotContain("SEGREDO-XYZ");
        assertNaoAvancou(r, "10");
    }

    @Test
    @DisplayName("docZip corrompido num lote de 3: lote inteiro IndisponivelTemporario, nenhum documento")
    void docZipCorrompido() {
        String corrompido = "<docZip NSU=\"000000000000012\" schema=\"procNFe_v4.00.xsd\">@@@nao-eh-base64@@@</docZip>";
        responder(resposta("138", 13, 13,
                docZip(11, "resNFe_v1.01.xsd", XML_RES) + corrompido + docZip(13, "procEventoNFe_v1.00.xsd", XML_EVT)));

        BuscarLoteResultado r = adapter.buscarLote(new ContextoConsulta(FONTE, CNPJ), new Cursor("10"));

        assertThat(r.desfecho()).isEqualTo(TEMPORARIO);
        assertNaoAvancou(r, "10");
    }

    @Test
    @DisplayName("inv. 1: ultNSU da resposta menor que o cursor é cursor inconsistente")
    void ultNsuMenorQueCursorEhCursorInconsistente() {
        responder(resposta("138", 5, 5, docZip(5, "resNFe_v1.01.xsd", XML_RES)));

        BuscarLoteResultado r = adapter.buscarLote(new ContextoConsulta(FONTE, CNPJ), new Cursor("10"));

        assertThat(r.desfecho()).isEqualTo(new Desfecho.IndisponivelDefinitivo("cursor inconsistente"));
        assertNaoAvancou(r, "10");
    }

    @Test
    @DisplayName("inv. 1: 137 com ultNSU menor que o cursor também é cursor inconsistente")
    void vazioComUltNsuMenorQueCursorEhCursorInconsistente() {
        BuscarLoteResultado r = buscarComCursor("137", 5, 5, "10");

        assertThat(r.desfecho()).isEqualTo(new Desfecho.IndisponivelDefinitivo("cursor inconsistente"));
        assertNaoAvancou(r, "10");
    }

    /** BlobStore que lança na n-ésima gravação (caos). */
    private static final class BlobStoreQueFalha implements BlobStore {
        private final BlobStore alvo;
        private final int falharNa;
        private int gravacoes;

        BlobStoreQueFalha(BlobStore alvo, int falharNa) {
            this.alvo = alvo;
            this.falharNa = falharNa;
        }

        @Override
        public BlobRef gravar(String chave, byte[] conteudo, String sha256) {
            if (++gravacoes == falharNa) {
                throw new IllegalStateException("blob store fora do ar");
            }
            return alvo.gravar(chave, conteudo, sha256);
        }

        @Override
        public Optional<byte[]> ler(BlobRef ref) {
            return alvo.ler(ref);
        }
    }

    @Test
    @DisplayName("inv. 5 (caos): BlobStore falhando no 1º docZip não devolve documento nem avança")
    void falhaDoBlobStoreNaoDevolveDocumentoNemAvanca() {
        responder(resposta("138", 13, 13,
                docZip(11, "resNFe_v1.01.xsd", XML_RES) + docZip(12, "procNFe_v4.00.xsd", XML_PROC)));

        BuscarLoteResultado r = adapterCom(new BlobStoreQueFalha(blobs, 2))
                .buscarLote(new ContextoConsulta(FONTE, CNPJ), new Cursor("10"));

        assertThat(r.desfecho()).isEqualTo(TEMPORARIO);
        assertNaoAvancou(r, "10");
    }

    @Test
    @DisplayName("inv. 5 (caos): BlobStore falhando no envelope não lê o corpo nem avança")
    void falhaDoBlobStoreNoEnvelopeNaoAvanca() {
        responder(resposta("138", 13, 13, docZip(11, "resNFe_v1.01.xsd", XML_RES)));

        BuscarLoteResultado r = adapterCom(new BlobStoreQueFalha(blobs, 1))
                .buscarLote(new ContextoConsulta(FONTE, CNPJ), new Cursor("10"));

        assertThat(r.desfecho()).isEqualTo(TEMPORARIO);
        assertSemAvanco(r, "10");
        assertThat(r.envelope()).isEmpty();
    }

    @Test
    @DisplayName("buscarLote antes de autenticar: ErroAutenticacao, sem chamada HTTP e sem avançar")
    void buscarLoteAntesDeAutenticarEhErroAutenticacao() {
        URI endpoint = URI.create("http://localhost:" + servidor.port() + CAMINHO);
        NfeNacionalAdapter semCredencial = new NfeNacionalAdapter(FONTE, TipoAmbiente.PRODUCAO, endpoint, blobs,
                Clock.fixed(AGORA, ZoneOffset.UTC),
                credencial -> new TransporteHttpJdk(HttpClient.newHttpClient(), Duration.ofSeconds(5), 1024));

        BuscarLoteResultado r = semCredencial.buscarLote(new ContextoConsulta(FONTE, CNPJ), new Cursor("10"));

        assertThat(r.desfecho()).isInstanceOf(Desfecho.ErroAutenticacao.class);
        assertSemAvanco(r, "10");
        assertThat(r.envelope()).isEmpty();
        assertThat(servidor.getAllServeEvents()).isEmpty();
    }

    @Test
    @DisplayName("descritor: lote e pontual suportados")
    void descritorComLoteEPontual() {
        assertThat(adapter.descritor().fonteId()).isEqualTo(FONTE);
        assertThat(adapter.descritor().capacidades()).isEqualTo(new Capacidades(true, true, false, false));
    }

    @Test
    @DisplayName("buscarUm 138: devolve o documento do NSU, blob com a chave do lote e consNSU sem distNSU")
    void buscarUm138DevolveODocumentoDoNsu() {
        responder(resposta("138", 42, 42, docZip(42, "procNFe_v4.00.xsd", XML_PROC)));

        BuscarUmResultado r = adapter.buscarUm(FONTE, CNPJ, new Nsu(42));

        assertThat(r.desfecho()).isEqualTo(new Desfecho.Ok());
        Documento doc = r.documento().orElseThrow();
        assertThat(doc.nsu()).isEqualTo(new Nsu(42));
        assertThat(doc.tipoConteudo()).isEqualTo("PROC_NFE");
        assertThat(doc.id()).isEqualTo(idEsperado(42));
        String sha = Sha256.hex(XML_PROC.getBytes(StandardCharsets.UTF_8));
        String chave = ChavesBlobPull.documento(FONTE, TipoAmbiente.PRODUCAO, CNPJ, new Nsu(42), sha);
        assertThat(doc.blobRef()).contains(chave);
        assertThat(blobs.ler(new BlobRef(chave, sha))).contains(XML_PROC.getBytes(StandardCharsets.UTF_8));

        var requisicoes = servidor.findAll(com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor(urlEqualTo(CAMINHO)));
        assertThat(requisicoes).hasSize(1);
        String corpo = requisicoes.get(0).getBodyAsString();
        assertThat(corpo).contains("<consNSU><NSU>000000000000042</NSU></consNSU>")
                .doesNotContain("distNSU").doesNotContain("ultNSU");
    }

    @Test
    @DisplayName("buscarUm usa a mesma chave de blob do buscarLote (inv. 7)")
    void buscarUmUsaAMesmaChaveDoLote() {
        responder(resposta("138", 42, 42, docZip(42, "procNFe_v4.00.xsd", XML_PROC)));

        BuscarLoteResultado lote = adapter.buscarLote(new ContextoConsulta(FONTE, CNPJ), new Cursor("41"));
        BuscarUmResultado um = adapter.buscarUm(FONTE, CNPJ, new Nsu(42));

        assertThat(um.desfecho()).isEqualTo(new Desfecho.Ok());
        assertThat(um.documento().orElseThrow().blobRef()).isEqualTo(lote.documentos().get(0).blobRef());
        assertThat(um.documento().orElseThrow().id()).isEqualTo(lote.documentos().get(0).id());
    }

    // ---- buscarUm: desfechos do consNSU ----

    private BuscarUmResultado umComCStat(String cStat) {
        responder(resposta(cStat, 42, 42, ""));
        return adapter.buscarUm(FONTE, CNPJ, new Nsu(42));
    }

    private void assertSemDocumento(BuscarUmResultado r, Desfecho esperado) {
        assertThat(r.documento()).isEmpty();
        assertThat(r.desfecho()).isEqualTo(esperado);
    }

    @Test
    @DisplayName("consNSU 137: Vazio sem documento")
    void umVazio137() {
        assertSemDocumento(umComCStat("137"), new Desfecho.Vazio());
    }

    @Test
    @DisplayName("consNSU 589: IndisponivelDefinitivo")
    void um589() {
        BuscarUmResultado r = umComCStat("589");
        assertThat(r.documento()).isEmpty();
        assertThat(r.desfecho()).isInstanceOf(Desfecho.IndisponivelDefinitivo.class);
    }

    @Test
    @DisplayName("consNSU 656: LimiteAtingido")
    void um656() {
        assertSemDocumento(umComCStat("656"), new Desfecho.LimiteAtingido(Duration.ofHours(1)));
    }

    @Test
    @DisplayName("consNSU 108: IndisponivelTemporario")
    void um108() {
        assertSemDocumento(umComCStat("108"), TEMPORARIO);
    }

    @Test
    @DisplayName("consNSU 109: ServicoFora")
    void um109() {
        BuscarUmResultado r = umComCStat("109");
        assertThat(r.documento()).isEmpty();
        assertThat(r.desfecho()).isInstanceOf(Desfecho.ServicoFora.class);
    }

    @Test
    @DisplayName("consNSU: timeout, 500, 403, malformado e docZip corrompido, sem documento (DA-14)")
    void umFalhasDeTransporteELeitura() {
        servidor.stubFor(post(urlEqualTo(CAMINHO)).willReturn(aResponse().withStatus(200)
                .withFixedDelay(2_000).withBody(resposta("138", 42, 42, docZip(42, "resNFe_v1.01.xsd", XML_RES)))));
        assertSemDocumento(adapterCom(blobs, Duration.ofMillis(200)).buscarUm(FONTE, CNPJ, new Nsu(42)), TEMPORARIO);

        responderStatus(500, "<soap:Envelope xmlns:soap=\"" + ContratoDistDfe.NS_SOAP12 + "\"><soap:Body/></soap:Envelope>");
        assertSemDocumento(adapter.buscarUm(FONTE, CNPJ, new Nsu(42)), TEMPORARIO);

        responderStatus(403, "proibido");
        BuscarUmResultado r403 = adapter.buscarUm(FONTE, CNPJ, new Nsu(42));
        assertThat(r403.documento()).isEmpty();
        assertThat(r403.desfecho()).isInstanceOf(Desfecho.ErroAutenticacao.class);

        responder("<soap:Envelope SEGREDO-XYZ <<< nada disso é XML");
        BuscarUmResultado malformado = adapter.buscarUm(FONTE, CNPJ, new Nsu(42));
        assertSemDocumento(malformado, TEMPORARIO);
        assertThat(malformado.toString()).doesNotContain("SEGREDO-XYZ");

        responder(resposta("138", 42, 42,
                "<docZip NSU=\"000000000000042\" schema=\"procNFe_v4.00.xsd\">@@@nao-eh-base64@@@</docZip>"));
        assertSemDocumento(adapter.buscarUm(FONTE, CNPJ, new Nsu(42)), TEMPORARIO);
    }

    @Test
    @DisplayName("DA-21: 138 sem docZip, com NSU diferente do pedido ou com mais de um docZip")
    void umRegrasDaDa21() {
        responder(resposta("138", 42, 42, ""));
        assertSemDocumento(adapter.buscarUm(FONTE, CNPJ, new Nsu(42)), TEMPORARIO);

        responder(resposta("138", 43, 43, docZip(43, "procNFe_v4.00.xsd", XML_PROC)));
        assertSemDocumento(adapter.buscarUm(FONTE, CNPJ, new Nsu(42)), TEMPORARIO);

        responder(resposta("138", 43, 43,
                docZip(42, "procNFe_v4.00.xsd", XML_PROC) + docZip(43, "resNFe_v1.01.xsd", XML_RES)));
        assertSemDocumento(adapter.buscarUm(FONTE, CNPJ, new Nsu(42)), TEMPORARIO);
    }

    @Test
    @DisplayName("buscarUm antes de autenticar: ErroAutenticacao sem chamada HTTP")
    void buscarUmAntesDeAutenticarEhErroAutenticacao() {
        URI endpoint = URI.create("http://localhost:" + servidor.port() + CAMINHO);
        NfeNacionalAdapter semCredencial = new NfeNacionalAdapter(FONTE, TipoAmbiente.PRODUCAO, endpoint, blobs,
                Clock.fixed(AGORA, ZoneOffset.UTC),
                credencial -> new TransporteHttpJdk(HttpClient.newHttpClient(), Duration.ofSeconds(5), 1024));

        BuscarUmResultado r = semCredencial.buscarUm(FONTE, CNPJ, new Nsu(42));

        assertThat(r.documento()).isEmpty();
        assertThat(r.desfecho()).isInstanceOf(Desfecho.ErroAutenticacao.class);
        assertThat(servidor.getAllServeEvents()).isEmpty();
    }

    @Test
    @DisplayName("buscarUm com fonte diferente da do adapter é IllegalArgumentException")
    void buscarUmComOutraFonte() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> adapter.buscarUm(new FonteId("OUTRA"), CNPJ, new Nsu(42)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("inv. 5: buscarUm grava o envelope antes do docZip")
    void buscarUmGravaEnvelopeAntesDoParse() {
        responder(resposta("138", 42, 42, docZip(42, "procNFe_v4.00.xsd", XML_PROC)));
        var espiao = new BlobStoreEspiao(blobs);

        adapterCom(espiao).buscarUm(FONTE, CNPJ, new Nsu(42));

        assertThat(espiao.chavesGravadas).hasSize(2);
        assertThat(espiao.chavesGravadas.get(0)).contains("envelope");
    }

    @Test
    @DisplayName("inv. 5 (caos): falha do BlobStore no envelope ou no docZip não devolve documento")
    void falhaDoBlobStoreNoBuscarUmNaoDevolveDocumento() {
        responder(resposta("138", 42, 42, docZip(42, "procNFe_v4.00.xsd", XML_PROC)));

        assertSemDocumento(adapterCom(new BlobStoreQueFalha(blobs, 1)).buscarUm(FONTE, CNPJ, new Nsu(42)), TEMPORARIO);
        assertSemDocumento(adapterCom(new BlobStoreQueFalha(blobs, 2)).buscarUm(FONTE, CNPJ, new Nsu(42)), TEMPORARIO);
    }

    // ---- buscarPorChave (consChNFe) ----

    private static final String CHAVE = "35261011111111000191550010000000011000000015";

    @Test
    @DisplayName("buscarPorChave 138: devolve o procNFe com a chave pedida e consChNFe sem distNSU")
    void buscarPorChave138DevolveProcNFe() {
        responder(resposta("138", 42, 42, docZip(42, "procNFe_v4.00.xsd", XML_PROC)));

        BuscarUmResultado r = adapter.buscarPorChave(new ContextoConsulta(FONTE, CNPJ), new ChaveAcessoNfe(CHAVE));

        assertThat(r.desfecho()).isEqualTo(new Desfecho.Ok());
        Documento doc = r.documento().orElseThrow();
        assertThat(doc.chave()).contains(CHAVE);
        assertThat(doc.nsu()).isEqualTo(new Nsu(42));
        String sha = Sha256.hex(XML_PROC.getBytes(StandardCharsets.UTF_8));
        String chaveBlob = ChavesBlobPull.documento(FONTE, TipoAmbiente.PRODUCAO, CNPJ, new Nsu(42), sha);
        assertThat(doc.blobRef()).contains(chaveBlob);
        assertThat(blobs.ler(new BlobRef(chaveBlob, sha))).contains(XML_PROC.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("consChNFe nunca envia distNSU/ultNSU (inv. 1)")
    void consChNFeNuncaEnviaDistNsu() {
        responder(resposta("138", 42, 42, docZip(42, "procNFe_v4.00.xsd", XML_PROC)));

        adapter.buscarPorChave(new ContextoConsulta(FONTE, CNPJ), new ChaveAcessoNfe(CHAVE));

        var requisicoes = servidor.findAll(com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor(urlEqualTo(CAMINHO)));
        assertThat(requisicoes).hasSize(1);
        assertThat(requisicoes.get(0).getBodyAsString())
                .contains("<consChNFe><chNFe>" + CHAVE + "</chNFe></consChNFe>")
                .doesNotContain("distNSU").doesNotContain("ultNSU");
    }

    private BuscarUmResultado chaveComCStat(String cStat) {
        responder(resposta(cStat, 42, 42, ""));
        return adapter.buscarPorChave(new ContextoConsulta(FONTE, CNPJ), new ChaveAcessoNfe(CHAVE));
    }

    @Test
    @DisplayName("consChNFe 632: IndisponivelDefinitivo (fora de prazo), não LimiteAtingido")
    void chave632() {
        BuscarUmResultado r = chaveComCStat("632");
        assertThat(r.documento()).isEmpty();
        assertThat(r.desfecho()).isInstanceOf(Desfecho.IndisponivelDefinitivo.class);
    }

    @Test
    @DisplayName("consChNFe 137 e 656")
    void chave137e656() {
        assertSemDocumento(chaveComCStat("137"), new Desfecho.Vazio());
        assertSemDocumento(chaveComCStat("656"), new Desfecho.LimiteAtingido(Duration.ofHours(1)));
    }

    @Test
    @DisplayName("consChNFe: 138 sem docZip ou com mais de um é IndisponivelTemporario (DA-21)")
    void chaveRegrasDaDa21() {
        assertSemDocumento(chaveComCStat("138"), TEMPORARIO);

        responder(resposta("138", 43, 43,
                docZip(42, "procNFe_v4.00.xsd", XML_PROC) + docZip(43, "resNFe_v1.01.xsd", XML_RES)));
        assertSemDocumento(adapter.buscarPorChave(new ContextoConsulta(FONTE, CNPJ), new ChaveAcessoNfe(CHAVE)), TEMPORARIO);
    }

    @Test
    @DisplayName("consChNFe: timeout, 5xx e malformado sem documento e sem vazar conteúdo (DA-14)")
    void chaveFalhasDeTransporte() {
        var ctx = new ContextoConsulta(FONTE, CNPJ);
        var chave = new ChaveAcessoNfe(CHAVE);
        servidor.stubFor(post(urlEqualTo(CAMINHO)).willReturn(aResponse().withStatus(200)
                .withFixedDelay(2_000).withBody(resposta("138", 42, 42, docZip(42, "resNFe_v1.01.xsd", XML_RES)))));
        assertSemDocumento(adapterCom(blobs, Duration.ofMillis(200)).buscarPorChave(ctx, chave), TEMPORARIO);

        responderStatus(500, "<soap:Envelope xmlns:soap=\"" + ContratoDistDfe.NS_SOAP12 + "\"><soap:Body/></soap:Envelope>");
        assertSemDocumento(adapter.buscarPorChave(ctx, chave), TEMPORARIO);

        responder("<soap:Envelope SEGREDO-XYZ <<< nada disso é XML");
        BuscarUmResultado malformado = adapter.buscarPorChave(ctx, chave);
        assertSemDocumento(malformado, TEMPORARIO);
        assertThat(malformado.toString()).doesNotContain("SEGREDO-XYZ");
    }

    @Test
    @DisplayName("buscarPorChave antes de autenticar: ErroAutenticacao sem chamada HTTP")
    void buscarPorChaveAntesDeAutenticar() {
        URI endpoint = URI.create("http://localhost:" + servidor.port() + CAMINHO);
        NfeNacionalAdapter semCredencial = new NfeNacionalAdapter(FONTE, TipoAmbiente.PRODUCAO, endpoint, blobs,
                Clock.fixed(AGORA, ZoneOffset.UTC),
                credencial -> new TransporteHttpJdk(HttpClient.newHttpClient(), Duration.ofSeconds(5), 1024));

        BuscarUmResultado r = semCredencial.buscarPorChave(new ContextoConsulta(FONTE, CNPJ), new ChaveAcessoNfe(CHAVE));

        assertThat(r.documento()).isEmpty();
        assertThat(r.desfecho()).isInstanceOf(Desfecho.ErroAutenticacao.class);
        assertThat(servidor.getAllServeEvents()).isEmpty();
    }

    @Test
    @DisplayName("inv. 5: buscarPorChave grava o envelope antes do docZip e falha de blob não devolve documento")
    void buscarPorChaveGravaEnvelopePrimeiro() {
        responder(resposta("138", 42, 42, docZip(42, "procNFe_v4.00.xsd", XML_PROC)));
        var espiao = new BlobStoreEspiao(blobs);
        var ctx = new ContextoConsulta(FONTE, CNPJ);
        var chave = new ChaveAcessoNfe(CHAVE);

        adapterCom(espiao).buscarPorChave(ctx, chave);
        assertThat(espiao.chavesGravadas).hasSize(2);
        assertThat(espiao.chavesGravadas.get(0)).contains("envelope");

        assertSemDocumento(adapterCom(new BlobStoreQueFalha(blobs, 1)).buscarPorChave(ctx, chave), TEMPORARIO);
        assertSemDocumento(adapterCom(new BlobStoreQueFalha(blobs, 2)).buscarPorChave(ctx, chave), TEMPORARIO);
    }
}
