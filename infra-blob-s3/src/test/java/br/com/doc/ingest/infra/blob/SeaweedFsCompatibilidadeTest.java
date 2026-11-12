package br.com.doc.ingest.infra.blob;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListBucketsRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Pré-condição da invariante 7: o servidor precisa honrar a escrita condicional {@code If-None-Match: *}. */
class SeaweedFsCompatibilidadeTest extends SeaweedFsTestBase {

    private final S3Client s3 = novoCliente();

    private void put(String bucket, String chave, String conteudo) {
        s3.putObject(PutObjectRequest.builder().bucket(bucket).key(chave).ifNoneMatch("*").build(),
                RequestBody.fromBytes(conteudo.getBytes(StandardCharsets.UTF_8)));
    }

    private String ler(String bucket, String chave) {
        return s3.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(chave).build())
                .asString(StandardCharsets.UTF_8);
    }

    @Test
    void sobeContainerECriaBucket() {
        String bucket = novoBucket(s3);
        assertThat(s3.listBuckets(ListBucketsRequest.builder().build()).buckets())
                .extracting(b -> b.name()).contains(bucket);
    }

    @Test
    void putComIfNoneMatchEmChaveNovaGrava() {
        String bucket = novoBucket(s3);
        put(bucket, "raw/a.xml", "um");
        assertThat(ler(bucket, "raw/a.xml")).isEqualTo("um");
    }

    @Test
    void putComIfNoneMatchEmChaveExistenteDevolve412() {
        String bucket = novoBucket(s3);
        put(bucket, "raw/a.xml", "um");
        assertThatThrownBy(() -> put(bucket, "raw/a.xml", "dois"))
                .isInstanceOfSatisfying(S3Exception.class, e -> assertThat(e.statusCode()).isEqualTo(412));
        assertThat(ler(bucket, "raw/a.xml")).isEqualTo("um");
    }
}
