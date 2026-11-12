package br.com.doc.ingest.infra.blob;

/** O armazenamento de blobs falhou (rede, bucket, servidor): nada foi confirmado, o chamador deve retentar. */
public final class BlobStoreIndisponivelException extends RuntimeException {

    public BlobStoreIndisponivelException(String operacao, String chave, Throwable causa) {
        super("blob store indisponível ao " + operacao + " a chave " + chave, causa);
    }
}
