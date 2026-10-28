package br.com.doc.ingest.adapter.nfe.evento;

import br.com.doc.ingest.adapter.nfe.cert.CertificadoCarregado;
import br.com.doc.ingest.adapter.nfe.soap.TipoAmbiente;
import br.com.doc.ingest.domain.adapter.TipoManifestacao;
import br.com.doc.ingest.domain.model.Cnpj;
import org.w3c.dom.Document;

import javax.xml.XMLConstants;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.ZoneId;
import java.util.Objects;

/**
 * Fachada da parte offline do {@code manifestar}: monta o {@code evento}, assina o {@code infEvento} e serializa
 * em bytes UTF-8 prontos para entrar no {@code envEvento}.
 *
 * <p>Sem declaração XML (o evento vai embutido no lote/SOAP, que já tem a sua) e sem pretty-print, que mudaria
 * o conteúdo assinado. Os bytes nunca saem do adapter (inv. 3).
 */
public final class EventoManifestacao {

    private final MontadorEvento montador;
    private final AssinadorXml assinador;

    public EventoManifestacao(Clock relogio, ZoneId fuso, AlgoritmoAssinatura algoritmo) {
        this.montador = new MontadorEvento(relogio, fuso);
        this.assinador = new AssinadorXml(algoritmo);
    }

    public byte[] assinado(TipoAmbiente ambiente, Cnpj autor, String chave, TipoManifestacao tipo, int seq,
                           CertificadoCarregado certificado) {
        Objects.requireNonNull(certificado, "certificado");
        Document doc = montador.montar(ambiente, autor, chave, tipo, seq);
        assinador.assinar(doc, new IdEvento(tipo, chave, seq).valor(), certificado);
        return serializar(doc);
    }

    private static byte[] serializar(Document doc) {
        try {
            TransformerFactory fabrica = TransformerFactory.newInstance();
            fabrica.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            fabrica.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            fabrica.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
            Transformer transformador = fabrica.newTransformer();
            transformador.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            transformador.setOutputProperty(OutputKeys.ENCODING, StandardCharsets.UTF_8.name());
            transformador.setOutputProperty(OutputKeys.INDENT, "no");
            ByteArrayOutputStream saida = new ByteArrayOutputStream();
            transformador.transform(new DOMSource(doc), new StreamResult(saida));
            return saida.toByteArray();
        } catch (TransformerException e) {
            // sem causa encadeada: a mensagem do transformador pode trazer trechos do XML (inv. 13)
            throw new IllegalStateException("falha ao serializar o evento (" + e.getClass().getSimpleName() + ")");
        }
    }
}
