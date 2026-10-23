package br.com.doc.ingest.domain.blob;

/**
 * Tentativa de regravar uma chave já existente com conteúdo diferente (invariante 7: blob imutável).
 * A mensagem traz apenas a chave e os hashes, nunca os bytes.
 */
public class BlobConflitanteException extends RuntimeException {

    public BlobConflitanteException(String chave, String shaExistente, String shaNovo) {
        super("Blob já existe com conteúdo diferente: chave=" + chave
                + ", sha256 existente=" + shaExistente + ", sha256 novo=" + shaNovo);
    }
}
