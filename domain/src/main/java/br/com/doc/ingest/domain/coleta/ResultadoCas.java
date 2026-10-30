package br.com.doc.ingest.domain.coleta;

import br.com.doc.ingest.domain.model.Nsu;

/**
 * Desfecho de {@link EstadoColetaRepositorio#confirmarLote}. Fora de {@link Aplicado},
 * nada foi gravado: nem ledger, nem cursor.
 */
public sealed interface ResultadoCas {

    /** Ledger e cursor gravados; a versão subiu para {@code novaVersao}. */
    record Aplicado(long novaVersao) implements ResultadoCas { }

    /** Outro fetcher confirmou antes: a versão lida não é mais a atual. */
    record ConflitoDeVersao(long versaoAtual) implements ResultadoCas { }

    /** O novo ultNsu seria menor que o atual (invariante 1). */
    record RetrocessoRecusado(Nsu ultNsuAtual) implements ResultadoCas { }
}
