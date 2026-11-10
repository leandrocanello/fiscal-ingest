package br.com.doc.ingest.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DigitoVerificadorTest {

    @Test
    void calculaOsDvsDoExemploDaReceita() {
        // FAQ da Receita, pergunta 14: 12ABC34501DE35
        assertThat(DigitoVerificador.modulo11("12ABC34501DE")).isEqualTo(3);
        assertThat(DigitoVerificador.modulo11("12ABC34501DE3")).isEqualTo(5);
    }

    @Test
    void numericoAntigoContinuaComOMesmoDv() {
        assertThat(DigitoVerificador.modulo11("111111110001")).isEqualTo(9);
        assertThat(DigitoVerificador.modulo11("1111111100019")).isEqualTo(1);
    }

    @Test
    void restoZeroOuUmViraZero() {
        // "0": soma 0, resto 0 → 0
        assertThat(DigitoVerificador.modulo11("0")).isZero();
        // "5": 5*2 = 10, resto 10 → DV 11 - 10 = 1
        assertThat(DigitoVerificador.modulo11("5")).isEqualTo(1);
        // "6": 6*2 = 12, resto 1 → 0
        assertThat(DigitoVerificador.modulo11("6")).isZero();
    }

    @Test
    void chaveNumericaEAlfanumerica() {
        assertThat(DigitoVerificador.modulo11(
                "352604" + "12ABC34501DE35" + "55001000000001100000001")).isEqualTo(3);
        // chave 100% numérica (3520 + 01 + 11111111000191 + 55 001 000000001 1 00000001) → DV 0
        assertThat(DigitoVerificador.modulo11(
                "3520011111111100019155001000000001100000001")).isZero();
    }
}
