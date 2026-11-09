package br.com.doc.ingest.adapter.nfe.evento;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ContratoRecepcaoEvento — constantes do NFeRecepcaoEvento (decisão E2-S13 D2)")
class ContratoRecepcaoEventoTest {

    @Test
    @DisplayName("namespace do WSDL, operação, action e versão do lote são os decididos em D2")
    void constantesDeD2() {
        assertThat(ContratoRecepcaoEvento.NS_WSDL)
                .isEqualTo("http://www.portalfiscal.inf.br/nfe/wsdl/NFeRecepcaoEvento4");
        assertThat(ContratoRecepcaoEvento.OPERACAO).isEqualTo("nfeRecepcaoEvento");
        assertThat(ContratoRecepcaoEvento.ACTION)
                .isEqualTo("http://www.portalfiscal.inf.br/nfe/wsdl/NFeRecepcaoEvento4/nfeRecepcaoEvento");
        assertThat(ContratoRecepcaoEvento.VERSAO_LOTE).isEqualTo("1.00");
    }
}
