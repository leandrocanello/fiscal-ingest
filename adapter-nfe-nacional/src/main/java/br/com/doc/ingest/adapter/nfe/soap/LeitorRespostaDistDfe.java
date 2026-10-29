package br.com.doc.ingest.adapter.nfe.soap;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * Lê o corpo HTTP da resposta do distDFe: DOM seguro (sem DOCTYPE, sem entidades externas) e depois JAXB
 * sobre o nó {@code retDistDFeInt}, achado em qualquer nível do {@code Body} [VALIDAR FONTES-OFICIAIS item 9:
 * nome do wrapper da resposta]. Nenhuma falha ecoa o conteúdo recebido (inv. 13).
 */
public final class LeitorRespostaDistDfe {

    private static final JAXBContext CONTEXTO = criarContexto();

    private static final ErrorHandler SILENCIOSO = new ErrorHandler() {
        // o handler padrão imprime o erro no stderr, com trechos do documento
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

    private LeitorRespostaDistDfe() {
    }

    public sealed interface Resultado {
    }

    public record Valida(RetDistDfeInt retorno) implements Resultado {
        @Override
        public String toString() {
            return "Valida[cStat=" + retorno.getCStat() + "]";
        }
    }

    /** {@code motivo} é um texto fixo e curto, nunca trecho da resposta. */
    public record Invalida(String motivo) implements Resultado {
    }

    public static Resultado ler(byte[] corpo) {
        Document doc;
        try {
            DocumentBuilder builder = fabrica().newDocumentBuilder();
            builder.setErrorHandler(SILENCIOSO);
            doc = builder.parse(new ByteArrayInputStream(corpo));
        } catch (SAXException | IOException e) {
            // a mensagem do parser pode citar o conteúdo ou o DOCTYPE: não é propagada
            return new Invalida("corpo não é XML bem formado ou contém DOCTYPE");
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException("parser XML sem suporte às proteções exigidas", e);
        }

        Element raiz = doc.getDocumentElement();
        if (!ehSoap12(raiz, "Envelope")) {
            return new Invalida("resposta não é envelope SOAP 1.2");
        }
        Element body = primeiroFilho(raiz, "Body");
        if (body == null) {
            return new Invalida("envelope SOAP sem Body");
        }
        if (body.getElementsByTagNameNS(ContratoDistDfe.NS_SOAP12, "Fault").getLength() > 0) {
            return new Invalida("SOAP Fault");
        }
        NodeList rets = body.getElementsByTagNameNS(ContratoDistDfe.NS_NFE, "retDistDFeInt");
        if (rets.getLength() == 0) {
            return new Invalida("envelope sem retDistDFeInt");
        }
        try {
            Object ret = CONTEXTO.createUnmarshaller().unmarshal(rets.item(0));
            return new Valida((RetDistDfeInt) ret);
        } catch (JAXBException | ClassCastException e) {
            return new Invalida("retDistDFeInt fora do layout esperado");
        }
    }

    private static DocumentBuilderFactory fabrica() throws ParserConfigurationException {
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
        return f;
    }

    private static Element primeiroFilho(Element pai, String nomeLocal) {
        for (Node n = pai.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e && ehSoap12(e, nomeLocal)) {
                return e;
            }
        }
        return null;
    }

    private static boolean ehSoap12(Element e, String nomeLocal) {
        return ContratoDistDfe.NS_SOAP12.equals(e.getNamespaceURI()) && nomeLocal.equals(e.getLocalName());
    }

    private static JAXBContext criarContexto() {
        try {
            return JAXBContext.newInstance(RetDistDfeInt.class);
        } catch (JAXBException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
