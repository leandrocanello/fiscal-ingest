package br.com.doc.ingest.domain.cofre;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PoliticaAlertaVencimento (DA-53)")
class PoliticaAlertaVencimentoTest {

    private static final Instant AGORA = Instant.parse("2026-11-09T12:00:00Z");
    private static final PoliticaAlertaVencimento PADRAO = PoliticaAlertaVencimento.padrao();

    private static Duration dias(long n) {
        return Duration.ofDays(n);
    }

    @Test
    @DisplayName("fora da janela (61 dias) não alerta")
    void foraDaJanelaNaoAlerta() {
        assertThat(PADRAO.marcoDevido(AGORA.plus(dias(61)), AGORA, Set.of())).isEmpty();
    }

    @Test
    @DisplayName("cada marco no limite exato já alerta")
    void cadaMarcoNoLimiteExato() {
        for (long marco : new long[] {60, 30, 15, 7, 1}) {
            assertThat(PADRAO.marcoDevido(AGORA.plus(dias(marco)), AGORA, Set.of()))
                    .as("marco %d", marco).contains(dias(marco));
        }
    }

    @Test
    @DisplayName("entre marcos devolve o marco mais urgente cruzado")
    void entreMarcosDevolveOMarcoMaisUrgenteCruzado() {
        assertThat(PADRAO.marcoDevido(AGORA.plus(dias(20)), AGORA, Set.of())).contains(dias(30));
        assertThat(PADRAO.marcoDevido(AGORA.plus(dias(5)), AGORA, Set.of())).contains(dias(7));
        assertThat(PADRAO.marcoDevido(AGORA.plus(Duration.ofHours(3)), AGORA, Set.of())).contains(dias(1));
    }

    @Test
    @DisplayName("marco já emitido não repete")
    void marcoJaEmitidoNaoRepete() {
        assertThat(PADRAO.marcoDevido(AGORA.plus(dias(20)), AGORA, Set.of(dias(30)))).isEmpty();
    }

    @Test
    @DisplayName("pulou vários marcos emite só o mais urgente")
    void pulouVariosMarcosEmiteSoOMaisUrgente() {
        assertThat(PADRAO.marcoDevido(AGORA.plus(dias(6)), AGORA, Set.of())).contains(dias(7));
        assertThat(PADRAO.marcoDevido(AGORA.plus(dias(6)), AGORA, Set.of(dias(60), dias(30))))
                .contains(dias(7));
    }

    @Test
    @DisplayName("vencido não é alerta de vencimento")
    void vencidoNaoEhAlertaDeVencimento() {
        assertThat(PADRAO.marcoDevido(AGORA.minusSeconds(1), AGORA, Set.of())).isEqualTo(Optional.empty());
        assertThat(PADRAO.marcoDevido(AGORA, AGORA, Set.of())).contains(dias(1));
    }

    @Test
    @DisplayName("marcos configuráveis: ordenados, positivos e sem repetição")
    void marcosConfiguraveis() {
        var custom = new PoliticaAlertaVencimento(List.of(dias(10), dias(2)));
        assertThat(custom.marcoDevido(AGORA.plus(dias(9)), AGORA, Set.of())).contains(dias(10));
        assertThat(custom.marcoDevido(AGORA.plus(dias(11)), AGORA, Set.of())).isEmpty();
        assertThat(PADRAO.marcos()).containsExactly(dias(60), dias(30), dias(15), dias(7), dias(1));

        assertThatThrownBy(() -> new PoliticaAlertaVencimento(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PoliticaAlertaVencimento(List.of(dias(2), dias(10))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PoliticaAlertaVencimento(List.of(dias(10), dias(10))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PoliticaAlertaVencimento(List.of(dias(10), Duration.ZERO)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
