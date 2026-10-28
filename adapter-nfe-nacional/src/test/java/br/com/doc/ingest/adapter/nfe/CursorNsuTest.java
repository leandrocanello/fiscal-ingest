package br.com.doc.ingest.adapter.nfe;

import br.com.doc.ingest.domain.adapter.Cursor;
import br.com.doc.ingest.domain.model.Nsu;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("CursorNsu (Cursor opaco <-> ultNSU, DA-14)")
class CursorNsuTest {

    @Test
    @DisplayName("cursor inicial (token vazio) é o NSU 0")
    void cursorInicialEhNsuZero() {
        assertThat(CursorNsu.nsu(Cursor.inicial())).isEqualTo(new Nsu(0));
    }

    @Test
    @DisplayName("token decimal vira o NSU correspondente")
    void tokenDecimalViraNsu() {
        assertThat(CursorNsu.nsu(new Cursor("123"))).isEqualTo(new Nsu(123));
    }

    @Test
    @DisplayName("de(Nsu) gera o token decimal sem zeros à esquerda")
    void deNsuGeraTokenDecimal() {
        assertThat(CursorNsu.de(new Nsu(7))).isEqualTo(new Cursor("7"));
    }

    @Test
    @DisplayName("ida e volta preserva o NSU")
    void idaEVoltaPreservaNsu() {
        var nsu = new Nsu(999_999_999_999_999L);
        assertThat(CursorNsu.nsu(CursorNsu.de(nsu))).isEqualTo(nsu);
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "-1", "12a", " 1", "1.0", "99999999999999999999"})
    @DisplayName("token não numérico, negativo ou fora da faixa é rejeitado")
    void tokenInvalidoRejeitado(String token) {
        assertThatThrownBy(() -> CursorNsu.nsu(new Cursor(token)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a mensagem de erro não ecoa o token inteiro")
    void mensagemNaoEcoaToken() {
        var token = "SEGREDO-XYZ-" + "x".repeat(200);
        assertThatThrownBy(() -> CursorNsu.nsu(new Cursor(token)))
                .isInstanceOf(IllegalArgumentException.class)
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("SEGREDO-XYZ"));
    }
}
