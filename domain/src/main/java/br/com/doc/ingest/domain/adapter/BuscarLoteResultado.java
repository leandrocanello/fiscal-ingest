package br.com.doc.ingest.domain.adapter;

import br.com.doc.ingest.domain.model.Documento;
import br.com.doc.ingest.domain.model.Nsu;
import java.util.List;
import java.util.Objects;

/**
 * Resultado de buscarLote: SOMENTE metadado + Nsu + BlobRef; NUNCA bytes/XML.
 *
 * <p>{@code ultNsu} e a posicao explicita (nao opaca) depois do lote, usada pelo core para
 * comparar posicoes sem interpretar o token do {@link Cursor}. O core so avanca se novo &ge; atual;
 * um {@code ultNsu} menor que a posicao ja consumida e recusado como RETROCESSO_REJEITADO
 * (erro de consistencia, nunca retentar).
 */
public record BuscarLoteResultado(
    List<Documento> documentos,
    Cursor proximoCursor,
    Nsu ultNsu,
    boolean temMais,
    Desfecho desfecho
) {
    public BuscarLoteResultado {
        Objects.requireNonNull(ultNsu, "ultNsu");
    }
}
