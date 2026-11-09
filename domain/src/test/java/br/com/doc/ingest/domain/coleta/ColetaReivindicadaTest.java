package br.com.doc.ingest.domain.coleta;

import br.com.doc.ingest.domain.adapter.Cursor;
import br.com.doc.ingest.domain.model.Ambiente;
import br.com.doc.ingest.domain.model.Cnpj;
import br.com.doc.ingest.domain.model.FonteId;
import br.com.doc.ingest.domain.model.Nsu;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ColetaReivindicada (resultado do claim)")
class ColetaReivindicadaTest {

    private static final PosicaoColeta POSICAO = new PosicaoColeta(
            new ChaveColeta(new FonteId("NFE_AN"), new Cnpj("12345678000195"), Ambiente.HOMOLOGACAO),
            Cursor.inicial(), new Nsu(0), 0L);
    private static final Instant ATE = Instant.parse("2026-11-09T12:00:00Z");

    @Test
    @DisplayName("guarda posição, dono e fim do lease")
    void guardaCampos() {
        var c = new ColetaReivindicada(POSICAO, "sched-1", ATE);

        assertThat(c.posicao()).isEqualTo(POSICAO);
        assertThat(c.dono()).isEqualTo("sched-1");
        assertThat(c.leaseAte()).isEqualTo(ATE);
    }

    @Test
    @DisplayName("rejeita nulos e dono vazio")
    void rejeitaNulos() {
        assertThatThrownBy(() -> new ColetaReivindicada(null, "a", ATE))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ColetaReivindicada(POSICAO, null, ATE))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ColetaReivindicada(POSICAO, "a", null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ColetaReivindicada(POSICAO, " ", ATE))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
