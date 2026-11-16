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
 *
 * <p>{@code eventoAssinado} referencia o evento assinado ja gravado; vazio quando nada foi assinado.
 */
public record ManifestarResultado(
        Desfecho desfecho, Optional<BlobRef> envelope, Optional<BlobRef> eventoAssinado) {
    public ManifestarResultado {
        Objects.requireNonNull(desfecho, "desfecho");
        Objects.requireNonNull(envelope, "envelope");
        Objects.requireNonNull(eventoAssinado, "eventoAssinado");
    }

    public ManifestarResultado(Desfecho desfecho, Optional<BlobRef> envelope) {
        this(desfecho, envelope, Optional.empty());
    }
}
