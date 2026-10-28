package br.com.doc.ingest.adapter.nfe.evento;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("FabricaDocumentoSeguro — DOM sem XXE")
class FabricaDocumentoSeguroTest {

    private static final String SEGREDO = "conteudo-que-nao-pode-ser-lido";

    @Test
    void recusaDoctypeEEntidadeExterna(@TempDir Path dir) throws Exception {
        Path alvo = dir.resolve("segredo.txt");
        Files.writeString(alvo, SEGREDO);
        String xml = "<?xml version=\"1.0\"?>"
                + "<!DOCTYPE evento [<!ENTITY xxe SYSTEM \"" + alvo.toUri() + "\">]>"
                + "<evento>&xxe;</evento>";

        assertThatThrownBy(() -> FabricaDocumentoSeguro.ler(xml.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(XmlInseguroOuInvalidoException.class)
                .hasNoCause()
                .satisfies(e -> assertThat(e.toString()).doesNotContain(SEGREDO));
    }

    @Test
    void recusaDoctypeComDtdExterno(@TempDir Path dir) throws Exception {
        Path dtd = dir.resolve("externo.dtd");
        Files.writeString(dtd, "<!ENTITY x \"" + SEGREDO + "\">");
        String xml = "<!DOCTYPE evento SYSTEM \"" + dtd.toUri() + "\"><evento>&x;</evento>";

        assertThatThrownBy(() -> FabricaDocumentoSeguro.ler(xml.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(XmlInseguroOuInvalidoException.class)
                .satisfies(e -> assertThat(e.toString()).doesNotContain(SEGREDO));
    }

    @Test
    void leXmlComumComNamespace() {
        String xml = "<evento xmlns=\"" + ContratoEvento.NS_NFE + "\" versao=\"1.00\"><infEvento/></evento>";

        Document doc = FabricaDocumentoSeguro.ler(xml.getBytes(StandardCharsets.UTF_8));

        assertThat(doc.getDocumentElement().getNamespaceURI()).isEqualTo(ContratoEvento.NS_NFE);
        assertThat(doc.getDocumentElement().getLocalName()).isEqualTo("evento");
    }

    @Test
    void xmlMalformadoViraExcecaoDoAdapter() {
        assertThatThrownBy(() -> FabricaDocumentoSeguro.ler("<evento>".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(XmlInseguroOuInvalidoException.class);
    }

    @Test
    void novoDocumentoVazioENamespaceAware() {
        Document doc = FabricaDocumentoSeguro.novoDocumento();

        assertThat(doc.getDocumentElement()).isNull();
    }
}
