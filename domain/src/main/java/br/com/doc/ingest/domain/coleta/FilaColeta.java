package br.com.doc.ingest.domain.coleta;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Fila de estados de coleta vencidos. Primeira linha da invariante 2 (um CNPJ por vez); o CAS do
 * cursor ({@link EstadoColetaRepositorio#confirmarLote}) segue como proteção final. O claim não
 * muda cursor nem versão (DA-34).
 */
public interface FilaColeta {

    /** Reivindica estados ativos, vencidos, não bloqueados e sem lease vigente, gravando o lease. */
    List<ColetaReivindicada> reivindicar(PedidoReivindicacao pedido);

    /** Estende o lease do dono por {@code lease} a partir de agora; {@code false} se não for o dono. */
    boolean renovar(ChaveColeta chave, String dono, Duration lease);

    /** Solta o lease do dono e grava o próximo {@code due_at}; {@code false} se não for o dono. */
    boolean liberar(ChaveColeta chave, String dono, Instant proximoDue);

    /**
     * Grava o bloqueio informado pela fonte, sem conferir dono (DA-34). Nunca encurta um bloqueio
     * existente, e o {@code due_at} não fica antes do fim dele.
     */
    void registrarBloqueio(ChaveColeta chave, Instant bloqueadoAte);
}
