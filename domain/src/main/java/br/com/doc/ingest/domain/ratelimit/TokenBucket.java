package br.com.doc.ingest.domain.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Token bucket — lógica PURA de rate-limiting para respeitar os limites da SEFAZ.
 *
 * <p><b>Modelo:</b> um balde com {@code capacidade} tokens que recarrega
 * 1 token a cada {@code intervaloRecarga}. Cada chamada a
 * {@link #tentarConsumir()} consome 1 token se disponível.
 *
 * <p><b>Exemplos de uso:</b>
 * <ul>
 *   <li>distNSU: capacidade=1, intervaloRecarga=1h (~1 consulta/hora)</li>
 *   <li>consNSU: capacidade=20, intervaloRecarga=3min (~20/hora)</li>
 * </ul>
 *
 * <p>Esta classe é <b>mutável</b> (mantém estado de tokens consumidos),
 * mas usa {@link Clock} injetável para determinismo em testes.
 * <b>Não é thread-safe</b> — a coordenação de concorrência fica no adapter/infra.
 */
public class TokenBucket {

    private final long capacidade;
    private final Duration intervaloRecarga;
    private Clock clock;

    // Estado interno
    private long tokensDisponiveis;
    private Instant ultimoConsumo;

    /**
     * @param capacidade        número máximo de tokens (ex: 1 para distNSU)
     * @param intervaloRecarga  tempo para regenerar 1 token (ex: 1h para distNSU); mínimo 1 ms
     * @param clock             fonte de tempo (injetável para testes)
     */
    public TokenBucket(long capacidade, Duration intervaloRecarga, Clock clock) {
        if (capacidade < 1) {
            throw new IllegalArgumentException("Capacidade deve ser >= 1: " + capacidade);
        }
        if (intervaloRecarga == null || intervaloRecarga.isZero() || intervaloRecarga.isNegative()) {
            throw new IllegalArgumentException("Intervalo de recarga deve ser positivo");
        }
        if (intervaloRecarga.toMillis() < 1) {
            throw new IllegalArgumentException(
                    "Intervalo de recarga deve ser >= 1 ms (resolução do bucket): " + intervaloRecarga);
        }
        this.capacidade = capacidade;
        this.intervaloRecarga = intervaloRecarga;
        this.clock = clock;
        this.tokensDisponiveis = capacidade; // começa cheio
        this.ultimoConsumo = Instant.now(clock);
    }

    /**
     * Tenta consumir 1 token.
     *
     * @return {@link ResultadoConsumo#ok()} se permitido,
     *         ou {@link ResultadoConsumo#limite(Duration)} com tempo até próximo token
     */
    public ResultadoConsumo tentarConsumir() {
        recarregar();

        if (tokensDisponiveis >= 1) {
            tokensDisponiveis--;
            ultimoConsumo = Instant.now(clock);
            return ResultadoConsumo.ok();
        }

        // Sem token — calcula quanto tempo até o próximo
        Duration retryApos = calcularRetryApos();
        return ResultadoConsumo.limite(retryApos);
    }

    /**
     * Permite trocar o Clock (usado em testes para simular avanço do tempo).
     */
    public void setClock(Clock clock) {
        this.clock = clock;
    }

    /**
     * Recarrega tokens com base no tempo decorrido desde o último consumo.
     */
    private void recarregar() {
        Instant agora = Instant.now(clock);
        Duration decorrido = Duration.between(ultimoConsumo, agora);

        // Quantos tokens inteiros se passaram desde o último consumo?
        long tokensGerados = decorrido.toMillis() / intervaloRecarga.toMillis();

        if (tokensGerados > 0) {
            tokensDisponiveis = Math.min(capacidade, tokensDisponiveis + tokensGerados);
        }
    }

    /**
     * Calcula quanto tempo falta até o próximo token estar disponível.
     */
    private Duration calcularRetryApos() {
        Instant agora = Instant.now(clock);
        Duration decorrido = Duration.between(ultimoConsumo, agora);

        long millisDecorridos = decorrido.toMillis();
        long millisRecarga = intervaloRecarga.toMillis();
        long millisRestantes = millisRecarga - (millisDecorridos % millisRecarga);

        return Duration.ofMillis(millisRestantes);
    }

    /** Apenas para inspeção/teste. */
    public long tokensDisponiveis() {
        return tokensDisponiveis;
    }
}
