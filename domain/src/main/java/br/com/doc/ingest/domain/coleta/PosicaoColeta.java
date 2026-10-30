package br.com.doc.ingest.domain.coleta;

import br.com.doc.ingest.domain.adapter.Cursor;
import br.com.doc.ingest.domain.model.Nsu;

import java.util.Objects;

/**
 * Fotografia do estado de coleta lida pelo fetcher. A {@code versao} é a base do CAS:
 * a confirmação só se aplica se a versão no banco ainda for esta (CLAUDE.md §6.5).
 */
public record PosicaoColeta(ChaveColeta chave, Cursor cursor, Nsu ultNsu, long versao) {

    public PosicaoColeta {
        Objects.requireNonNull(chave, "chave");
        Objects.requireNonNull(cursor, "cursor");
        Objects.requireNonNull(ultNsu, "ultNsu");
        if (versao < 0) {
            throw new IllegalArgumentException("versão não pode ser negativa: " + versao);
        }
    }
}
