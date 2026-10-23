package br.com.doc.ingest.domain.blob;

import java.util.Optional;

/**
 * Porta de armazenamento de objetos imutáveis (invariantes 5 e 7).
 *
 * <p>Regras:
 * <ul>
 *   <li>{@code gravar} só devolve o {@link BlobRef} depois de o objeto estar guardado;</li>
 *   <li>mesma chave com o mesmo hash é no-op idempotente (devolve o mesmo {@code BlobRef});</li>
 *   <li>mesma chave com hash diferente lança {@link BlobConflitanteException};</li>
 *   <li>hash informado que não bate com os bytes lança {@link IllegalArgumentException} e nada é gravado;</li>
 *   <li>os bytes são copiados na entrada e na saída.</li>
 * </ul>
 */
public interface BlobStore {

    BlobRef gravar(String chave, byte[] conteudo, String sha256);

    /** Devolve vazio se a chave não existe ou se o hash da referência difere do gravado. */
    Optional<byte[]> ler(BlobRef ref);
}
