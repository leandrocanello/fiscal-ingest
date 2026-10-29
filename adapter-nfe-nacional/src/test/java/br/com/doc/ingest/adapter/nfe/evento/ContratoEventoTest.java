package br.com.doc.ingest.adapter.nfe.evento;

import br.com.doc.ingest.domain.adapter.TipoManifestacao;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ContratoEvento da manifestação")
class ContratoEventoTest {

    @Test
    @DisplayName("ciência vira tpEvento 210210 com descEvento sem acento e constantes do leiaute")
    void mapeiaCienciaPara210210() {
        assertThat(ContratoEvento.tpEvento(TipoManifestacao.CIENCIA)).isEqualTo("210210");
        assertThat(ContratoEvento.descEvento(TipoManifestacao.CIENCIA)).isEqualTo("Ciencia da Operacao");
        assertThat(ContratoEvento.NS_NFE).isEqualTo("http://www.portalfiscal.inf.br/nfe");
        assertThat(ContratoEvento.VERSAO).isEqualTo("1.00");
        assertThat(ContratoEvento.VER_EVENTO).isEqualTo("1.00");
        assertThat(ContratoEvento.C_ORGAO_AN).isEqualTo("91");
    }

    @ParameterizedTest
    @EnumSource(value = TipoManifestacao.class, names = "CIENCIA", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("demais tipos ainda não são suportados e a exceção nomeia o tipo")
    void demaisTiposAindaNaoSuportados(TipoManifestacao tipo) {
        assertThatThrownBy(() -> ContratoEvento.tpEvento(tipo))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(tipo.name());
        assertThatThrownBy(() -> ContratoEvento.descEvento(tipo))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(tipo.name());
    }
}
