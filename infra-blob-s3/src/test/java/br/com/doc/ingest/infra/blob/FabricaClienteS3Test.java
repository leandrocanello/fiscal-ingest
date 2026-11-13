package br.com.doc.ingest.infra.blob;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListBucketsRequest;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class FabricaClienteS3Test extends SeaweedFsTestBase {

    @Test
    void criaClientePathStyleNoEndpointInformado() {
        S3Config config = new S3Config(endpoint(), "us-east-1", "qualquer", ACCESS_KEY, SECRET_KEY.toCharArray(),
                Duration.ofSeconds(5), Duration.ofSeconds(30));
        try (S3Client s3 = FabricaClienteS3.criar(config)) {
            String bucket = novoBucket(s3);
            assertThat(s3.listBuckets(ListBucketsRequest.builder().build()).buckets())
                    .extracting(b -> b.name()).contains(bucket);
        }
    }
}
