package br.com.doc.ingest.adapter.nfe.evento;

import br.com.doc.ingest.adapter.nfe.cert.CarregadorPfx;
import br.com.doc.ingest.adapter.nfe.cert.CertificadoCarregado;
import br.com.doc.ingest.adapter.nfe.cert.GeradorPfxDeTeste;
import br.com.doc.ingest.adapter.nfe.soap.TipoAmbiente;
import br.com.doc.ingest.domain.adapter.Credencial;
import br.com.doc.ingest.domain.adapter.TipoManifestacao;
import br.com.doc.ingest.domain.model.Cnpj;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMValidateContext;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("EnvelopeEnvEvento — lote envEvento dentro do SOAP 1.2 do RecepcaoEvento")
class EnvelopeEnvEventoTest {

    private static final String CHAVE = "35261012345678000199550010000001231000000011";
    private static final Cnpj AUTOR = new Cnpj("98765432000110");
    private static final Instant AGORA = Instant.parse("2026-10-28T13:04:05Z");
    private static final String NS_SOAP12 = "http://www.w3.org/2003/05/soap-envelope";

    private static CertificadoCarregado certificado;

    @BeforeAll
    static void gerarCertificado() {
        char[] senha = "senha-de-teste".toCharArray();
        byte[] pfx = GeradorPfxDeTeste.moderno(senha.clone(), AGORA.minus(1, ChronoUnit.DAYS),
                AGORA.plus(365, ChronoUnit.DAYS));
        certificado = new CarregadorPfx().carregar(new Credencial(pfx, senha));
    }

    private static byte[] cienciaAssinada() {
        return new EventoManifestacao(Clock.fixed(AGORA, ZoneOffset.UTC), ZoneId.of("America/Sao_Paulo"),
                AlgoritmoAssinatura.PADRAO)
                .assinado(TipoAmbiente.HOMOLOGACAO, AUTOR, CHAVE, TipoManifestacao.CIENCIA, 1, certificado);
    }

    private static Element envEvento(Document doc) {
        var lista = doc.getElementsByTagNameNS(ContratoEvento.NS_NFE, "envEvento");
        assertThat(lista.getLength()).isEqualTo(1);
        return (Element) lista.item(0);
    }

    private static List<Element> filhos(Element pai) {
        List<Element> elementos = new ArrayList<>();
        for (Node n = pai.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e) {
                elementos.add(e);
            }
        }
        return elementos;
    }

    /** Leitura byte a byte: ISO-8859-1 mapeia cada byte em um caractere. */
    private static String bytesComoTexto(byte[] bytes) {
        return new String(bytes, StandardCharsets.ISO_8859_1);
    }

    @Test
    @DisplayName("envEvento tem versao 1.00, idLote com 15 dígitos e depois o evento")
    void envEventoComVersaoEIdLote15Digitos() {
        Document doc = FabricaDocumentoSeguro.ler(EnvelopeEnvEvento.soap12(cienciaAssinada(), 123L));

        Element lote = envEvento(doc);
        assertThat(lote.getAttribute("versao")).isEqualTo("1.00");
        List<Element> filhos = filhos(lote);
        assertThat(filhos).extracting(Element::getLocalName).containsExactly("idLote", "evento");
        assertThat(filhos).allSatisfy(e -> assertThat(e.getNamespaceURI()).isEqualTo(ContratoEvento.NS_NFE));
        assertThat(filhos.get(0).getTextContent()).isEqualTo("000000000000123");
    }

    @ParameterizedTest
    @ValueSource(longs = {-1L, 1_000_000_000_000_000L})
    @DisplayName("idLote que não cabe em 15 dígitos é recusado")
    void idLoteForaDosQuinzeDigitosEhRecusado(long idLote) {
        assertThatThrownBy(() -> EnvelopeEnvEvento.soap12(cienciaAssinada(), idLote))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("os bytes do evento entram sem alteração, mesmo com aspas, espaços e acentos que um DOM reescreveria")
    void eventoAssinadoEmbutidoByteABytE() {
        byte[] assinado = cienciaAssinada();
        byte[] incomum = ("<evento  versao='1.00'   xmlns='" + ContratoEvento.NS_NFE + "' ><a >ção</a ></evento >")
                .getBytes(StandardCharsets.UTF_8);

        assertThat(bytesComoTexto(EnvelopeEnvEvento.soap12(assinado, 1L))).contains(bytesComoTexto(assinado));
        assertThat(bytesComoTexto(EnvelopeEnvEvento.soap12(incomum, 1L))).contains(bytesComoTexto(incomum));
    }

    /**
     * Valida no próprio documento do envelope. O C14N inclusivo leva os namespaces em escopo para o digest, então
     * um {@code xmlns:soap12} no Envelope quebraria esta validação.
     */
    @Test
    @DisplayName("a assinatura continua válida com o evento no lugar, dentro do envelope")
    void assinaturaContinuaValidaDentroDoEnvelope() throws Exception {
        Document doc = FabricaDocumentoSeguro.ler(EnvelopeEnvEvento.soap12(cienciaAssinada(), 1L));

        Element infEvento = (Element) doc.getElementsByTagNameNS(ContratoEvento.NS_NFE, "infEvento").item(0);
        infEvento.setIdAttributeNS(null, "Id", true);
        Node signature = doc.getElementsByTagNameNS(XMLSignature.XMLNS, "Signature").item(0);
        DOMValidateContext ctx = new DOMValidateContext(certificado.cadeia()[0].getPublicKey(), signature);
        ctx.setProperty("org.jcp.xml.dsig.secureValidation", Boolean.FALSE);

        assertThat(XMLSignatureFactory.getInstance("DOM").unmarshalXMLSignature(ctx).validate(ctx)).isTrue();
    }

    @Test
    @DisplayName("SOAP 1.2 com prólogo UTF-8 e Body contendo só o nfeDadosMsg no namespace do WSDL")
    void soap12ComOperacaoDoWsdl() {
        byte[] bytes = EnvelopeEnvEvento.soap12(cienciaAssinada(), 1L);

        assertThat(new String(bytes, StandardCharsets.UTF_8))
                .startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        Element envelope = FabricaDocumentoSeguro.ler(bytes).getDocumentElement();
        assertThat(envelope.getLocalName()).isEqualTo("Envelope");
        assertThat(envelope.getNamespaceURI()).isEqualTo(NS_SOAP12);

        List<Element> corpo = filhos(envelope);
        assertThat(corpo).hasSize(1);
        assertThat(corpo.get(0).getLocalName()).isEqualTo("Body");
        assertThat(corpo.get(0).getNamespaceURI()).isEqualTo(NS_SOAP12);

        List<Element> dados = filhos(corpo.get(0));
        assertThat(dados).hasSize(1);
        assertThat(dados.get(0).getLocalName()).isEqualTo("nfeDadosMsg");
        assertThat(dados.get(0).getNamespaceURI()).isEqualTo(ContratoRecepcaoEvento.NS_WSDL);
        assertThat(filhos(dados.get(0))).extracting(Element::getLocalName).containsExactly("envEvento");
    }
}
