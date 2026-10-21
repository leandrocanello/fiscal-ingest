package br.com.doc.ingest.domain.model;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Documento canonico: SOMENTE metadado + BlobRef; NUNCA bytes/XML.
 * Materializa a regra "XML nunca passa pelo core".
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
    Optional<String> blobRef
) {}
