package br.com.doc.ingest.domain.catalogo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.doc.ingest.domain.model.Cnpj;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MetadadosDocumentoTest {

    private static final String CPF = "12345678909";
    private static final String CNPJ = "22222222000191";

    private static MetadadosDocumento comDestinatario(String destinatario) {
        return new MetadadosDocumento(
                Optional.of(1),
                Optional.of(123L),
                Optional.of(OffsetDateTime.parse("2026-10-20T10:00:00-03:00")),
                Optional.of(new Cnpj("11111111000191")),
                Optional.of(destinatario),
                Optional.of(new BigDecimal("1234.56")),
                Optional.of("4.00"));
    }

    private static MetadadosDocumento vazios() {
        return new MetadadosDocumento(
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }

    @Test
    void aceitaTodosOsCamposVazios() {
        assertThat(vazios().destinatarioDoc()).isEmpty();
    }

    @Test
    void aceitaDestinatarioCpfOuCnpj() {
        assertThat(comDestinatario(CPF).destinatarioDoc()).contains(CPF);
        assertThat(comDestinatario(CNPJ).destinatarioDoc()).contains(CNPJ);
    }

    @Test
    void toStringMascaraDestinatario() {
        assertThat(comDestinatario(CPF).toString()).doesNotContain(CPF);
        assertThat(comDestinatario(CNPJ).toString()).doesNotContain(CNPJ);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "1234567890", "123456789012", "1234567890123", "123456789012345", "1234567890X"})
    void destinatarioSoComOnzeOuQuatorzeDigitos(String invalido) {
        assertThatThrownBy(() -> comDestinatario(invalido))
                .isInstanceOf(IllegalArgumentException.class)
                .satisfies(e -> {
                    if (!invalido.isEmpty()) {
                        assertThat(e.getMessage()).doesNotContain(invalido);
                    }
                });
    }

    @Test
    void serieNegativaEhRecusada() {
        assertThatThrownBy(() -> new MetadadosDocumento(
                Optional.of(-1), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void numeroNegativoEhRecusado() {
        assertThatThrownBy(() -> new MetadadosDocumento(
                Optional.empty(), Optional.of(-1L), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void valorNegativoEhRecusado() {
        assertThatThrownBy(() -> new MetadadosDocumento(
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.of(new BigDecimal("-0.01")), Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void zeroEhAceitoEmSerieNumeroEValor() {
        var metadados = new MetadadosDocumento(
                Optional.of(0), Optional.of(0L), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.of(BigDecimal.ZERO), Optional.empty());

        assertThat(metadados.serie()).contains(0);
    }

    @Test
    void optionalNuloEhRecusado() {
        assertThatThrownBy(() -> new MetadadosDocumento(
                null, Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void toStringMascaraOEmitente() {
        // inv. 13: o CNPJ do emitente sai mascarado, como o destinatario
        assertThat(comDestinatario(CPF).toString())
                .doesNotContain("11111111000191")
                .contains("emitente=***0191");
    }

    @Test
    void emitenteEDestinatarioAlfanumericos() {
        var metadados = new MetadadosDocumento(
                Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.of(new Cnpj("12ABC34501DE35")),
                Optional.of("12ABC34501DE35"),
                Optional.empty(), Optional.empty());
        assertThat(metadados.emitenteCnpj()).contains(new Cnpj("12ABC34501DE35"));
        assertThat(metadados.destinatarioDoc()).contains("12ABC34501DE35");
        // destinatario nao exige DV (DA-38: na entrada so o formato)
        assertThat(comDestinatario("12ABC34501DE99").destinatarioDoc()).contains("12ABC34501DE99");
        assertThat(metadados.toString()).doesNotContain("12ABC34501DE35");
    }

    @ParameterizedTest
    @ValueSource(strings = {"1234567890A", "A2345678909", "12abc34501de35", "12ABC34501DEA5"})
    void destinatarioCpfContinuaSoNumerico(String invalido) {
        assertThatThrownBy(() -> comDestinatario(invalido))
                .isInstanceOf(IllegalArgumentException.class)
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain(invalido));
    }
}
