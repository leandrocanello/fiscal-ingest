package br.com.doc.ingest.domain.coleta;

import br.com.doc.ingest.domain.adapter.Cursor;
import br.com.doc.ingest.domain.ledger.EntradaLedger;
import br.com.doc.ingest.domain.model.Nsu;

import java.util.List;
import java.util.Objects;

/**
 * Pedido de confirmação de um lote: registrar {@code entradas} no ledger e avançar o cursor
 * para {@code novoCursor}/{@code novoUltNsu}, a partir da posição {@code lida} (CAS pela versão).
 * Lista vazia é válida (Vazio: o cursor avança sem entradas).
 */
public record ConfirmacaoLote(PosicaoColeta lida, Cursor novoCursor, Nsu novoUltNsu,
                              List<EntradaLedger> entradas) {

    public ConfirmacaoLote {
        Objects.requireNonNull(lida, "lida");
        Objects.requireNonNull(novoCursor, "novoCursor");
        Objects.requireNonNull(novoUltNsu, "novoUltNsu");
        entradas = List.copyOf(Objects.requireNonNull(entradas, "entradas"));
        for (EntradaLedger entrada : entradas) {
            if (entrada.nsu().ehMaiorQue(novoUltNsu)) {
                throw new IllegalArgumentException(
                        "entrada com NSU " + entrada.nsu().valor()
                                + " maior que o novo ultNsu " + novoUltNsu.valor());
            }
        }
    }
}
