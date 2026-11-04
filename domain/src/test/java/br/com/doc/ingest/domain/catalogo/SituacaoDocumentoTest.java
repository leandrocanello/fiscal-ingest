package br.com.doc.ingest.domain.catalogo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SituacaoDocumentoTest {

    /** A fusão do documento (DA-27) depende desta ordem: a situação nunca regride. */
    @Test
    void ordemDeclaradaVaiDoResumoADenegada() {
        assertThat(SituacaoDocumento.values()).containsExactly(
                SituacaoDocumento.SO_RESUMO,
                SituacaoDocumento.AUTORIZADA,
                SituacaoDocumento.CANCELADA,
                SituacaoDocumento.DENEGADA);
    }
}
