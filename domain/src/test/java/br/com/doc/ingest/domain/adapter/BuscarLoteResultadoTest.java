package br.com.doc.ingest.domain.adapter;

import br.com.doc.ingest.domain.blob.BlobRef;
import br.com.doc.ingest.domain.model.Nsu;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("BuscarLoteResultado (referência do envelope bruto)")
class BuscarLoteResultadoTest {

    private static final BlobRef ENVELOPE = new BlobRef(
            "raw/pull/NFE_AN/PRODUCAO/11111111000191/2026/10/28/" + "a".repeat(64) + ".envelope.xml",
            "a".repeat(64));

    @Test
    @DisplayName("construtor antigo de 5 argumentos deixa o envelope vazio")
    void construtorAntigoDaEnvelopeVazio() {
        var resultado = new BuscarLoteResultado(
                List.of(), new Cursor("3"), new Nsu(3), false, new Desfecho.Ok());

        assertThat(resultado.envelope()).isEmpty();
    }

    @Test
    @DisplayName("construtor novo guarda o BlobRef do envelope")
    void construtorNovoGuardaEnvelope() {
        var resultado = new BuscarLoteResultado(
                List.of(), new Cursor("3"), new Nsu(3), false, new Desfecho.Ok(), Optional.of(ENVELOPE));

        assertThat(resultado.envelope()).contains(ENVELOPE);
    }

    @Test
    @DisplayName("envelope nulo é rejeitado (use Optional.empty())")
    void envelopeNuloRejeitado() {
        assertThatThrownBy(() -> new BuscarLoteResultado(
                List.of(), new Cursor("3"), new Nsu(3), false, new Desfecho.Ok(), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("envelope");
    }
}
