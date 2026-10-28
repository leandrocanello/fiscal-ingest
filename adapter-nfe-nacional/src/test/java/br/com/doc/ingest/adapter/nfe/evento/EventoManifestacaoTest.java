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
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMValidateContext;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EventoManifestacao — evento de ciência montado, assinado e serializado")
class EventoManifestacaoTest {

    private static final String CHAVE = "35261012345678000199550010000001231000000011";
    private static final Cnpj AUTOR = new Cnpj("98765432000110");
    private static final Instant AGORA = Instant.parse("2026-10-28T13:04:05Z");

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

    @Test
    @DisplayName("serializa o evento assinado em bytes UTF-8, sem declaração XML duplicada e sem pretty-print")
    void serializaEventoAssinadoEmBytesUtf8SemDeclaracaoDuplicada() {
        String xml = new String(cienciaAssinada(), StandardCharsets.UTF_8);

        assertThat(xml).startsWith("<evento ").doesNotContain("<?xml");
        assertThat(xml).contains("<descEvento>Ciencia da Operacao</descEvento>");
        assertThat(xml).contains("<Signature xmlns=\"" + XMLSignature.XMLNS + "\">");
        assertThat(xml).doesNotContain("\n<").doesNotContain(">\n ");
    }

    @Test
    @DisplayName("reparse dos bytes pela fábrica segura continua com assinatura válida")
    void reparseDosBytesContinuaValido() throws Exception {
        Document doc = FabricaDocumentoSeguro.ler(cienciaAssinada());

        Element infEvento = (Element) doc.getElementsByTagNameNS(ContratoEvento.NS_NFE, "infEvento").item(0);
        infEvento.setIdAttributeNS(null, "Id", true);
        NodeList sigs = doc.getElementsByTagNameNS(XMLSignature.XMLNS, "Signature");
        assertThat(sigs.getLength()).isEqualTo(1);
        DOMValidateContext ctx = new DOMValidateContext(certificado.cadeia()[0].getPublicKey(), sigs.item(0));
        ctx.setProperty("org.jcp.xml.dsig.secureValidation", Boolean.FALSE);
        XMLSignature assinatura = XMLSignatureFactory.getInstance("DOM").unmarshalXMLSignature(ctx);

        assertThat(assinatura.validate(ctx)).isTrue();
    }
}
