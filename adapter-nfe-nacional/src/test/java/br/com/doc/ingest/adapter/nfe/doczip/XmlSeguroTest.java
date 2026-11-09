package br.com.doc.ingest.adapter.nfe.doczip;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Leitor StAX seguro")
class XmlSeguroTest {

    private static void lerTudo(XMLStreamReader r) throws Exception {
        while (r.hasNext()) {
            r.next();
        }
    }

    @Test
    @DisplayName("xmlComDoctypeEhRejeitado: entidade externa nunca é lida")
    void xmlComDoctypeEhRejeitado(@TempDir Path dir) throws Exception {
        Path segredo = dir.resolve("segredo.txt");
        Files.writeString(segredo, "CONTEUDO-SECRETO-XYZ");
        String xml = "<?xml version=\"1.0\"?><!DOCTYPE a [<!ENTITY e SYSTEM \"" + segredo.toUri() + "\">]>"
                + "<a>&e;</a>";

        assertThatThrownBy(() -> lerTudo(XmlSeguro.novoLeitor(xml.getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(DocZipInvalidoException.class)
                .hasMessageNotContaining("CONTEUDO-SECRETO-XYZ");
    }

    @Test
    @DisplayName("xmlMalformadoLancaDocZipInvalido")
    void xmlMalformadoLancaDocZipInvalido() {
        assertThatThrownBy(() -> lerTudo(XmlSeguro.novoLeitor("<a><b></a>".getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(DocZipInvalidoException.class);
    }

    @Test
    @DisplayName("xml válido é lido")
    void xmlValidoELido() throws Exception {
        XMLStreamReader r = XmlSeguro.novoLeitor("<a><b>1</b></a>".getBytes(StandardCharsets.UTF_8));
        lerTudo(r);
        assertThat(r.getEventType()).isEqualTo(XMLStreamConstants.END_DOCUMENT);
    }
}
