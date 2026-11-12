package br.com.doc.ingest.infra.blob;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/** Cria o {@link S3Client} sempre com endpoint explícito e path-style (servidores S3 compatíveis). */
public final class FabricaClienteS3 {

    private FabricaClienteS3() {
    }

    public static S3Client criar(S3Config config) {
        return S3Client.builder()
                .endpointOverride(config.endpoint())
                .region(Region.of(config.regiao()))
                .forcePathStyle(true)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(config.accessKey(), new String(config.secretKey()))))
                .httpClientBuilder(UrlConnectionHttpClient.builder().connectionTimeout(config.timeoutConexao()))
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .apiCallTimeout(config.timeoutChamada()).build())
                .build();
    }
}
