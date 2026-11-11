package br.com.doc.ingest.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

class CnpjTest {

    @Test
    void aceitaNumericoAntigo() {
        assertThat(new Cnpj("11111111000191").valor()).isEqualTo("11111111000191");
        assertThat(Cnpj.valido("11222333000181")).isTrue();
    }

    @Test
    void aceitaExemploDaReceita() {
        // FAQ da Receita, pergunta 14
        assertThat(new Cnpj("12ABC34501DE35").valor()).isEqualTo("12ABC34501DE35");
    }

    @Test
    void aceitaRaizZeradaComDvCerto() {
        assertThat(Cnpj.valido("00000000000191")).isTrue();
    }

    @Test
    void recusaQuatorzeZeros() {
        // Anexo I da NT Conjunta 2025.001: o DV de 14 zeros "confere", mas o valor e recusado
        assertThat(Cnpj.valido("00000000000000")).isFalse();
        assertThatThrownBy(() -> new Cnpj("00000000000000"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"12ABC34501DE36", "12ABC34501DE45", "11111111000190", "11111111000181"})
    void recusaDvErrado(String valor) {
        assertThat(Cnpj.valido(valor)).isFalse();
        assertThatThrownBy(() -> new Cnpj(valor)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"12ABC34501DEA5", "12ABC34501DE3A"})
    void recusaLetraNoDv(String valor) {
        assertThat(Cnpj.valido(valor)).isFalse();
        assertThatThrownBy(() -> new Cnpj(valor)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"12abc34501de35", "12.ABC.345/01DE-35", "11.111.111/0001-91", "", "1111111100019"})
    void recusaMinusculaEMascara(String valor) {
        assertThat(Cnpj.valido(valor)).isFalse();
        assertThatThrownBy(() -> new Cnpj(valor)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "1111111100019١", // digito arabico-indico 1
        "Ａ2ABC34501DE35", // A de largura total
        "12ÇBC34501DE35" // C cedilha
    })
    void recusaNaoAscii(String valor) {
        assertThat(Cnpj.valido(valor)).isFalse();
        assertThatThrownBy(() -> new Cnpj(valor)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void recusaNulo() {
        assertThat(Cnpj.valido(null)).isFalse();
        assertThatThrownBy(() -> new Cnpj(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void invalidoEhRecusadoSemEcoarOValor() {
        // inv. 13: o CNPJ e dado de cliente e nao vai para mensagem de excecao
        assertThatThrownBy(() -> new Cnpj("12ABC34501DE36"))
                .isInstanceOf(IllegalArgumentException.class)
                .message()
                .doesNotContain("12ABC34501DE36")
                .doesNotContain("12ABC34501DE")
                .doesNotContain("DE36");
    }

    @Test
    void aColisaoAe6NaoEhDetectavelPeloDv() {
        // 'A' vale 17 e '6' vale 6: com o mesmo peso, 17 - 6 = 11 e o resto mod 11 nao muda.
        // Limite documentado do algoritmo da NT: trocar A por 6 na base nao altera o DV.
        assertThat(Cnpj.valido("12ABC34501DE35")).isTrue();
        assertThat(Cnpj.valido("126BC34501DE35")).isTrue();
    }

    @Test
    void validoNaoLancaExcecao() {
        assertThatCode(() -> Cnpj.valido("qualquer coisa")).doesNotThrowAnyException();
        assertThatCode(() -> new Cnpj(FixturesFiscais.cnpj("ZZZZZZZZ0001"))).doesNotThrowAnyException();
    }
}
