package br.com.doc.ingest.adapter.nfe.evento;

import br.com.doc.ingest.domain.adapter.TipoManifestacao;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("IdEvento da manifestação")
class IdEventoTest {

    /** Chave sintética: UF 35, AAMM 2610, CNPJ fictício, mod 55, série 1, nNF 123, tpEmis 1, cNF fictício + DV. */
    private static final String CHAVE = chaveSintetica("3526101234567800019955001000000123100000001");

    @Test
    @DisplayName("valor é \"ID\" + tpEvento + chave de 44 dígitos + seq em 2 dígitos")
    void formatoIdTpEventoChaveSeq() {
        IdEvento id = new IdEvento(TipoManifestacao.CIENCIA, CHAVE, 1);

        assertThat(id.valor()).isEqualTo("ID210210" + CHAVE + "01");
        assertThat(id.valor()).hasSize(54);
        assertThat(new IdEvento(TipoManifestacao.CIENCIA, CHAVE, 12).valor()).endsWith("12");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "352610123456780001995500100000012310000000",    // 42 dígitos
            "3526101234567800019955001000000123100000001",   // 43 dígitos
            "352610123456780001995500100000012310000000123", // 45 dígitos
            "352610123456780001995500100000012310000000A1", // 44 com letra
            ""})
    @DisplayName("rejeita chave que não tem exatamente 44 dígitos numéricos")
    void rejeitaChaveComMenosDe44DigitosOuNaoNumerica(String chave) {
        assertThatThrownBy(() -> new IdEvento(TipoManifestacao.CIENCIA, chave, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new IdEvento(TipoManifestacao.CIENCIA, null, 1))
                .isInstanceOf(NullPointerException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 100})
    @DisplayName("rejeita seq fora de 1 a 99")
    void rejeitaSeqForaDe1a99(int seq) {
        assertThatThrownBy(() -> new IdEvento(TipoManifestacao.CIENCIA, CHAVE, seq))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(String.valueOf(seq));
    }

    /** Acrescenta o DV (módulo 11, pesos 2..9 da direita para a esquerda) aos 43 primeiros dígitos. */
    private static String chaveSintetica(String sem) {
        int soma = 0;
        int peso = 2;
        for (int i = sem.length() - 1; i >= 0; i--) {
            soma += (sem.charAt(i) - '0') * peso;
            peso = peso == 9 ? 2 : peso + 1;
        }
        int resto = soma % 11;
        int dv = resto < 2 ? 0 : 11 - resto;
        return sem + dv;
    }
}
