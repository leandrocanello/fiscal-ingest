package br.com.doc.ingest.domain.model;

import br.com.doc.ingest.domain.blob.BlobRef;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Documento canonico: SOMENTE metadado + BlobRef; NUNCA bytes/XML.
 * Materializa a regra "XML nunca passa pelo core".
 *
 * @param blobRef referência lógica (chave + sha256) ao bruto no blob store, exatamente como
 *                {@code BlobStore.gravar} a devolveu; nunca bytes
 */
public record Documento(
    UUID id,
    FonteId fonte,
    Cnpj cnpjDestinatario,
    String modelo,
    Nsu nsu,
    Instant recebidoEm,
    String tipoConteudo,
    Optional<String> chave,
    Optional<BlobRef> blobRef
) {}
