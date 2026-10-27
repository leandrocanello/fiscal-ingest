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
}
