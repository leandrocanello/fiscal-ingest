package br.com.doc.ingest.adapter.nfe.soap;

import br.com.doc.ingest.domain.model.Cnpj;
import br.com.doc.ingest.domain.model.Nsu;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Montagem do distDFeInt por ultNSU")
class EnvelopeDistDfeTest {

    private static final Cnpj CNPJ = new Cnpj("11111111000191");

    private String monta(TipoAmbiente ambiente, Nsu nsu) {
        return new String(EnvelopeDistDfe.distNsu(ambiente, CNPJ, nsu), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("ultNSU vai com quinze dígitos")
    void ultNsuVaiComQuinzeDigitos() {
        assertThat(monta(TipoAmbiente.PRODUCAO, new Nsu(100)))
                .contains("<ultNSU>000000000000100</ultNSU>");
        assertThat(monta(TipoAmbiente.PRODUCAO, new Nsu(0)))
                .contains("<ultNSU>000000000000000</ultNSU>");
    }

    @Test
    @DisplayName("CNPJ, ambiente e versão vão no corpo, sem declaração XML")
    void cnpjEAmbienteVaoNoCorpo() {
        String xml = monta(TipoAmbiente.HOMOLOGACAO, new Nsu(1));
        assertThat(xml)
                .contains("<tpAmb>2</tpAmb>")
                .contains("<CNPJ>11111111000191</CNPJ>")
                .contains("versao=\"1.01\"")
                .doesNotContain("<?xml");
    }

    @Test
    @DisplayName("Envelope SOAP 1.2 com nfeDadosMsg contendo o distDFeInt")
    void envelopeSoap12ComNfeDadosMsg() throws Exception {
        byte[] bytes = EnvelopeDistDfe.soap12DistNsu(TipoAmbiente.PRODUCAO, CNPJ, new Nsu(5));

        assertThat(new String(bytes, StandardCharsets.UTF_8))
                .startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");

        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        Document doc = f.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));

        Element envelope = doc.getDocumentElement();
        assertThat(envelope.getLocalName()).isEqualTo("Envelope");
        assertThat(envelope.getNamespaceURI()).isEqualTo("http://www.w3.org/2003/05/soap-envelope");

        NodeList corpos = envelope.getElementsByTagNameNS(ContratoDistDfe.NS_SOAP12, "Body");
        assertThat(corpos.getLength()).isEqualTo(1);
        NodeList ops = ((Element) corpos.item(0))
                .getElementsByTagNameNS(ContratoDistDfe.NS_WSDL, ContratoDistDfe.OPERACAO);
        assertThat(ops.getLength()).isEqualTo(1);
        NodeList dados = ((Element) ops.item(0))
                .getElementsByTagNameNS(ContratoDistDfe.NS_WSDL, "nfeDadosMsg");
        assertThat(dados.getLength()).isEqualTo(1);
        NodeList dist = ((Element) dados.item(0))
                .getElementsByTagNameNS("http://www.portalfiscal.inf.br/nfe", "distDFeInt");
        assertThat(dist.getLength()).isEqualTo(1);
    }

    private static final String CHAVE = "35200111111111000191550010000000011000000015";

    @Test
    @DisplayName("consNSU leva o NSU com quinze dígitos e não leva distNSU")
    void consNsuSemDistNsu() {
        String xml = new String(EnvelopeDistDfe.consNsu(TipoAmbiente.PRODUCAO, CNPJ, new Nsu(42)), StandardCharsets.UTF_8);
        assertThat(xml)
                .contains("<consNSU><NSU>000000000000042</NSU></consNSU>")
                .contains("<CNPJ>11111111000191</CNPJ>")
                .doesNotContain("distNSU")
                .doesNotContain("ultNSU")
                .doesNotContain("consChNFe");
    }

    @Test
    @DisplayName("consChNFe leva a chave de 44 dígitos e não leva distNSU")
    void consChNFeSemDistNsu() {
        String xml = new String(EnvelopeDistDfe.consChNFe(TipoAmbiente.PRODUCAO, CNPJ, CHAVE), StandardCharsets.UTF_8);
        assertThat(xml)
                .contains("<consChNFe><chNFe>" + CHAVE + "</chNFe></consChNFe>")
                .doesNotContain("distNSU")
                .doesNotContain("consNSU");
    }

    @Test
    @DisplayName("SOAP do consNSU/consChNFe usa o mesmo envelope e o mesmo ACTION")
    void soapComMesmoEnvelope() {
        String nsu = new String(EnvelopeDistDfe.soap12ConsNsu(TipoAmbiente.PRODUCAO, CNPJ, new Nsu(7)), StandardCharsets.UTF_8);
        String chave = new String(EnvelopeDistDfe.soap12ConsChNFe(TipoAmbiente.PRODUCAO, CNPJ, CHAVE), StandardCharsets.UTF_8);
        String dist = new String(EnvelopeDistDfe.soap12DistNsu(TipoAmbiente.PRODUCAO, CNPJ, new Nsu(7)), StandardCharsets.UTF_8);
        String prefixo = dist.substring(0, dist.indexOf("<distDFeInt"));
        assertThat(nsu).startsWith(prefixo).contains("<consNSU>").endsWith("</soap12:Body></soap12:Envelope>");
        assertThat(chave).startsWith(prefixo).contains("<consChNFe>").endsWith("</soap12:Body></soap12:Envelope>");
    }

    @Test
    @DisplayName("Formatação do NSU não depende do locale")
    void formatacaoIndependeDeLocale() {
        java.util.Locale anterior = java.util.Locale.getDefault();
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("ar-SA-u-nu-arab"));
            assertThat(monta(TipoAmbiente.PRODUCAO, new Nsu(100))).contains("<ultNSU>000000000000100</ultNSU>");
            assertThat(new String(EnvelopeDistDfe.consNsu(TipoAmbiente.PRODUCAO, CNPJ, new Nsu(42)), StandardCharsets.UTF_8))
                    .contains("<NSU>000000000000042</NSU>");
        } finally {
            java.util.Locale.setDefault(anterior);
        }
    }
}
