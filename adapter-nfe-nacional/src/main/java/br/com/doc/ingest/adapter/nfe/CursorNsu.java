package br.com.doc.ingest.adapter.nfe;

import br.com.doc.ingest.domain.adapter.Cursor;
import br.com.doc.ingest.domain.model.Nsu;

/**
 * Conversão entre o {@link Cursor} opaco do core e o {@code ultNSU} da distribuição (DA-14).
 * O token é o NSU em decimal, sem zeros à esquerda; o token vazio ({@link Cursor#inicial()}) é o NSU 0.
 * Só o adapter interpreta o token; o core continua tratando-o como opaco.
 */
public final class CursorNsu {

    private static final int MAX_DIGITOS = 18;

    private CursorNsu() {
    }

    public static Nsu nsu(Cursor cursor) {
        var token = cursor.token();
        if (token.isEmpty()) {
            return new Nsu(0);
        }
        if (token.length() > MAX_DIGITOS || !token.chars().allMatch(c -> c >= '0' && c <= '9')) {
            // não ecoa o token: ele vem de estado persistido e pode estar corrompido com qualquer conteúdo
            throw new IllegalArgumentException(
                    "token de cursor inválido para NSU (tamanho " + token.length() + ")");
        }
        return new Nsu(Long.parseLong(token));
    }

    public static Cursor de(Nsu nsu) {
        return new Cursor(Long.toString(nsu.valor()));
    }
}
