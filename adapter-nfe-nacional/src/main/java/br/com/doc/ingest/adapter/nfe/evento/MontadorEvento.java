package br.com.doc.ingest.adapter.nfe.evento;

import br.com.doc.ingest.adapter.nfe.soap.TipoAmbiente;
import br.com.doc.ingest.domain.adapter.TipoManifestacao;
import br.com.doc.ingest.domain.model.Cnpj;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Monta o {@code evento} (ainda sem assinatura) da manifestação do destinatário, conforme o leiaute do
 * MOC 7.0 p. 100 (decisão E2-S11 D2). [VALIDAR docs/FONTES-OFICIAIS.md itens 6/7].
 *
 * <p>O {@code dhEvento} vem do {@link Clock} injetado, no fuso informado, sem frações de segundo
 * (formato {@code AAAA-MM-DDThh:mm:ssTZD}).
 */
public final class MontadorEvento {

    private static final DateTimeFormatter DH_EVENTO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    private final Clock relogio;
    private final ZoneId fuso;

    public MontadorEvento(Clock relogio, ZoneId fuso) {
        this.relogio = Objects.requireNonNull(relogio, "relogio");
        this.fuso = Objects.requireNonNull(fuso, "fuso");
    }

    public Document montar(TipoAmbiente ambiente, Cnpj autor, String chave, TipoManifestacao tipo, int seq) {
        Objects.requireNonNull(ambiente, "ambiente");
        Objects.requireNonNull(autor, "autor");
        IdEvento id = new IdEvento(tipo, chave, seq);

        Document doc = novoDocumento();
        Element evento = doc.createElementNS(ContratoEvento.NS_NFE, "evento");
        evento.setAttribute("versao", ContratoEvento.VERSAO);
        doc.appendChild(evento);

        Element inf = filho(evento, "infEvento", null);
        inf.setAttribute("Id", id.valor());
        filho(inf, "cOrgao", ContratoEvento.C_ORGAO_AN);
        filho(inf, "tpAmb", ambiente.codigoTpAmb());
        filho(inf, "CNPJ", autor.valor());
        filho(inf, "chNFe", chave);
        filho(inf, "dhEvento", ZonedDateTime.now(relogio).withZoneSameInstant(fuso)
                .truncatedTo(ChronoUnit.SECONDS).format(DH_EVENTO));
        filho(inf, "tpEvento", ContratoEvento.tpEvento(tipo));
        filho(inf, "nSeqEvento", String.valueOf(seq));
        filho(inf, "verEvento", ContratoEvento.VER_EVENTO);
        Element det = filho(inf, "detEvento", null);
        det.setAttribute("versao", ContratoEvento.VER_EVENTO);
        filho(det, "descEvento", ContratoEvento.descEvento(tipo));
        return doc;
    }

    private static Element filho(Element pai, String nome, String texto) {
        Element e = pai.getOwnerDocument().createElementNS(ContratoEvento.NS_NFE, nome);
        if (texto != null) {
            e.setTextContent(texto);
        }
        pai.appendChild(e);
        return e;
    }

    private static Document novoDocumento() {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setFeature("http://xml.org/sax/features/external-general-entities", false);
            f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            f.setXIncludeAware(false);
            f.setExpandEntityReferences(false);
            return f.newDocumentBuilder().newDocument();
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException("Falha ao configurar DOM seguro", e);
        }
    }
}
