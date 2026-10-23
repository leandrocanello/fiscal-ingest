package br.com.doc.ingest.domain.blob;

/**
 * Referência lógica a um objeto no blob store: chave determinística + sha256. Nunca carrega bytes.
 *
 * @param chave  caminho do objeto, relativo (sem "..", sem "/" inicial)
 * @param sha256 hash do conteúdo, 64 hex minúsculos
 */
public record BlobRef(String chave, String sha256) {

    public BlobRef {
        if (chave == null || chave.isBlank()) {
            throw new IllegalArgumentException("Chave do blob não pode ser nula ou em branco");
        }
        if (chave.startsWith("/") || chave.contains("..")) {
            throw new IllegalArgumentException("Chave do blob inválida: " + chave);
        }
        if (sha256 == null || !sha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("sha256 deve ter 64 hex minúsculos: " + sha256);
        }
    }
}
