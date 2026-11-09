package br.com.doc.ingest.domain.coleta;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PedidoReivindicacao (claim da fila de coleta)")
class PedidoReivindicacaoTest {

    @Test
    @DisplayName("aceita dono, lease e limite positivos")
    void aceitaPedidoValido() {
        var pedido = new PedidoReivindicacao("sched-1", Duration.ofMinutes(5), 20);

        assertThat(pedido.dono()).isEqualTo("sched-1");
        assertThat(pedido.lease()).isEqualTo(Duration.ofMinutes(5));
        assertThat(pedido.limite()).isEqualTo(20);
    }

    @Test
    @DisplayName("rejeita dono vazio ou nulo")
    void rejeitaDonoVazioOuNulo() {
        assertThatThrownBy(() -> new PedidoReivindicacao(" ", Duration.ofMinutes(1), 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PedidoReivindicacao("", Duration.ofMinutes(1), 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PedidoReivindicacao(null, Duration.ofMinutes(1), 1))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("rejeita lease não positivo")
    void rejeitaLeaseNaoPositivo() {
        assertThatThrownBy(() -> new PedidoReivindicacao("a", Duration.ZERO, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PedidoReivindicacao("a", Duration.ofSeconds(-1), 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("rejeita limite não positivo")
    void rejeitaLimiteNaoPositivo() {
        assertThatThrownBy(() -> new PedidoReivindicacao("a", Duration.ofMinutes(1), 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PedidoReivindicacao("a", Duration.ofMinutes(1), -3))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("rejeita lease nulo")
    void rejeitaNulos() {
        assertThatThrownBy(() -> new PedidoReivindicacao("a", null, 1))
                .isInstanceOf(NullPointerException.class);
    }
}
