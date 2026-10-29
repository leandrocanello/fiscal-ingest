package br.com.doc.ingest.domain.adapter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TipoManifestacao (manifestação do destinatário, sem código da SEFAZ)")
class TipoManifestacaoTest {

    @Test
    @DisplayName("prevê os quatro tipos de manifestação do destinatário")
    void quatroTiposPrevistos() {
        assertThat(TipoManifestacao.values()).containsExactly(
            TipoManifestacao.CIENCIA,
            TipoManifestacao.CONFIRMACAO,
            TipoManifestacao.DESCONHECIMENTO,
            TipoManifestacao.OPERACAO_NAO_REALIZADA);
    }

    @Test
    @DisplayName("só a operação não realizada exige justificativa")
    void soOperacaoNaoRealizadaExigeJustificativa() {
        assertThat(TipoManifestacao.OPERACAO_NAO_REALIZADA.exigeJustificativa()).isTrue();
        assertThat(TipoManifestacao.CIENCIA.exigeJustificativa()).isFalse();
        assertThat(TipoManifestacao.CONFIRMACAO.exigeJustificativa()).isFalse();
        assertThat(TipoManifestacao.DESCONHECIMENTO.exigeJustificativa()).isFalse();
    }
}
