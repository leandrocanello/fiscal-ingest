package br.com.doc.ingest.domain.adapter;

import br.com.doc.ingest.domain.model.Documento;
import java.util.List;

/**
 * Resultado de buscarLote: SOMENTE metadado + Nsu + BlobRef; NUNCA bytes/XML.
 */
public record BuscarLoteResultado(
    List<Documento> documentos,
    Cursor proximoCursor,
    boolean temMais,
    Desfecho desfecho
) {}
