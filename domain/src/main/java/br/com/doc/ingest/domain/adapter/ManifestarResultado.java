package br.com.doc.ingest.domain.adapter;

import br.com.doc.ingest.domain.blob.BlobRef;
import java.util.Objects;
import java.util.Optional;

/**
 * Resultado de manifestar: SOMENTE o desfecho normalizado + BlobRef; NUNCA bytes/XML (inv. 3).
 *
 * <p>{@code envelope} referencia a resposta bruta do webservice ja gravada no blob store
 * (grave primeiro, inv. 5); vazio quando nao houve resposta a gravar (ex.: pre-condicao recusada
 * antes do envio).
 */
public record ManifestarResultado(Desfecho desfecho, Optional<BlobRef> envelope) {
    public ManifestarResultado {
        Objects.requireNonNull(desfecho, "desfecho");
        Objects.requireNonNull(envelope, "envelope");
    }
}
