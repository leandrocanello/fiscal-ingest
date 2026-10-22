package br.com.doc.ingest.domain.ratelimit;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.LongRange;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Propriedades do {@link TokenBucket} (CLAUDE.md §2.2 invariante 2 / §3.2 — respeitar o limite
 * por CNPJ; ADR-026): o balde nunca libera além do teto.
 */
class TokenBucketPropriedadesTest {

    private static final Instant INICIO = Instant.parse("2026-01-01T00:00:00Z");

    /** Fração do intervalo (0..3) em passos de 1/10, para cobrir avanços parciais e múltiplos. */
    @Provide
    Arbitrary<List<Integer>> decimosDeIntervalo() {
        return Arbitraries.integers().between(0, 30).list().ofMaxSize(100);
    }

    @Property
    void nuncaLiberaMaisQueCapacidadeMaisRecargas(
            @ForAll @LongRange(min = 1, max = 50) long capacidade,
            @ForAll @LongRange(min = 1, max = 7_200_000) long intervaloMs,
            @ForAll("decimosDeIntervalo") List<Integer> passos) {
        RelogioManual relogio = new RelogioManual(INICIO);
        Duration intervalo = Duration.ofMillis(intervaloMs);
        TokenBucket balde = new TokenBucket(capacidade, intervalo, relogio);

        long permitidos = 0;
        long tempoTotalMs = 0;
        for (int decimos : passos) {
            long avancoMs = intervaloMs * decimos / 10;
            relogio.avancar(Duration.ofMillis(avancoMs));
            tempoTotalMs += avancoMs;

            if (balde.tentarConsumir().permitido()) {
                permitidos++;
            }

            assertThat(permitidos).isLessThanOrEqualTo(capacidade + tempoTotalMs / intervaloMs);
            assertThat(balde.tokensDisponiveis()).isBetween(0L, capacidade);
        }
    }

    @Property
    void retryAposEhHonesto(
            @ForAll @LongRange(min = 1, max = 50) long capacidade,
            @ForAll @LongRange(min = 1, max = 7_200_000) long intervaloMs,
            @ForAll("decimosDeIntervalo") List<Integer> passos) {
        RelogioManual relogio = new RelogioManual(INICIO);
        Duration intervalo = Duration.ofMillis(intervaloMs);
        TokenBucket balde = new TokenBucket(capacidade, intervalo, relogio);

        for (int decimos : passos) {
            relogio.avancar(Duration.ofMillis(intervaloMs * decimos / 10));

            ResultadoConsumo r = balde.tentarConsumir();
            if (r.permitido()) {
                assertThat(r.retryApos()).isNull();
            } else {
                assertThat(r.retryApos()).isNotNull();
                assertThat(r.retryApos()).isPositive().isLessThanOrEqualTo(intervalo);

                relogio.avancar(r.retryApos());
                assertThat(balde.tentarConsumir().permitido()).isTrue();
            }
        }
    }
}
