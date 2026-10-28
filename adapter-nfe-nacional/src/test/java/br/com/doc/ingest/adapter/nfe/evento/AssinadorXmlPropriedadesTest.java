package br.com.doc.ingest.adapter.nfe.evento;

import br.com.doc.ingest.adapter.nfe.cert.CarregadorPfx;
import br.com.doc.ingest.adapter.nfe.cert.CertificadoCarregado;
import br.com.doc.ingest.adapter.nfe.cert.GeradorPfxDeTeste;
import br.com.doc.ingest.adapter.nfe.soap.TipoAmbiente;
import br.com.doc.ingest.domain.adapter.Credencial;
import br.com.doc.ingest.domain.adapter.TipoManifestacao;
import br.com.doc.ingest.domain.model.Cnpj;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMValidateContext;
import org.w3c.dom.Node;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Chave RSA gerada uma vez por classe (lazy holder); poucas tentativas por causa do custo da assinatura. */
class AssinadorXmlPropriedadesTest {

    private static final Instant AGORA = Instant.parse("2026-10-28T13:04:05Z");

    private static final class Holder {
        static final CertificadoCarregado CERT = gerar();

        private static CertificadoCarregado gerar() {
            char[] senha = "senha-de-teste".toCharArray();
            byte[] pfx = GeradorPfxDeTeste.moderno(senha.clone(), AGORA.minus(1, ChronoUnit.DAYS),
                    AGORA.plus(365, ChronoUnit.DAYS));
            return new CarregadorPfx().carregar(new Credencial(pfx, senha));
        }
    }

    @Provide
    Arbitrary<String> chaves() {
        return Arbitraries.strings().numeric().ofLength(44);
    }

    @Provide
    Arbitrary<Cnpj> cnpjs() {
        return Arbitraries.strings().numeric().ofLength(14).map(Cnpj::new);
    }

    private static Document assinado(Cnpj autor, String chave, int seq) {
        Document doc = new MontadorEvento(Clock.fixed(AGORA, ZoneOffset.UTC), ZoneId.of("America/Sao_Paulo"))
                .montar(TipoAmbiente.HOMOLOGACAO, autor, chave, TipoManifestacao.CIENCIA, seq);
        new AssinadorXml(AlgoritmoAssinatura.PADRAO)
                .assinar(doc, new IdEvento(TipoManifestacao.CIENCIA, chave, seq).valor(), Holder.CERT);
        return doc;
    }

    private static boolean valida(Document doc) throws Exception {
        Element infEvento = (Element) doc.getElementsByTagNameNS(ContratoEvento.NS_NFE, "infEvento").item(0);
        infEvento.setIdAttributeNS(null, "Id", true);
        Element sig = (Element) doc.getElementsByTagNameNS(XMLSignature.XMLNS, "Signature").item(0);
        DOMValidateContext ctx = new DOMValidateContext(Holder.CERT.cadeia()[0].getPublicKey(), sig);
        ctx.setProperty("org.jcp.xml.dsig.secureValidation", Boolean.FALSE);
        return XMLSignatureFactory.getInstance("DOM").unmarshalXMLSignature(ctx).validate(ctx);
    }

    @Property(tries = 15)
    void eventoAssinadoSempreValida(@ForAll("cnpjs") Cnpj autor, @ForAll("chaves") String chave,
                                    @ForAll @IntRange(min = 1, max = 99) int seq) throws Exception {
        assertThat(valida(assinado(autor, chave, seq))).isTrue();
    }

    @Property(tries = 15)
    void alterarTextoDoInfEventoSempreInvalida(@ForAll("cnpjs") Cnpj autor, @ForAll("chaves") String chave,
                                               @ForAll @IntRange(min = 1, max = 99) int seq,
                                               @ForAll @IntRange(min = 0, max = 1000) int escolha) throws Exception {
        Document doc = assinado(autor, chave, seq);
        Node infEvento = doc.getElementsByTagNameNS(ContratoEvento.NS_NFE, "infEvento").item(0);
        java.util.List<Node> textos = new java.util.ArrayList<>();
        coletarTextos(infEvento, textos);
        Node alvo = textos.get(escolha % textos.size());

        alvo.setTextContent(alvo.getTextContent() + "x");

        assertThat(valida(doc)).isFalse();
    }

    private static void coletarTextos(Node no, java.util.List<Node> saida) {
        for (Node f = no.getFirstChild(); f != null; f = f.getNextSibling()) {
            if (f.getNodeType() == Node.TEXT_NODE && !f.getTextContent().isBlank()) {
                saida.add(f);
            } else {
                coletarTextos(f, saida);
            }
        }
    }
}
