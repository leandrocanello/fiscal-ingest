package br.com.doc.ingest.domain.outbox;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MensagemOutboxTest {

    private static MensagemOutbox com(String topico, String chave, int versao, String idem, String payload) {
        return new MensagemOutbox(topico, chave, versao, idem, payload);
    }

    @Test
    void aceitaMensagemValida() {
        var m = com("docs.coletados", "12345678000199", 1, "nsu-1", "{\"nsu\":1}");
        assertThat(m.topico()).isEqualTo("docs.coletados");
        assertThat(m.versaoSchema()).isEqualTo(1);
    }

    @Test
    void rejeitaNulosEBrancos() {
        assertThatThrownBy(() -> com(null, "c", 1, "i", "{}")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> com("t", null, 1, "i", "{}")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> com("t", "c", 1, null, "{}")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> com("t", "c", 1, "i", null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> com(" ", "c", 1, "i", "{}")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> com("t", "", 1, "i", "{}")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> com("t", "c", 1, " ", "{}")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> com("t", "c", 1, "i", "  ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejeitaVersaoSchemaMenorQueUm() {
        assertThatThrownBy(() -> com("t", "c", 0, "i", "{}")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejeitaPayloadAcimaDoLimite() {
        String noLimite = "x".repeat(MensagemOutbox.LIMITE_PAYLOAD_BYTES);
        assertThat(com("t", "c", 1, "i", noLimite).payloadJson()).hasSize(MensagemOutbox.LIMITE_PAYLOAD_BYTES);
        String acima = "x".repeat(MensagemOutbox.LIMITE_PAYLOAD_BYTES + 1);
        assertThatThrownBy(() -> com("t", "c", 1, "i", acima)).isInstanceOf(IllegalArgumentException.class);
    }
}
