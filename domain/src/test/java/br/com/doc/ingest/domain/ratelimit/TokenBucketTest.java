package br.com.doc.ingest.domain.ratelimit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes do TokenBucket — lógica PURA de rate-limiting.
 *
 * Simula os limites da SEFAZ:
 * - distNSU: ~1 consulta/hora por CNPJ
 * - consNSU: ~20 consultas/hora por CNPJ
 *
 * O bucket:
 * - Tem capacidade máxima de tokens (ex: 1 para distNSU, 20 para consNSU).
 * - Recarga 1 token a cada intervalo fixo (ex: 1h para distNSU, 3min para consNSU).
 * - tentarConsumir() devolve OK ou LIMITE_COM retryApos.
 */
@DisplayName("TokenBucket (rate-limit puro)")
class TokenBucketTest {

    @Nested
    @DisplayName("Consumo dentro da capacidade")
    class ConsumoDentroDaCapacidade {

        @Test
        @DisplayName("deve permitir primeiro consumo quando bucket tem tokens")
        void devePermitirPrimeiroConsumo() {
            Instant inicio = Instant.parse("2026-07-11T12:00:00Z");
            TokenBucket bucket = new TokenBucket(
                    1,                      // capacidade: 1 token (distNSU)
                    Duration.ofHours(1),    // recarga: 1 token/hora
                    Clock.fixed(inicio, ZoneId.of("UTC"))
            );

            ResultadoConsumo resultado = bucket.tentarConsumir();

            assertThat(resultado.permitido()).isTrue();
            assertThat(resultado.retryApos()).isNull();
        }

        @Test
        @DisplayName("deve permitir múltiplos consumos quando há capacidade")
        void devePermitirMultiplosConsumosComCapacidade() {
            Instant inicio = Instant.parse("2026-07-11T12:00:00Z");
            TokenBucket bucket = new TokenBucket(
                    20,                     // capacidade: 20 tokens (consNSU)
                    Duration.ofMinutes(3),  // recarga: 1 token a cada 3 min
                    Clock.fixed(inicio, ZoneId.of("UTC"))
            );

            ResultadoConsumo primeiro = bucket.tentarConsumir();
            ResultadoConsumo segundo = bucket.tentarConsumir();

            assertThat(primeiro.permitido()).isTrue();
            assertThat(segundo.permitido()).isTrue();
        }
    }

    @Nested
    @DisplayName("Limite atingido")
    class LimiteAtingido {

        @Test
        @DisplayName("deve negar segundo consumo quando capacidade é 1 e não houve recarga")
        void deveNegarSegundoConsumoSemRecarga() {
            Instant inicio = Instant.parse("2026-07-11T12:00:00Z");
            TokenBucket bucket = new TokenBucket(
                    1,
                    Duration.ofHours(1),
                    Clock.fixed(inicio, ZoneId.of("UTC"))
            );

            bucket.tentarConsumir();              // consome o único token
            ResultadoConsumo segundo = bucket.tentarConsumir();

            assertThat(segundo.permitido()).isFalse();
            assertThat(segundo.retryApos()).isEqualTo(Duration.ofHours(1));
        }

        @Test
        @DisplayName("deve calcular retryApos corretamente (próxima recarga)")
        void deveCalcularRetryAposCorretamente() {
            Instant inicio = Instant.parse("2026-07-11T12:00:00Z");
            TokenBucket bucket = new TokenBucket(
                    1,
                    Duration.ofMinutes(30),
                    Clock.fixed(inicio, ZoneId.of("UTC"))
            );

            bucket.tentarConsumir();
            ResultadoConsumo negado = bucket.tentarConsumir();

            assertThat(negado.permitido()).isFalse();
            assertThat(negado.retryApos()).isEqualTo(Duration.ofMinutes(30));
        }
    }

    @Nested
    @DisplayName("Recarga ao avançar o relógio")
    class Recarga {

        @Test
        @DisplayName("deve permitir novamente após recarga completa")
        void devePermitirAposRecarga() {
            Instant inicio = Instant.parse("2026-07-11T12:00:00Z");
            Clock clock = Clock.fixed(inicio, ZoneId.of("UTC"));
            TokenBucket bucket = new TokenBucket(
                    1,
                    Duration.ofHours(1),
                    clock
            );

            bucket.tentarConsumir();                   // esgota
            assertThat(bucket.tentarConsumir().permitido()).isFalse();  // negado

            // Avança o relógio 1 hora
            bucket.setClock(Clock.fixed(inicio.plus(Duration.ofHours(1)), ZoneId.of("UTC")));

            ResultadoConsumo aposRecarga = bucket.tentarConsumir();
            assertThat(aposRecarga.permitido()).isTrue();
        }

        @Test
        @DisplayName("não deve permitir recarga parcial antes do intervalo completo")
        void naoDevePermitirRecargaParcial() {
            Instant inicio = Instant.parse("2026-07-11T12:00:00Z");
            TokenBucket bucket = new TokenBucket(
                    1,
                    Duration.ofHours(1),
                    Clock.fixed(inicio, ZoneId.of("UTC"))
            );

            bucket.tentarConsumir();  // esgota

            // Avança só 30 min (meia recarga)
            bucket.setClock(Clock.fixed(inicio.plus(Duration.ofMinutes(30)), ZoneId.of("UTC")));

            ResultadoConsumo parcial = bucket.tentarConsumir();
            assertThat(parcial.permitido()).isFalse();
            assertThat(parcial.retryApos()).isEqualTo(Duration.ofMinutes(30));
        }
    }
}
