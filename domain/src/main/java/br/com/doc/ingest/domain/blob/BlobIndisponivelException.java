package br.com.doc.ingest.domain.blob;

import java.util.Objects;

/**
 * O objeto referenciado não pôde ser lido (ausente ou com hash diferente do da referência).
 *
 * <p>É <b>retentável</b>: o bruto é gravado antes de qualquer registro (inv. 5), então a ausência é
 * transitória ou falha de infraestrutura; quem chama reprocessa o item depois, sem descartá-lo.
 * A mensagem traz só o sha256: a chave contém o CNPJ consultado (§8.1) e fica fora (inv. 13).
 */
public class BlobIndisponivelException extends RuntimeException {

    public BlobIndisponivelException(BlobRef ref) {
        super("Blob indisponível: sha256=" + Objects.requireNonNull(ref, "ref").sha256());
    }

    /** Sempre {@code true}: reprocessar o item é seguro e é o tratamento esperado. */
    public boolean retentavel() {
        return true;
    }
}
