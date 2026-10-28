package br.com.doc.ingest.adapter.nfe.soap;

import br.com.doc.ingest.adapter.nfe.soap.LeitorRespostaDistDfe.Invalida;
import br.com.doc.ingest.adapter.nfe.soap.LeitorRespostaDistDfe.Valida;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Leitura segura do retDistDFeInt")
class LeitorRespostaDistDfeTest {

    private static final String NFE_NS = "http://www.portalfiscal.inf.br/nfe";
    private static final String SOAP12 = "http://www.w3.org/2003/05/soap-envelope";

    private static final String RET_138 = "<retDistDFeInt versao=\"1.01\" xmlns=\"" + NFE_NS + "\">"
            + "<tpAmb>1</tpAmb><verAplic>1.0</verAplic><cStat>138</cStat><xMotivo>Documentos localizados</xMotivo>"
            + "<dhResp>2026-10-28T12:00:00-03:00</dhResp><ultNSU>000000000000002</ultNSU><maxNSU>000000000000005</maxNSU>"
            + "<loteDistDFeInt><docZip NSU=\"000000000000001\" schema=\"resNFe_v1.01.xsd\">dGVzdGU=</docZip>"
            + "<docZip NSU=\"000000000000002\" schema=\"procNFe_v4.00.xsd\">dGVzdGUy</docZip></loteDistDFeInt>"
            + "</retDistDFeInt>";

    private static byte[] envelope(String corpoDoBody) {
        return ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<soap:Envelope xmlns:soap=\"" + SOAP12 + "\"><soap:Body>" + corpoDoBody
                + "</soap:Body></soap:Envelope>").getBytes(StandardCharsets.UTF_8);
    }

    private static String comWrapperUsual(String ret) {
        return "<nfeDistDFeInteresseResponse xmlns=\"" + ContratoDistDfe.NS_WSDL + "\">"
                + "<nfeDistDFeInteresseResult>" + ret + "</nfeDistDFeInteresseResult>"
                + "</nfeDistDFeInteresseResponse>";
    }

    @Test
    @DisplayName("SOAP 1.2 com o wrapper usual vira RetDistDfeInt com cStat, NSUs e docZips")
    void envelopeValidoComWrapper() {
        var resultado = LeitorRespostaDistDfe.ler(envelope(comWrapperUsual(RET_138)));

        assertThat(resultado).isInstanceOf(Valida.class);
        RetDistDfeInt ret = ((Valida) resultado).retorno();
        assertThat(ret.getCStat()).isEqualTo("138");
        assertThat(ret.getUltNSU()).isEqualTo("000000000000002");
        assertThat(ret.getMaxNSU()).isEqualTo("000000000000005");
        assertThat(ret.getDocumentos()).hasSize(2);
        assertThat(ret.getDocumentos().get(1).getSchema()).isEqualTo("procNFe_v4.00.xsd");
        assertThat(ret.getDocumentos().get(1).getContent()).isEqualTo("dGVzdGUy");
    }

    @Test
    @DisplayName("retDistDFeInt é achado em qualquer nível do Body, sem depender do wrapper [VALIDAR item 9]")
    void retDistDfeIntEmQualquerNivel() {
        var direto = LeitorRespostaDistDfe.ler(envelope(RET_138));
        var outroWrapper = LeitorRespostaDistDfe.ler(envelope("<a xmlns=\"urn:x\"><b>" + RET_138 + "</b></a>"));

        assertThat(direto).isInstanceOf(Valida.class);
        assertThat(((Valida) direto).retorno().getCStat()).isEqualTo("138");
        assertThat(outroWrapper).isInstanceOf(Valida.class);
        assertThat(((Valida) outroWrapper).retorno().getDocumentos()).hasSize(2);
    }

    @Test
    @DisplayName("corpo que não é XML é inválido")
    void corpoNaoXml() {
        var resultado = LeitorRespostaDistDfe.ler("<html>Service Unavailable".getBytes(StandardCharsets.UTF_8));

        assertThat(resultado).isInstanceOf(Invalida.class);
        assertThat(((Invalida) resultado).motivo()).isNotBlank().hasSizeLessThanOrEqualTo(80);
    }

    @Test
    @DisplayName("corpo vazio é inválido")
    void corpoVazio() {
        assertThat(LeitorRespostaDistDfe.ler(new byte[0])).isInstanceOf(Invalida.class);
    }

    @Test
    @DisplayName("XML que não é envelope SOAP 1.2 é inválido")
    void naoEhEnvelopeSoap() {
        var resultado = LeitorRespostaDistDfe.ler(RET_138.getBytes(StandardCharsets.UTF_8));

        assertThat(resultado).isInstanceOf(Invalida.class);
    }

    @Test
    @DisplayName("Envelope sem retDistDFeInt é inválido")
    void envelopeSemRetDistDfeInt() {
        var resultado = LeitorRespostaDistDfe.ler(envelope(comWrapperUsual("<outra xmlns=\"" + NFE_NS + "\"/>")));

        assertThat(resultado).isInstanceOf(Invalida.class);
        assertThat(((Invalida) resultado).motivo()).contains("retDistDFeInt");
    }

    @Test
    @DisplayName("soap12:Fault é inválido, mesmo que traga retDistDFeInt no detalhe")
    void soapFault() {
        String fault = "<soap:Fault xmlns:soap=\"" + SOAP12 + "\"><soap:Code><soap:Value>soap:Receiver</soap:Value></soap:Code>"
                + "<soap:Reason><soap:Text xml:lang=\"pt\">erro interno</soap:Text></soap:Reason>"
                + "<soap:Detail>" + RET_138 + "</soap:Detail></soap:Fault>";

        var resultado = LeitorRespostaDistDfe.ler(envelope(fault));

        assertThat(resultado).isInstanceOf(Invalida.class);
        assertThat(((Invalida) resultado).motivo()).containsIgnoringCase("fault");
    }

    @Test
    @DisplayName("DOCTYPE com entidade externa é recusado sem expandir a entidade")
    void doctypeComEntidadeExternaRecusado(@TempDir Path dir) throws Exception {
        Path arquivo = dir.resolve("segredo.txt");
        Files.writeString(arquivo, "SEGREDO-XYZ");
        String xml = "<?xml version=\"1.0\"?><!DOCTYPE e [<!ENTITY x SYSTEM \"" + arquivo.toUri() + "\">]>"
                + "<soap:Envelope xmlns:soap=\"" + SOAP12 + "\"><soap:Body>"
                + RET_138.replace("Documentos localizados", "&x;")
                + "</soap:Body></soap:Envelope>";

        var resultado = LeitorRespostaDistDfe.ler(xml.getBytes(StandardCharsets.UTF_8));

        assertThat(resultado).isInstanceOf(Invalida.class);
        assertThat(resultado.toString()).doesNotContain("SEGREDO-XYZ");
    }

    @Test
    @DisplayName("DOCTYPE sem entidade também é recusado")
    void doctypeSimplesRecusado() {
        String xml = "<!DOCTYPE soap:Envelope><soap:Envelope xmlns:soap=\"" + SOAP12 + "\"><soap:Body>"
                + RET_138 + "</soap:Body></soap:Envelope>";

        assertThat(LeitorRespostaDistDfe.ler(xml.getBytes(StandardCharsets.UTF_8))).isInstanceOf(Invalida.class);
    }

    @Test
    @DisplayName("resposta malformada não vaza conteúdo no motivo nem no toString (inv. 13)")
    void respostaMalformadaNaoVazaConteudo() {
        byte[] malformados[] = {
                "SEGREDO-XYZ não é xml".getBytes(StandardCharsets.UTF_8),
                envelope("<retDistDFeInt xmlns=\"" + NFE_NS + "\"><cStat>SEGREDO-XYZ</retDistDFeInt>"),
                envelope("<soap:Fault xmlns:soap=\"" + SOAP12 + "\"><soap:Reason><soap:Text>SEGREDO-XYZ</soap:Text></soap:Reason></soap:Fault>"),
                envelope("<SEGREDO-XYZ/>"),
        };
        for (byte[] corpo : malformados) {
            var resultado = LeitorRespostaDistDfe.ler(corpo);

            assertThat(resultado).isInstanceOf(Invalida.class);
            assertThat(((Invalida) resultado).motivo()).doesNotContain("SEGREDO-XYZ");
            assertThat(resultado.toString()).doesNotContain("SEGREDO-XYZ");
        }
    }
}
