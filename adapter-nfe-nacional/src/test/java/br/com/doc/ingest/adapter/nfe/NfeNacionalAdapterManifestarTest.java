package br.com.doc.ingest.adapter.nfe;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.doc.ingest.adapter.fake.BlobStoreEmMemoria;
import br.com.doc.ingest.adapter.nfe.cert.CarregadorPfx;
import br.com.doc.ingest.adapter.nfe.cert.GeradorPfxDeTeste;
import br.com.doc.ingest.adapter.nfe.evento.ContratoEvento;
import br.com.doc.ingest.adapter.nfe.evento.ContratoRecepcaoEvento;
import br.com.doc.ingest.adapter.nfe.evento.FabricaDocumentoSeguro;
import br.com.doc.ingest.adapter.nfe.soap.TipoAmbiente;
import br.com.doc.ingest.adapter.nfe.transporte.FornecedorTransporte;
import br.com.doc.ingest.adapter.nfe.transporte.TransporteHttpJdk;
import br.com.doc.ingest.domain.adapter.Capacidades;
import br.com.doc.ingest.domain.adapter.ContextoConsulta;
import br.com.doc.ingest.domain.adapter.Credencial;
import br.com.doc.ingest.domain.adapter.Desfecho;
import br.com.doc.ingest.domain.adapter.ManifestarResultado;
import br.com.doc.ingest.domain.adapter.TipoManifestacao;
import br.com.doc.ingest.domain.blob.BlobRef;
import br.com.doc.ingest.domain.blob.BlobStore;
import br.com.doc.ingest.domain.blob.Sha256;
import br.com.doc.ingest.domain.model.Cnpj;
import br.com.doc.ingest.domain.model.FonteId;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.github.tomakehurst.wiremock.WireMockServer;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMValidateContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/** manifestar contra WireMock em localhost (inv. 14). Certificado e fixtures sintéticos. */
@DisplayName("NfeNacionalAdapter.manifestar com WireMock")
class NfeNacionalAdapterManifestarTest {

    private static final String CAMINHO_DIST = "/ws/distdfe";
    private static final String CAMINHO_EVENTO = "/ws/recepcaoevento";
    private static final FonteId FONTE = new FonteId("NFE_AN");
    private static final Cnpj CNPJ = new Cnpj("11111111000191");
    private static final String CHAVE = "35261012345678000195550010000001231000000014";
    private static final Instant AGORA = Instant.parse("2026-11-04T11:00:00Z");
    private static final String NFE_NS = "http://www.portalfiscal.inf.br/nfe";
    private static final String SOAP12 = "http://www.w3.org/2003/05/soap-envelope";
    private static final Desfecho TEMPORARIO = new Desfecho.IndisponivelTemporario(Duration.ofMinutes(5));
    private static final String SENHA = "senha-de-teste";
    private static final String XMOTIVO = "Motivo sensivel do CPF 12345678909";
    private static final String N_PROT = "891260000000001";
    private static final String OUTRA_CHAVE = "35261012345678000195550010000001241000000012";

    private static Credencial credencial;
    private static PublicKey chavePublica;
    private static String chavePrivadaBase64;

    private WireMockServer servidor;
    private BlobStoreEmMemoria blobs;

    @BeforeAll
    static void gerarCertificado() {
        char[] senha = SENHA.toCharArray();
        byte[] pfx = GeradorPfxDeTeste.moderno(senha.clone(), AGORA.minus(1, ChronoUnit.DAYS),
                AGORA.plus(365, ChronoUnit.DAYS));
        credencial = new Credencial(pfx, senha);
        var carregado = new CarregadorPfx().carregar(credencial);
        chavePublica = carregado.cadeia()[0].getPublicKey();
        chavePrivadaBase64 = Base64.getEncoder().encodeToString(carregado.chavePrivada().getEncoded());
    }

    @BeforeEach
    void subir() {
        servidor = new WireMockServer(options().dynamicPort());
        servidor.start();
        blobs = new BlobStoreEmMemoria();
    }

    @AfterEach
    void parar() {
        servidor.stop();
    }

    private NfeNacionalAdapter adapterCom(BlobStore store) {
        return adapterCom(store, Duration.ofSeconds(5));
    }

    private NfeNacionalAdapter adapterCom(BlobStore store, Duration timeout) {
        NfeNacionalAdapter novo = new NfeNacionalAdapter(FONTE, TipoAmbiente.HOMOLOGACAO, url(CAMINHO_DIST), store,
                Clock.fixed(AGORA, ZoneOffset.UTC), transporteHttp(timeout), url(CAMINHO_EVENTO));
        novo.autenticar(credencial);
        return novo;
    }

    private URI url(String caminho) {
        return URI.create("http://localhost:" + servidor.port() + caminho);
    }

    private static FornecedorTransporte transporteHttp() {
        return transporteHttp(Duration.ofSeconds(5));
    }

    private static FornecedorTransporte transporteHttp(Duration timeout) {
        return c -> new TransporteHttpJdk(HttpClient.newHttpClient(), timeout, 1024 * 1024);
    }

    private ManifestarResultado manifestar(NfeNacionalAdapter adapter) {
        return adapter.manifestar(new ContextoConsulta(FONTE, CNPJ), CHAVE, TipoManifestacao.CIENCIA).orElseThrow();
    }

    private void responder(int status, String corpo) {
        servidor.stubFor(post(urlEqualTo(CAMINHO_EVENTO)).willReturn(aResponse().withStatus(status)
                .withHeader("Content-Type", "application/soap+xml; charset=utf-8").withBody(corpo)));
    }

    private static String resposta(String cStatEvento) {
        return respostaDeLote("128", retEvento(cStatEvento, CHAVE));
    }

    private static String respostaDeLote(String cStatLote, String retEvento) {
        return envelopeSoap("<nfeResultMsg xmlns=\"" + ContratoRecepcaoEvento.NS_WSDL + "\">"
                + "<retEnvEvento versao=\"1.00\" xmlns=\"" + NFE_NS + "\"><idLote>1</idLote><tpAmb>2</tpAmb>"
                + "<verAplic>AN_1.0</verAplic><cOrgao>91</cOrgao><cStat>" + cStatLote + "</cStat>"
                + "<xMotivo>" + XMOTIVO + "</xMotivo>" + retEvento + "</retEnvEvento></nfeResultMsg>");
    }

    private static String retEvento(String cStat, String chave) {
        return "<retEvento versao=\"1.00\"><infEvento><tpAmb>2</tpAmb><verAplic>AN_1.0</verAplic><cOrgao>91</cOrgao>"
                + "<cStat>" + cStat + "</cStat><xMotivo>" + XMOTIVO + "</xMotivo><chNFe>" + chave + "</chNFe>"
                + "<tpEvento>210210</tpEvento><nSeqEvento>1</nSeqEvento>"
                + "<nProt>" + N_PROT + "</nProt></infEvento></retEvento>";
    }

    private static String soapFault() {
        return envelopeSoap("<soap:Fault><soap:Code><soap:Value>soap:Sender</soap:Value></soap:Code>"
                + "<soap:Reason><soap:Text xml:lang=\"pt\">" + XMOTIVO + "</soap:Text></soap:Reason></soap:Fault>");
    }

    private static String envelopeSoap(String corpo) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<soap:Envelope xmlns:soap=\"" + SOAP12 + "\"><soap:Body>" + corpo + "</soap:Body></soap:Envelope>";
    }

    private static String textoDe(Element pai, String nome) {
        return pai.getElementsByTagNameNS(ContratoEvento.NS_NFE, nome).item(0).getTextContent();
    }

    /** BlobStore que falha em toda gravação. */
    private static final class BlobStoreQueFalha implements BlobStore {
        @Override
        public BlobRef gravar(String chave, byte[] conteudo, String sha256) {
            throw new IllegalStateException("blob store fora do ar");
        }

        @Override
        public Optional<byte[]> ler(BlobRef ref) {
            return Optional.empty();
        }
    }

    @Test
    @DisplayName("ciência com 135: Ok e referência da resposta bruta guardada")
    void ciencia135DevolveOkComEnvelope() {
        String corpo = resposta("135");
        responder(200, corpo);

        ManifestarResultado r = manifestar(adapterCom(blobs));

        assertThat(r.desfecho()).isEqualTo(new Desfecho.Ok());
        byte[] bytes = corpo.getBytes(StandardCharsets.UTF_8);
        String sha = Sha256.hex(bytes);
        assertThat(r.envelope()).contains(new BlobRef(
                "raw/pull/NFE_AN/HOMOLOGACAO/11111111000191/evento/2026/11/04/" + sha + ".retorno.xml", sha));
        assertThat(blobs.ler(r.envelope().orElseThrow())).hasValueSatisfying(b -> assertThat(b).isEqualTo(bytes));
    }

    @Test
    @DisplayName("a requisição vai ao endpoint do evento, com a action e o evento assinado válido do CNPJ do contexto")
    void requisicaoTemActionEEventoAssinadoValido() throws Exception {
        responder(200, resposta("135"));

        manifestar(adapterCom(blobs));

        servidor.verify(1, postRequestedFor(urlEqualTo(CAMINHO_EVENTO)));
        servidor.verify(0, postRequestedFor(urlEqualTo(CAMINHO_DIST)));
        var requisicao = servidor.getAllServeEvents().get(0).getRequest();
        assertThat(requisicao.getHeader("Content-Type")).contains("action=\"" + ContratoRecepcaoEvento.ACTION + "\"");

        Document doc = FabricaDocumentoSeguro.ler(requisicao.getBody());
        Element lote = (Element) doc.getElementsByTagNameNS(ContratoEvento.NS_NFE, "envEvento").item(0);
        assertThat(textoDe(lote, "idLote")).isEqualTo(String.format("%015d", AGORA.toEpochMilli()));
        assertThat(textoDe(lote, "tpAmb")).isEqualTo("2");
        assertThat(textoDe(lote, "CNPJ")).isEqualTo(CNPJ.valor());
        assertThat(textoDe(lote, "chNFe")).isEqualTo(CHAVE);
        assertThat(textoDe(lote, "tpEvento")).isEqualTo("210210");
        assertThat(textoDe(lote, "nSeqEvento")).isEqualTo("1");
        assertThat(textoDe(lote, "dhEvento")).isEqualTo("2026-11-04T08:00:00-03:00");

        Element infEvento = (Element) doc.getElementsByTagNameNS(ContratoEvento.NS_NFE, "infEvento").item(0);
        infEvento.setIdAttributeNS(null, "Id", true);
        Node assinatura = doc.getElementsByTagNameNS(XMLSignature.XMLNS, "Signature").item(0);
        DOMValidateContext ctx = new DOMValidateContext(chavePublica, assinatura);
        ctx.setProperty("org.jcp.xml.dsig.secureValidation", Boolean.FALSE);
        assertThat(XMLSignatureFactory.getInstance("DOM").unmarshalXMLSignature(ctx).validate(ctx)).isTrue();
    }

    @ParameterizedTest
    @CsvSource({"200,nao e xml", "500,erro interno", "200,''"})
    @DisplayName("inv. 5: resposta malformada, 5xx ou vazia fica guardada, byte a byte, e o desfecho é temporário")
    void gravaRespostaBrutaAntesDeInterpretar(int status, String corpo) {
        responder(status, corpo);

        ManifestarResultado r = manifestar(adapterCom(blobs));

        assertThat(r.desfecho()).isEqualTo(TEMPORARIO);
        assertThat(r.envelope()).isPresent();
        assertThat(blobs.ler(r.envelope().orElseThrow()))
                .hasValueSatisfying(b -> assertThat(b).isEqualTo(corpo.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("inv. 5 (caos): BlobStore falhando não vira Ok, mesmo com 135 na resposta")
    void falhaDoBlobStoreNaoViraOk() {
        responder(200, resposta("135"));

        ManifestarResultado r = manifestar(adapterCom(new BlobStoreQueFalha()));

        assertThat(r.desfecho()).isEqualTo(TEMPORARIO);
        assertThat(r.envelope()).isEmpty();
    }

    @Test
    @DisplayName("com endpoint de evento, o descritor declara a manifestação")
    void comEndpointDeEventoDeclaraManifestacao() {
        assertThat(adapterCom(blobs).descritor().capacidades()).isEqualTo(new Capacidades(true, false, true, false));
    }

    @Test
    @DisplayName("sem endpoint de evento, o adapter não manifesta nem abre o .pfx")
    void semEndpointDeEventoNaoManifesta() {
        NfeNacionalAdapter semEvento = new NfeNacionalAdapter(FONTE, TipoAmbiente.HOMOLOGACAO, url(CAMINHO_DIST), blobs,
                Clock.fixed(AGORA, ZoneOffset.UTC), transporteHttp());
        semEvento.autenticar(new Credencial(new byte[] {1}, new char[0]));

        assertThat(semEvento.manifestar(new ContextoConsulta(FONTE, CNPJ), CHAVE, TipoManifestacao.CIENCIA)).isEmpty();
        assertThat(semEvento.descritor().capacidades().fazManifestacao()).isFalse();
        servidor.verify(0, postRequestedFor(urlEqualTo(CAMINHO_EVENTO)));
    }

    @Test
    @DisplayName("sem autenticar: ErroAutenticacao, sem HTTP")
    void semAutenticarEhErroAutenticacaoSemHttp() {
        responder(200, resposta("135"));
        NfeNacionalAdapter semCredencial = new NfeNacionalAdapter(FONTE, TipoAmbiente.HOMOLOGACAO, url(CAMINHO_DIST), blobs,
                Clock.fixed(AGORA, ZoneOffset.UTC), transporteHttp(), url(CAMINHO_EVENTO));

        ManifestarResultado r = manifestar(semCredencial);

        assertThat(r.desfecho()).isEqualTo(new Desfecho.ErroAutenticacao("adapter não autenticado"));
        assertThat(r.envelope()).isEmpty();
        servidor.verify(0, anyRequestedFor(anyUrl()));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "35261012345678000195550010000001231000000015", // DV errado (o certo é 4)
            "3526101234567800019555001000000123100000001", // 43 dígitos
            "3526101234567800019555001000000123100000001A"})
    @DisplayName("chave com DV ou formato inválido: IndisponivelDefinitivo com motivo fixo, sem HTTP")
    void chaveComDvInvalidoEhDefinitivoSemHttp(String chave) {
        responder(200, resposta("135"));

        ManifestarResultado r = adapterCom(blobs)
                .manifestar(new ContextoConsulta(FONTE, CNPJ), chave, TipoManifestacao.CIENCIA).orElseThrow();

        assertThat(r.desfecho()).isEqualTo(new Desfecho.IndisponivelDefinitivo("chave de acesso inválida"));
        assertThat(r.envelope()).isEmpty();
        servidor.verify(0, anyRequestedFor(anyUrl()));
    }

    @ParameterizedTest
    @EnumSource(value = TipoManifestacao.class, names = "CIENCIA", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("tipo ainda não suportado: IndisponivelDefinitivo com motivo fixo, sem HTTP")
    void tipoAindaNaoSuportadoEhDefinitivoSemHttp(TipoManifestacao tipo) {
        responder(200, resposta("135"));

        ManifestarResultado r = adapterCom(blobs).manifestar(new ContextoConsulta(FONTE, CNPJ), CHAVE, tipo).orElseThrow();

        assertThat(r.desfecho()).isEqualTo(new Desfecho.IndisponivelDefinitivo("tipo de manifestação não suportado"));
        assertThat(r.envelope()).isEmpty();
        servidor.verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    @DisplayName("inv. 4: falha ao assinar vira IndisponivelDefinitivo com motivo fixo, sem exceção e sem HTTP")
    void falhaAoAssinarEhDefinitivoSemHttp() {
        responder(200, resposta("135"));
        char[] senha = SENHA.toCharArray();
        byte[] pfxEc = GeradorPfxDeTeste.comChaveEc(senha.clone(), AGORA.minus(1, ChronoUnit.DAYS),
                AGORA.plus(365, ChronoUnit.DAYS));
        NfeNacionalAdapter adapter = new NfeNacionalAdapter(FONTE, TipoAmbiente.HOMOLOGACAO, url(CAMINHO_DIST), blobs,
                Clock.fixed(AGORA, ZoneOffset.UTC), transporteHttp(), url(CAMINHO_EVENTO));
        adapter.autenticar(new Credencial(pfxEc, senha));

        ManifestarResultado r = manifestar(adapter);

        assertThat(r.desfecho()).isEqualTo(new Desfecho.IndisponivelDefinitivo("falha ao assinar o evento"));
        assertThat(r.envelope()).isEmpty();
        servidor.verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    @DisplayName("o autor do evento e a chave do blob usam o CNPJ do contexto")
    void autorEhOCnpjDoContexto() {
        Cnpj outro = new Cnpj("22222222000191");
        responder(200, resposta("135"));

        ManifestarResultado r = adapterCom(blobs)
                .manifestar(new ContextoConsulta(FONTE, outro), CHAVE, TipoManifestacao.CIENCIA).orElseThrow();

        Document doc = FabricaDocumentoSeguro.ler(servidor.getAllServeEvents().get(0).getRequest().getBody());
        Element lote = (Element) doc.getElementsByTagNameNS(ContratoEvento.NS_NFE, "envEvento").item(0);
        assertThat(textoDe(lote, "CNPJ")).isEqualTo(outro.valor());
        assertThat(r.envelope().orElseThrow().chave()).contains("/" + outro.valor() + "/");
    }

    /** Os códigos de D1 dentro de respostas HTTP de verdade: mapper, tradutor e leitor juntos, com o texto exato do desfecho. */
    static Stream<Arguments> tabelaDeCStat() {
        Duration hora = Duration.ofHours(1);
        return Stream.of(
                evento("135", new Desfecho.Ok()),
                evento("136", new Desfecho.Ok()),
                evento("573", new Desfecho.Ok()),
                eventoRejeitado("650"),
                eventoRejeitado("655"),
                eventoRejeitado("596"),
                eventoRejeitado("217"),
                eventoRejeitado("236"),
                eventoRejeitado("614"),
                eventoRejeitado("615"),
                eventoRejeitado("616"),
                eventoRejeitado("617"),
                eventoRejeitado("618"),
                eventoRejeitado("619"),
                evento("575", new Desfecho.IndisponivelDefinitivo("Autor do evento diverge do destinatário (cStat 575)")),
                evento("213", new Desfecho.ErroAutenticacao("Autor do evento diverge do certificado (cStat 213)")),
                evento("297", new Desfecho.IndisponivelDefinitivo("Assinatura do evento rejeitada (cStat 297)")),
                evento("298", new Desfecho.IndisponivelDefinitivo("Assinatura do evento rejeitada (cStat 298)")),
                evento("999", TEMPORARIO),
                lote("108", TEMPORARIO),
                lote("109", new Desfecho.ServicoFora("Serviço paralisado sem previsão (cStat 109)")),
                lote("656", new Desfecho.LimiteAtingido(hora)),
                loteRejeitado("215"),
                loteRejeitado("225"),
                loteRejeitado("243"),
                loteRejeitado("252"),
                lote("999", TEMPORARIO),
                // sucesso do evento não vale no lote, mesmo com um retEvento 135 junto
                Arguments.of("lote 135 com retEvento 135", respostaDeLote("135", retEvento("135", CHAVE)), TEMPORARIO),
                // lote rejeitado manda, mesmo com um retEvento 135 junto
                Arguments.of("lote 215 com retEvento 135", respostaDeLote("215", retEvento("135", CHAVE)),
                        new Desfecho.IndisponivelDefinitivo("Lote de evento rejeitado (cStat 215)")));
    }

    private static Arguments evento(String cStat, Desfecho esperado) {
        return Arguments.of("evento " + cStat, resposta(cStat), esperado);
    }

    private static Arguments eventoRejeitado(String cStat) {
        return evento(cStat, new Desfecho.IndisponivelDefinitivo("Evento rejeitado (cStat " + cStat + ")"));
    }

    private static Arguments lote(String cStat, Desfecho esperado) {
        return Arguments.of("lote " + cStat, respostaDeLote(cStat, ""), esperado);
    }

    private static Arguments loteRejeitado(String cStat) {
        return lote(cStat, new Desfecho.IndisponivelDefinitivo("Lote de evento rejeitado (cStat " + cStat + ")"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("tabelaDeCStat")
    @DisplayName("tabela de D1 ponta a ponta: cada resposta vira o desfecho decidido, com o bruto guardado")
    void tabelaDeCStatPontaAPonta(String caso, String corpo, Desfecho esperado) {
        responder(200, corpo);

        ManifestarResultado r = manifestar(adapterCom(blobs));

        assertThat(r.desfecho()).isEqualTo(esperado);
        assertThat(blobs.ler(r.envelope().orElseThrow()))
                .hasValueSatisfying(b -> assertThat(b).isEqualTo(corpo.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("timeout: IndisponivelTemporario, sem envelope (não chegou corpo para guardar)")
    void timeoutETemporario() {
        servidor.stubFor(post(urlEqualTo(CAMINHO_EVENTO)).willReturn(aResponse().withStatus(200)
                .withFixedDelay(2_000).withBody(resposta("135"))));

        ManifestarResultado r = manifestar(adapterCom(blobs, Duration.ofMillis(200)));

        assertThat(r.desfecho()).isEqualTo(TEMPORARIO);
        assertThat(r.envelope()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {200, 500})
    @DisplayName("SOAP Fault, com 2xx ou 5xx: IndisponivelTemporario, com o bruto guardado")
    void soapFaultETemporario(int status) {
        String corpo = soapFault();
        responder(status, corpo);

        ManifestarResultado r = manifestar(adapterCom(blobs));

        assertThat(r.desfecho()).isEqualTo(TEMPORARIO);
        assertThat(blobs.ler(r.envelope().orElseThrow()))
                .hasValueSatisfying(b -> assertThat(b).isEqualTo(corpo.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("inv. 13: desfechos e logs não trazem xMotivo, XML, chave, CNPJ, protocolo, senha nem chave privada")
    void desfechosELogsSemXmlNemSegredo() {
        record Cenario(int status, String corpo) {}
        List<Cenario> cenarios = List.of(
                new Cenario(200, resposta("135")),
                new Cenario(200, resposta("650")),
                new Cenario(200, resposta("297")),
                new Cenario(200, resposta("213")),
                new Cenario(200, resposta("999")),
                new Cenario(200, respostaDeLote("215", "")),
                new Cenario(200, respostaDeLote("999", "")),
                new Cenario(200, respostaDeLote("128", "")),
                new Cenario(200, respostaDeLote("128", retEvento("135", OUTRA_CHAVE))),
                new Cenario(200, soapFault()),
                new Cenario(200, "<nao-fecha>" + XMOTIVO),
                new Cenario(400, resposta("135")),
                new Cenario(403, resposta("135")),
                new Cenario(500, resposta("135")));

        Logger logger = (Logger) LoggerFactory.getLogger("br.com.doc.ingest");
        Level nivelAnterior = logger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.DEBUG);
        List<String> textos = new ArrayList<>();
        try {
            NfeNacionalAdapter adapter = adapterCom(blobs);
            for (Cenario c : cenarios) {
                responder(c.status(), c.corpo());
                textos.add(manifestar(adapter).desfecho().toString());
            }
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(nivelAnterior);
        }

        assertThat(appender.list).as("a captura de log precisa ter visto os alertas").isNotEmpty();
        for (ILoggingEvent evento : appender.list) {
            textos.add(evento.getFormattedMessage());
            if (evento.getThrowableProxy() != null) {
                textos.add(evento.getThrowableProxy().getMessage());
            }
        }
        assertThat(textos).allSatisfy(texto -> assertThat(texto)
                .doesNotContain(XMOTIVO, "12345678909", "<", CHAVE, OUTRA_CHAVE, CNPJ.valor(), N_PROT, SENHA, chavePrivadaBase64));
    }
}
