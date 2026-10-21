package br.com.doc.ingest.domain.ratelimit;

import java.time.Duration;

/**
 * Resultado imutável de uma tentativa de consumo do {@link TokenBucket}.
 *
 * @param permitido  true se o token foi consumido e a operação pode prosseguir
 * @param retryApos  tempo até o próximo token estar disponível (null se permitido)
 */
public record ResultadoConsumo(boolean permitido, Duration retryApos) {

    /** Factory para quando o consumo é permitido. */
    public static ResultadoConsumo ok() {
        return new ResultadoConsumo(true, null);
    }

    /** Factory para quando o consumo é negado, com tempo de retry. */
    public static ResultadoConsumo limite(Duration retryApos) {
        return new ResultadoConsumo(false, retryApos);
    }
}
