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
}
