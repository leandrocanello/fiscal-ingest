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

    // NT Conjunta 2025.001: [0-9]{6}[A-Z0-9]{12}[0-9]{26}; exemplo da Receita na posicao do emitente
    private static final String CHAVE_ALFANUMERICA = "352604" + "12ABC34501DE35" + "55001000000001100000001" + "3";

    @Test
    void aceitaChaveAlfanumerica() {
        assertThat(new ChaveAcesso(CHAVE_ALFANUMERICA).valor()).isEqualTo(CHAVE_ALFANUMERICA);
        assertThat(ChaveAcesso.formatoValido(CHAVE_ALFANUMERICA)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "A52604" + "12ABC34501DE35" + "55001000000001100000001" + "3",
            "35260A" + "12ABC34501DE35" + "55001000000001100000001" + "3",
            "352604" + "12ABC34501DE35" + "A5001000000001100000001" + "3",
            "352604" + "12ABC34501DE35" + "55001000000001100000001" + "X",
            "352604" + "12ABC34501DEA5" + "55001000000001100000001" + "3"})
    void recusaLetraForaDasPosicoes7a18(String invalida) {
        assertThat(ChaveAcesso.formatoValido(invalida)).isFalse();
        assertThatThrownBy(() -> new ChaveAcesso(invalida))
                .isInstanceOf(IllegalArgumentException.class)
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain(invalida));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "352604" + "12abc34501de35" + "55001000000001100000001" + "3",
            "352604" + "12ＡBC34501DE35" + "55001000000001100000001" + "3",
            "352604" + "12ÇBC34501DE35" + "55001000000001100000001" + "3"})
    void recusaMinuscula(String invalida) {
        assertThat(ChaveAcesso.formatoValido(invalida)).isFalse();
        assertThatThrownBy(() -> new ChaveAcesso(invalida)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void formatoValidoRecusaNulo() {
        assertThat(ChaveAcesso.formatoValido(null)).isFalse();
    }

    @Test
    void modeloContinuaNasPosicoes21E22() {
        assertThat(new ChaveAcesso(CHAVE_ALFANUMERICA).modelo()).isEqualTo("55");
    }

    @Test
    void temDvValidoConfereOUltimoDigito() {
        String base = "352604" + "12ABC34501DE35" + "55001000000001100000001";
        String certa = FixturesFiscais.chave(base);
        char dv = certa.charAt(43);
        String errada = base + (char) ('0' + (dv - '0' + 1) % 10);
        assertThat(new ChaveAcesso(certa).temDvValido()).isTrue();
        assertThat(new ChaveAcesso(errada).temDvValido()).isFalse();
        String numerica = FixturesFiscais.chave("3520011111111100019155001000000001100000001");
        assertThat(new ChaveAcesso(numerica).temDvValido()).isTrue();
    }

    @Test
    void formatoNaoConfereDv() {
        // DA-38: na entrada so o formato e conferido; o DV fica para temDvValido()
        String base = "352604" + "12ABC34501DE35" + "55001000000001100000001";
        char dv = FixturesFiscais.chave(base).charAt(43);
        String errada = base + (char) ('0' + (dv - '0' + 1) % 10);
        assertThat(ChaveAcesso.formatoValido(errada)).isTrue();
        assertThat(new ChaveAcesso(errada).valor()).isEqualTo(errada);
    }
}
