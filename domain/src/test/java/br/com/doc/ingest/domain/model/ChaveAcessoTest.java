package br.com.doc.ingest.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ChaveAcessoTest {

    private static final String CHAVE_NFE = "35200111111111000191550010000000011000000015";
    private static final String CHAVE_NFCE = "35200111111111000191650010000000011000000015";

    @Test
    void aceita44Digitos() {
        assertThat(new ChaveAcesso(CHAVE_NFE).valor()).isEqualTo(CHAVE_NFE);
    }

    @Test
    void nuloEhRecusado() {
        assertThatThrownBy(() -> new ChaveAcesso(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "3520011111111100019155001000000001100000001",
            "352001111111110001915500100000000110000000155",
            "3520011111111100019155001000000001100000001X",
            "35200111111111000191550010000000011000000 15",
            "٣٥200111111111000191550010000000011000000015"})
    void invalidaEhRecusadaSemEcoarOValor(String invalida) {
        assertThatThrownBy(() -> new ChaveAcesso(invalida))
                .isInstanceOf(IllegalArgumentException.class)
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain(invalida));
    }

    @Test
    void vaziaEhRecusada() {
        assertThatThrownBy(() -> new ChaveAcesso("")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void modeloLeAsPosicoes21E22() {
        assertThat(new ChaveAcesso(CHAVE_NFE).modelo()).isEqualTo("55");
        assertThat(new ChaveAcesso(CHAVE_NFCE).modelo()).isEqualTo("65");
    }

    @Test
    void toStringNaoExpoeAChave() {
        assertThat(new ChaveAcesso(CHAVE_NFE).toString()).doesNotContain(CHAVE_NFE);
    }
}
