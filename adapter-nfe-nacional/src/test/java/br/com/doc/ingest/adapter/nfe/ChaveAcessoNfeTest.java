package br.com.doc.ingest.adapter.nfe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.doc.ingest.domain.model.FixturesFiscais;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ChaveAcessoNfeTest {

    private static final String CHAVE = "35200111111111000191550010000000011000000010";

    @Test
    void aceita44Digitos() {
        assertThat(CHAVE).hasSize(44);
        assertThat(new ChaveAcessoNfe(CHAVE).valor()).isEqualTo(CHAVE);
    }

    @Test
    void nuloEhRejeitado() {
        assertThatThrownBy(() -> new ChaveAcessoNfe(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "3520011111111100019155001000000001100000001",
            "352001111111110001915500100000000110000000155",
            "3520011111111100019155001000000001100000001X",
            "35200111111111000191550010000000011000000 15"})
    void invalidaNaoEcoaOValor(String invalido) {
        assertThatThrownBy(() -> new ChaveAcessoNfe(invalido))
                .isInstanceOf(IllegalArgumentException.class)
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain(invalido.isEmpty() ? "\u0000" : invalido));
    }

    @Test
    void aceitaAlfanumericaComDvCerto() {
        String chave = FixturesFiscais.chave("352604" + "12ABC34501DE35" + "55001000000001100000001");

        assertThat(chave).endsWith("3");
        assertThat(new ChaveAcessoNfe(chave).valor()).isEqualTo(chave);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "35260412ABC34501DE35550010000000011000000014", // alfanumérica, DV certo é 3
            "35261012345678000195550010000001231000000015"}) // numérica, DV certo é 4
    void recusaDvErradoSemEcoar(String dvErrado) {
        assertThatThrownBy(() -> new ChaveAcessoNfe(dvErrado))
                .isInstanceOf(IllegalArgumentException.class)
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain(dvErrado));
    }
}
