package br.com.doc.ingest.adapter.nfe.doczip;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("MetadadosDfe")
class MetadadosDfeTest {

    private static final String EMITENTE = "12ABC34501DE35";
    private static final String DESTINATARIO = "11222333000181";
    private static final String AUTORIZADO = "33444555000172";
    private static final String TRANSPORTADOR = "44555666000163";
    private static final String AUTOR_EVENTO = "55666777000154";

    @Test
    @DisplayName("toStringMascaraOsCnpjs")
    void toStringMascaraOsCnpjs() {
        var m = new MetadadosDfe(
                "35261011111111000191550010000001231123456780", "55",
                Optional.of(EMITENTE), Optional.of(DESTINATARIO), List.of(AUTORIZADO),
                Optional.of(TRANSPORTADOR), Optional.of(1), Optional.of(123L), Optional.empty(),
                Optional.empty(), Optional.of("210210"), Optional.of(1), Optional.empty(),
                Optional.of("1"), Optional.of(AUTOR_EVENTO));

        String texto = m.toString();

        // inv. 13: nenhum CNPJ aparece inteiro; só os 4 últimos caracteres
        for (String cnpj : List.of(EMITENTE, DESTINATARIO, AUTORIZADO, TRANSPORTADOR, AUTOR_EVENTO)) {
            assertThat(texto).doesNotContain(cnpj);
        }
        assertThat(texto).contains("emitente=***DE35").contains("destinatario=***0181");
    }
}
