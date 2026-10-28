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
import org.junit.jupiter.params.provider.CsvSource;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMValidateContext;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AssinadorXml — XML-DSig enveloped do infEvento")
class AssinadorXmlTest {

    private static final String DSIG = XMLSignature.XMLNS;
    private static final String CHAVE = "35261012345678000199550010000001231000000011";
    private static final Cnpj AUTOR = new Cnpj("98765432000110");
    private static final Instant AGORA = Instant.parse("2026-10-28T13:04:05Z");
    private static final String ID = new IdEvento(TipoManifestacao.CIENCIA, CHAVE, 1).valor();

    private static CertificadoCarregado certificado;

    @BeforeAll
    static void gerarCertificado() {
        char[] senha = "senha-de-teste".toCharArray();
        byte[] pfx = GeradorPfxDeTeste.moderno(senha.clone(), AGORA.minus(1, ChronoUnit.DAYS),
                AGORA.plus(365, ChronoUnit.DAYS));
        certificado = new CarregadorPfx().carregar(new Credencial(pfx, senha));
    }

    private static Document eventoAssinado(AlgoritmoAssinatura algoritmo) {
        Document doc = new MontadorEvento(Clock.fixed(AGORA, ZoneOffset.UTC), ZoneId.of("America/Sao_Paulo"))
                .montar(TipoAmbiente.HOMOLOGACAO, AUTOR, CHAVE, TipoManifestacao.CIENCIA, 1);
        new AssinadorXml(algoritmo).assinar(doc, ID, certificado);
        return doc;
    }

    private static Element signature(Document doc) {
        NodeList lista = doc.getElementsByTagNameNS(DSIG, "Signature");
        assertThat(lista.getLength()).isEqualTo(1);
        return (Element) lista.item(0);
    }

    private static Element unico(Element pai, String nome) {
        NodeList lista = pai.getElementsByTagNameNS(DSIG, nome);
        assertThat(lista.getLength()).as(nome).isEqualTo(1);
        return (Element) lista.item(0);
    }

    /** Valida com a chave pública do próprio certificado (SHA-1 exige desligar a validação segura do JDK). */
    static boolean valida(Document doc) throws Exception {
        Element infEvento = (Element) doc.getElementsByTagNameNS(ContratoEvento.NS_NFE, "infEvento").item(0);
        infEvento.setIdAttributeNS(null, "Id", true);
        DOMValidateContext ctx = new DOMValidateContext(certificado.cadeia()[0].getPublicKey(), signature(doc));
        ctx.setProperty("org.jcp.xml.dsig.secureValidation", Boolean.FALSE);
        XMLSignature assinatura = XMLSignatureFactory.getInstance("DOM").unmarshalXMLSignature(ctx);
        return assinatura.validate(ctx);
    }

    @Test
    @DisplayName("assinatura enveloped valida com a chave pública do certificado gerado")
    void assinaturaEnvelopedValidaComCertificadoGerado() throws Exception {
        assertThat(valida(eventoAssinado(AlgoritmoAssinatura.PADRAO))).isTrue();
    }

    @Test
    @DisplayName("Signature é filha de evento, logo depois do infEvento")
    void signatureEhIrmaDoInfEvento() {
        Element sig = signature(eventoAssinado(AlgoritmoAssinatura.PADRAO));

        Element pai = (Element) sig.getParentNode();
        assertThat(pai.getLocalName()).isEqualTo("evento");
        Node anterior = sig.getPreviousSibling();
        assertThat(anterior.getLocalName()).isEqualTo("infEvento");
        assertThat(sig.getNextSibling()).isNull();
    }

    @Test
    @DisplayName("Reference aponta para #Id do infEvento")
    void referenceApontaParaOId() {
        Element ref = unico(signature(eventoAssinado(AlgoritmoAssinatura.PADRAO)), "Reference");

        assertThat(ref.getAttribute("URI")).isEqualTo("#" + ID);
    }

    @Test
    @DisplayName("transforms: enveloped-signature + C14N inclusivo; canonicalização C14N inclusivo")
    void transformsEnvelopedEC14n() {
        Element sig = signature(eventoAssinado(AlgoritmoAssinatura.PADRAO));

        List<String> transforms = new ArrayList<>();
        NodeList lista = sig.getElementsByTagNameNS(DSIG, "Transform");
        for (int i = 0; i < lista.getLength(); i++) {
            transforms.add(((Element) lista.item(i)).getAttribute("Algorithm"));
        }
        assertThat(transforms).containsExactly(
                "http://www.w3.org/2000/09/xmldsig#enveloped-signature",
                "http://www.w3.org/TR/2001/REC-xml-c14n-20010315");
        assertThat(unico(sig, "CanonicalizationMethod").getAttribute("Algorithm"))
                .isEqualTo("http://www.w3.org/TR/2001/REC-xml-c14n-20010315");
    }

    @Test
    @DisplayName("KeyInfo traz só o X509Certificate do titular")
    void keyInfoTrazSoOCertificadoDoTitular() throws Exception {
        Element keyInfo = unico(signature(eventoAssinado(AlgoritmoAssinatura.PADRAO)), "KeyInfo");

        Element x509Data = unico(keyInfo, "X509Data");
        List<String> filhos = new ArrayList<>();
        for (Node n = x509Data.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e) {
                filhos.add(e.getLocalName());
            }
        }
        assertThat(filhos).containsExactly("X509Certificate");
        byte[] der = Base64.getMimeDecoder().decode(unico(x509Data, "X509Certificate").getTextContent());
        assertThat(der).isEqualTo(certificado.cadeia()[0].getEncoded());
    }

    @ParameterizedTest
    @CsvSource({
        "RSA_SHA1,   http://www.w3.org/2000/09/xmldsig#rsa-sha1,        http://www.w3.org/2000/09/xmldsig#sha1",
        "RSA_SHA256, http://www.w3.org/2001/04/xmldsig-more#rsa-sha256, http://www.w3.org/2001/04/xmlenc#sha256"
    })
    @DisplayName("SignatureMethod e DigestMethod conforme o AlgoritmoAssinatura, e a assinatura valida")
    void algoritmosConformeParametro(AlgoritmoAssinatura algoritmo, String signatureMethod, String digestMethod)
            throws Exception {
        Document doc = eventoAssinado(algoritmo);
        Element sig = signature(doc);

        assertThat(unico(sig, "SignatureMethod").getAttribute("Algorithm")).isEqualTo(signatureMethod);
        assertThat(unico(sig, "DigestMethod").getAttribute("Algorithm")).isEqualTo(digestMethod);
        assertThat(valida(doc)).isTrue();
    }

    @Test
    @DisplayName("alterar o descEvento depois de assinar invalida a assinatura")
    void adulterarDetEventoInvalidaAssinatura() throws Exception {
        Document doc = eventoAssinado(AlgoritmoAssinatura.PADRAO);
        assertThat(valida(doc)).isTrue();

        Node descEvento = doc.getElementsByTagNameNS(ContratoEvento.NS_NFE, "descEvento").item(0);
        descEvento.setTextContent(descEvento.getTextContent() + " adulterada");

        assertThat(valida(doc)).isFalse();
    }

    @ParameterizedTest
    @CsvSource({"RSA_SHA1", "RSA_SHA256"})
    @DisplayName("assinar duas vezes o mesmo conteúdo com o mesmo relógio dá o mesmo digest e a mesma assinatura")
    void assinarDuasVezesOMesmoConteudoComMesmoRelogioDaMesmoDigest(AlgoritmoAssinatura algoritmo) {
        Element primeira = signature(eventoAssinado(algoritmo));
        Element segunda = signature(eventoAssinado(algoritmo));

        assertThat(unico(segunda, "DigestValue").getTextContent())
                .isNotBlank()
                .isEqualTo(unico(primeira, "DigestValue").getTextContent());
        assertThat(unico(segunda, "SignatureValue").getTextContent())
                .isEqualTo(unico(primeira, "SignatureValue").getTextContent());
    }

    @Test
    @DisplayName("padrão é RSA_SHA1 (MOC 7.0 Tab. 4-2, decisão E2-S11 D1)")
    void padraoEhRsaSha1() {
        assertThat(AlgoritmoAssinatura.PADRAO).isEqualTo(AlgoritmoAssinatura.RSA_SHA1);
    }
}
