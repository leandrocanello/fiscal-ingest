package br.com.doc.ingest.adapter.nfe.soap;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TipoAmbiente do distDFe")
class TipoAmbienteTest {

    @Test
    @DisplayName("produção vira tpAmb 1")
    void producaoVira1() {
        assertThat(TipoAmbiente.PRODUCAO.codigoTpAmb()).isEqualTo("1");
    }

    @Test
    @DisplayName("homologação vira tpAmb 2")
    void homologacaoVira2() {
        assertThat(TipoAmbiente.HOMOLOGACAO.codigoTpAmb()).isEqualTo("2");
    }
}
