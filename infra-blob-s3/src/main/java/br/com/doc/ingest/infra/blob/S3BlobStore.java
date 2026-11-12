package br.com.doc.ingest.infra.blob;

import br.com.doc.ingest.domain.blob.BlobRef;
import br.com.doc.ingest.domain.blob.BlobStore;
import br.com.doc.ingest.domain.blob.Sha256;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** {@link BlobStore} sobre API S3 compatível. O sha256 é conferido na escrita e na leitura. */
public final class S3BlobStore implements BlobStore {

    static final String METADADO_SHA256 = "sha256";

    private final S3Client s3;
    private final String bucket;

    public S3BlobStore(S3Client s3, String bucket) {
        this.s3 = Objects.requireNonNull(s3, "s3");
        this.bucket = Objects.requireNonNull(bucket, "bucket");
    }

    @Override
    public BlobRef gravar(String chave, byte[] conteudo, String sha256) {
        BlobRef ref = new BlobRef(chave, sha256);
        if (!Sha256.hex(conteudo).equals(sha256)) {
            throw new IllegalArgumentException("sha256 informado não confere com o conteúdo para a chave " + chave);
        }
        byte[] copia = conteudo.clone();
        s3.putObject(PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(chave)
                        .metadata(Map.of(METADADO_SHA256, sha256))
                        .checksumSHA256(Base64.getEncoder().encodeToString(HexFormat.of().parseHex(sha256)))
                        .build(),
                RequestBody.fromBytes(copia));
        return ref;
    }

    @Override
    public Optional<byte[]> ler(BlobRef ref) {
        byte[] bytes;
        try {
            bytes = s3.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(ref.chave()).build())
                    .asByteArray();
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        }
        if (!Sha256.hex(bytes).equals(ref.sha256())) {
            return Optional.empty();
        }
        return Optional.of(bytes);
    }
}
