package br.com.doc.ingest.adapter.nfe.doczip;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.util.StreamReaderDelegate;
import java.io.ByteArrayInputStream;

/**
 * Fábrica de leitores StAX para XML não confiável (CLAUDE.md §13): sem DTD, sem entidades externas.
 * Qualquer {@code <!DOCTYPE>} ou erro de sintaxe vira {@link DocZipInvalidoException}, sem eco do conteúdo.
 */
final class XmlSeguro {

    private static final XMLInputFactory FABRICA = criarFabrica();

    private XmlSeguro() {
    }

    private static XMLInputFactory criarFabrica() {
        XMLInputFactory f = XMLInputFactory.newFactory();
        f.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        f.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        f.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, false);
        f.setProperty(XMLInputFactory.IS_COALESCING, true);
        return f;
    }

    static XMLStreamReader novoLeitor(byte[] xml) {
        try {
            return new Seguro(FABRICA.createXMLStreamReader(new ByteArrayInputStream(xml)));
        } catch (XMLStreamException e) {
            throw new DocZipInvalidoException("XML malformado");
        }
    }

    private static final class Seguro extends StreamReaderDelegate {

        Seguro(XMLStreamReader base) {
            super(base);
        }

        @Override
        public int next() throws XMLStreamException {
            try {
                int evento = super.next();
                if (evento == XMLStreamConstants.DTD || evento == XMLStreamConstants.ENTITY_REFERENCE) {
                    throw new DocZipInvalidoException("XML com DOCTYPE/entidade não é aceito");
                }
                return evento;
            } catch (XMLStreamException | RuntimeException e) {
                if (e instanceof DocZipInvalidoException d) {
                    throw d;
                }
                throw new DocZipInvalidoException("XML malformado");
            }
        }
    }
}
