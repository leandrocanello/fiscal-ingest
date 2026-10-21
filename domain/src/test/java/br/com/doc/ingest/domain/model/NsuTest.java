package br.com.doc.ingest.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testes do value object Nsu (Número Sequencial Único).
 *
 * Invariantes validadas:
 * - NSU deve ser >= 0 (zero é o NSU inicial/vazio na SEFAZ).
 * - NSU é imutável (record).
 * - Não deve ser possível criar NSU negativo.
 * - equals/hashCode baseados no valor numérico.
 * - Comparação: ehMaiorQue, ehMenorQue para controle de retrocesso.
 */
@DisplayName("Nsu (value object)")
class NsuTest {

    @Nested
    @DisplayName("Criação")
    class Criacao {

        @Test
        @DisplayName("deve criar NSU válido com valor zero (NSU inicial)")
        void deveCriarNsuZero() {
            Nsu nsu = new Nsu(0L);
            assertThat(nsu.valor()).isEqualTo(0L);
        }

        @Test
        @DisplayName("deve criar NSU válido com valor positivo")
        void deveCriarNsuPositivo() {
            Nsu nsu = new Nsu(123456L);
            assertThat(nsu.valor()).isEqualTo(123456L);
        }

        @ParameterizedTest
        @ValueSource(longs = {-1L, -100L, Long.MIN_VALUE})
        @DisplayName("deve lançar exceção ao criar NSU com valor negativo")
        void deveLancarExcecaoParaNsuNegativo(long valorInvalido) {
            assertThatThrownBy(() -> new Nsu(valorInvalido))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("NSU");
        }
    }

    @Nested
    @DisplayName("Igualdade (value object)")
    class Igualdade {

        @Test
        @DisplayName("dois NSUs com mesmo valor devem ser iguais")
        void devemSerIguais() {
            Nsu a = new Nsu(100L);
            Nsu b = new Nsu(100L);
            assertThat(a).isEqualTo(b);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }

        @Test
        @DisplayName("dois NSUs com valores diferentes devem ser diferentes")
        void devemSerDiferentes() {
            Nsu a = new Nsu(100L);
            Nsu b = new Nsu(200L);
            assertThat(a).isNotEqualTo(b);
        }
    }

    @Nested
    @DisplayName("Comparação (controle de retrocesso)")
    class Comparacao {

        @Test
        @DisplayName("ehMaiorQue deve retornar true quando NSU é maior que o outro")
        void ehMaiorQue_deveRetornarTrue() {
            Nsu maior = new Nsu(200L);
            Nsu menor = new Nsu(100L);
            assertThat(maior.ehMaiorQue(menor)).isTrue();
        }

        @Test
        @DisplayName("ehMaiorQue deve retornar false quando NSU é menor ou igual")
        void ehMaiorQue_deveRetornarFalse() {
            Nsu maior = new Nsu(100L);
            Nsu menor = new Nsu(200L);
            assertThat(maior.ehMaiorQue(menor)).isFalse();

            Nsu igual = new Nsu(100L);
            assertThat(maior.ehMaiorQue(igual)).isFalse();
        }

        @Test
        @DisplayName("ehMenorQue deve retornar true quando NSU é menor que o outro")
        void ehMenorQue_deveRetornarTrue() {
            Nsu menor = new Nsu(100L);
            Nsu maior = new Nsu(200L);
            assertThat(menor.ehMenorQue(maior)).isTrue();
        }

        @Test
        @DisplayName("proximo deve retornar o NSU seguinte (valor + 1)")
        void proximo_deveIncrementar() {
            Nsu atual = new Nsu(100L);
            Nsu proximo = atual.proximo();
            assertThat(proximo.valor()).isEqualTo(101L);
        }
    }

    @Test
    @DisplayName("deve ter representação toString legível")
    void deveTerToStringLegivel() {
        Nsu nsu = new Nsu(42L);
        assertThat(nsu.toString()).contains("42");
    }
}
