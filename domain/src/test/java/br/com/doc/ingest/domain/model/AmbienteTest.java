package br.com.doc.ingest.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Ambiente")
class AmbienteTest {

    @Test
    @DisplayName("dois ambientes previstos, sem código numérico de protocolo")
    void doisAmbientesPrevistos() {
        assertThat(Ambiente.values()).containsExactly(Ambiente.PRODUCAO, Ambiente.HOMOLOGACAO);
    }
}
