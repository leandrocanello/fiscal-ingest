package br.com.doc.ingest.domain.adapter;

import java.time.Duration;

/**
 * Vocabulário único de desfechos de operações do adapter.
 *
 * <p>O adapter <b>traduz</b> o dialeto da origem (cStat 656/137/632, HTTP 429...)
 * para este vocabulário. O core <b>nunca</b> vê cStat/HTTP — só pattern matching
 * sobre estas variantes.
 *
 * <p><b>Regra crítica:</b> {@link LimiteAtingido} e {@link IndisponivelTemporario}
 * NÃO devem provocar avanço de cursor (prevenção cStat 656).
 */
public sealed interface Desfecho
        permits Desfecho.Ok, Desfecho.Vazio, Desfecho.LimiteAtingido,
                Desfecho.IndisponivelTemporario, Desfecho.IndisponivelDefinitivo,
                Desfecho.ErroAutenticacao, Desfecho.ServicoFora {

    /** Operação processada com conteúdo. */
    record Ok() implements Desfecho {}

    /** Nada novo a relatar (ex: cStat 137 — sem documentos). */
    record Vazio() implements Desfecho {}

    /** Limite de consumo atingido (ex: cStat 656). NÃO avançar o ponteiro. */
    record LimiteAtingido(Duration retryApos) implements Desfecho {}

    /** Indisponibilidade temporária (ex: timeout, 5xx, 429). Retry sem avançar. */
    record IndisponivelTemporario(Duration retryApos) implements Desfecho {}

    /** Indisponibilidade definitiva (ex: schema incompatível). */
    record IndisponivelDefinitivo(String motivo) implements Desfecho {}

    /** Falha de autenticação (ex: certificado inválido). */
    record ErroAutenticacao(String motivo) implements Desfecho {}

    /** Serviço paralisado (ex: cStat 109). */
    record ServicoFora(String motivo) implements Desfecho {}
}
