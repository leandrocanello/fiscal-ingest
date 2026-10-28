package br.com.doc.ingest.domain.adapter;

import br.com.doc.ingest.domain.blob.BlobRef;
import br.com.doc.ingest.domain.model.Documento;
import br.com.doc.ingest.domain.model.Nsu;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Resultado de buscarLote: SOMENTE metadado + Nsu + BlobRef; NUNCA bytes/XML.
 *
 * <p>{@code ultNsu} e a posicao explicita (nao opaca) depois do lote, usada pelo core para
 * comparar posicoes sem interpretar o token do {@link Cursor}. O core so avanca se novo &ge; atual;
 * um {@code ultNsu} menor que a posicao ja consumida e recusado como RETROCESSO_REJEITADO
 * (erro de consistencia, nunca retentar).
 *
 * <p>{@code envelope} referencia a resposta bruta do webservice ja gravada no blob store
 * (grave primeiro, inv. 5); vazio quando a fonte nao grava envelope (ex.: fake).
 */
public record BuscarLoteResultado(
    List<Documento> documentos,
    Cursor proximoCursor,
    Nsu ultNsu,
    boolean temMais,
    Desfecho desfecho,
    Optional<BlobRef> envelope
) {
    public BuscarLoteResultado {
        Objects.requireNonNull(ultNsu, "ultNsu");
        Objects.requireNonNull(envelope, "envelope");
    }

    public BuscarLoteResultado(
            List<Documento> documentos, Cursor proximoCursor, Nsu ultNsu, boolean temMais, Desfecho desfecho) {
        this(documentos, proximoCursor, ultNsu, temMais, desfecho, Optional.empty());
    }
}
