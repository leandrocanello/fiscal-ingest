package br.com.doc.ingest.adapter.nfe.evento;

import br.com.doc.ingest.adapter.nfe.soap.TipoAmbiente;
import br.com.doc.ingest.domain.adapter.TipoManifestacao;
import br.com.doc.ingest.domain.model.Cnpj;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.namespace.NamespaceContext;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Iterator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MontadorEvento — Ciência da Operação (210210)")
class MontadorEventoCienciaTest {

    /** Chave sintética (UF/CNPJ fictícios) com DV válido. */
    private static final String CHAVE = "35261012345678000199550010000001231000000011";
    private static final Cnpj AUTOR = new Cnpj("98765432000110");
    /** 2026-10-28 13:04:05.789 UTC → 10:04:05 em São Paulo (-03:00). */
    private static final Instant AGORA = Instant.parse("2026-10-28T13:04:05.789Z");
    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");

    private final MontadorEvento montador = new MontadorEvento(Clock.fixed(AGORA, ZoneOffset.UTC), SAO_PAULO);

    private Document montar(TipoAmbiente ambiente) {
        return montador.montar(ambiente, AUTOR, CHAVE, TipoManifestacao.CIENCIA, 1);
    }

    @Test
    @DisplayName("raiz é evento no namespace da NF-e com versao 1.00")
    void raizEvento() {
        Element raiz = montar(TipoAmbiente.PRODUCAO).getDocumentElement();

        assertThat(raiz.getLocalName()).isEqualTo("evento");
        assertThat(raiz.getNamespaceURI()).isEqualTo(ContratoEvento.NS_NFE);
        assertThat(raiz.getAttribute("versao")).isEqualTo(ContratoEvento.VERSAO);
    }

    @Test
    @DisplayName("infEvento traz cOrgao, tpAmb, CNPJ, chNFe, dhEvento, tpEvento, nSeqEvento, verEvento e detEvento, nessa ordem")
    void montaInfEventoComCamposDoLeiaute() throws Exception {
        Document doc = montar(TipoAmbiente.PRODUCAO);

        assertThat(texto(doc, "/n:evento/n:infEvento/n:cOrgao")).isEqualTo("91");
        assertThat(texto(doc, "/n:evento/n:infEvento/n:tpAmb")).isEqualTo("1");
        assertThat(texto(doc, "/n:evento/n:infEvento/n:CNPJ")).isEqualTo("98765432000110");
        assertThat(texto(doc, "/n:evento/n:infEvento/n:chNFe")).isEqualTo(CHAVE);
        assertThat(texto(doc, "/n:evento/n:infEvento/n:dhEvento")).isEqualTo("2026-10-28T10:04:05-03:00");
        assertThat(texto(doc, "/n:evento/n:infEvento/n:tpEvento")).isEqualTo("210210");
        assertThat(texto(doc, "/n:evento/n:infEvento/n:nSeqEvento")).isEqualTo("1");
        assertThat(texto(doc, "/n:evento/n:infEvento/n:verEvento")).isEqualTo("1.00");
        assertThat(texto(doc, "/n:evento/n:infEvento/n:detEvento/@versao")).isEqualTo("1.00");
        assertThat(texto(doc, "/n:evento/n:infEvento/n:detEvento/n:descEvento")).isEqualTo("Ciencia da Operacao");

        assertThat(nomesDosFilhos(doc, "/n:evento/n:infEvento")).containsExactly(
                "cOrgao", "tpAmb", "CNPJ", "chNFe", "dhEvento", "tpEvento", "nSeqEvento", "verEvento", "detEvento");
        assertThat(nomesDosFilhos(doc, "/n:evento/n:infEvento/n:detEvento")).containsExactly("descEvento");
    }

    @Test
    @DisplayName("dhEvento vem do Clock, no fuso configurado, sem frações de segundo")
    void dhEventoVemDoClockComFuso() throws Exception {
        MontadorEvento noVerao = new MontadorEvento(
                Clock.fixed(Instant.parse("2026-01-15T03:00:00.999Z"), ZoneOffset.UTC), SAO_PAULO);

        Document doc = noVerao.montar(TipoAmbiente.PRODUCAO, AUTOR, CHAVE, TipoManifestacao.CIENCIA, 1);

        assertThat(texto(doc, "/n:evento/n:infEvento/n:dhEvento")).isEqualTo("2026-01-15T00:00:00-03:00");
    }

    @Test
    @DisplayName("atributo Id do infEvento é o IdEvento")
    void atributoIdIgualAoIdEvento() throws Exception {
        Document doc = montador.montar(TipoAmbiente.PRODUCAO, AUTOR, CHAVE, TipoManifestacao.CIENCIA, 7);

        assertThat(texto(doc, "/n:evento/n:infEvento/@Id"))
                .isEqualTo(new IdEvento(TipoManifestacao.CIENCIA, CHAVE, 7).valor());
        assertThat(texto(doc, "/n:evento/n:infEvento/n:nSeqEvento")).isEqualTo("7");
    }

    @Test
    @DisplayName("ambiente de homologação gera tpAmb 2")
    void ambienteHomologacaoGeraTpAmb2() throws Exception {
        assertThat(texto(montar(TipoAmbiente.HOMOLOGACAO), "/n:evento/n:infEvento/n:tpAmb")).isEqualTo("2");
    }

    private static String texto(Document doc, String expr) throws XPathExpressionException {
        return (String) xpath().evaluate(expr, doc, XPathConstants.STRING);
    }

    private static List<String> nomesDosFilhos(Document doc, String expr) throws XPathExpressionException {
        Element pai = (Element) xpath().evaluate(expr, doc, XPathConstants.NODE);
        var nomes = new java.util.ArrayList<String>();
        for (var n = pai.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e) {
                assertThat(e.getNamespaceURI()).isEqualTo(ContratoEvento.NS_NFE);
                nomes.add(e.getLocalName());
            }
        }
        return nomes;
    }

    private static XPath xpath() {
        XPath xp = XPathFactory.newInstance().newXPath();
        xp.setNamespaceContext(new NamespaceContext() {
            @Override
            public String getNamespaceURI(String prefix) {
                return "n".equals(prefix) ? ContratoEvento.NS_NFE : null;
            }

            @Override
            public String getPrefix(String uri) {
                return null;
            }

            @Override
            public Iterator<String> getPrefixes(String uri) {
                return null;
            }
        });
        return xp;
    }
}
