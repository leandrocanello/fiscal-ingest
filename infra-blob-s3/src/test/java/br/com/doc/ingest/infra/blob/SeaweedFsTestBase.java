package br.com.doc.ingest.infra.blob;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

import java.net.URI;
import java.util.UUID;

/** Base dos testes de integração: um único SeaweedFS (S3, com autenticação) compartilhado pela JVM de testes. */
abstract class SeaweedFsTestBase {

    // Imagem fixada num único lugar (DA-11).
    private static final String IMAGEM = "chrislusf/seaweedfs:4.53";

    // Identidade sintética, só existe dentro do container (src/test/resources/s3.json).
    static final String ACCESS_KEY = "chave-so-de-teste";
    static final String SECRET_KEY = "segredo-so-de-teste";

    private static final GenericContainer<?> SEAWEEDFS = new GenericContainer<>(IMAGEM)
            .withCommand("server", "-s3", "-s3.config=/etc/seaweedfs/s3.json", "-dir=/data")
            .withCopyFileToContainer(MountableFile.forClasspathResource("s3.json"), "/etc/seaweedfs/s3.json")
            .withExposedPorts(8333)
            .waitingFor(Wait.forListeningPorts(8333));

    static {
        SEAWEEDFS.start();
    }

    static URI endpoint() {
        return URI.create("http://" + SEAWEEDFS.getHost() + ":" + SEAWEEDFS.getMappedPort(8333));
    }

    static S3Client novoCliente() {
        return S3Client.builder()
                .endpointOverride(endpoint())
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(ACCESS_KEY, SECRET_KEY)))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .build();
    }

    static String novoBucket(S3Client s3) {
        String bucket = "teste-" + UUID.randomUUID();
        s3.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
        return bucket;
    }
}
