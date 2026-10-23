package br.com.doc.ingest.adapter.nfe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import javax.xml.crypto.dsig.XMLSignatureFactory;
import org.apache.xml.security.Init;
import org.junit.jupiter.api.Test;

/** Guarda de regressão: a remoção do CXF não pode ter levado junto o que a assinatura (2E) usa. */
class SantuarioDisponivelTest {

    @Test
    void santuarioInicializa() {
        assertThatCode(Init::init).doesNotThrowAnyException();
        assertThat(Init.isInitialized()).isTrue();
    }

    @Test
    void jdkFornecedorXmlDsigDom() throws Exception {
        assertThat(XMLSignatureFactory.getInstance("DOM")).isNotNull();
    }
}
