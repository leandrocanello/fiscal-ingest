package br.com.doc.ingest.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class FixturesFiscaisTest {

    @Test
    void cnpjGeradoTemDvCerto() {
        assertThat(FixturesFiscais.cnpj("12ABC34501DE")).isEqualTo("12ABC34501DE35");
        assertThat(FixturesFiscais.cnpj("123456780001")).isEqualTo("12345678000195");
        assertThat(FixturesFiscais.cnpj("987654320001")).isEqualTo("98765432000198");
        assertThat(FixturesFiscais.cnpj("998887770001")).isEqualTo("99888777000100");
    }

    @Test
    void chaveGeradaTemDvCerto() {
        assertThat(FixturesFiscais.chave("352604" + "12ABC34501DE35" + "55001000000001100000001"))
                .isEqualTo("352604" + "12ABC34501DE35" + "55001000000001100000001" + "3");
        assertThat(FixturesFiscais.chave("3520011111111100019155001000000001100000001"))
                .endsWith("0")
                .hasSize(44);
    }

    @Test
    void baseDeTamanhoErradoEhRecusada() {
        assertThatThrownBy(() -> FixturesFiscais.cnpj("1234")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FixturesFiscais.chave("1234")).isInstanceOf(IllegalArgumentException.class);
    }
}
