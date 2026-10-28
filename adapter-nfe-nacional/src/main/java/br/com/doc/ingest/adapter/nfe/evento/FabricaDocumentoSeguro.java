package br.com.doc.ingest.adapter.nfe.evento;

import org.w3c.dom.Document;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Objects;

/**
 * Único ponto de criação de DOM no adapter: namespace-aware, sem DOCTYPE, sem entidades externas,
 * sem XInclude e com {@code FEATURE_SECURE_PROCESSING} (§13, sem XXE).
 */
public final class FabricaDocumentoSeguro {

    /** Evita que o handler padrão imprima trechos do XML no stderr; o erro sobe como exceção. */
    private static final ErrorHandler SEM_ECO_NO_STDERR = new ErrorHandler() {
        @Override
        public void warning(SAXParseException e) {
        }

        @Override
        public void error(SAXParseException e) throws SAXException {
            throw e;
        }

        @Override
        public void fatalError(SAXParseException e) throws SAXException {
            throw e;
        }
    };

    private FabricaDocumentoSeguro() {
    }

    public static Document novoDocumento() {
        return construtor().newDocument();
    }

    public static Document ler(byte[] xml) {
        Objects.requireNonNull(xml, "xml");
        DocumentBuilder construtor = construtor();
        construtor.setErrorHandler(SEM_ECO_NO_STDERR);
        try {
            return construtor.parse(new ByteArrayInputStream(xml));
        } catch (SAXException | IOException e) {
            throw new XmlInseguroOuInvalidoException(
                    "XML recusado (malformado, DOCTYPE ou entidade externa): " + e.getClass().getSimpleName());
        }
    }

    private static DocumentBuilder construtor() {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setFeature("http://xml.org/sax/features/external-general-entities", false);
            f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            f.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            f.setXIncludeAware(false);
            f.setExpandEntityReferences(false);
            return f.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException("Falha ao configurar DOM seguro", e);
        }
    }
}
