package br.com.doc.ingest.infra.blob;

import br.com.doc.ingest.domain.blob.BlobRef;
import br.com.doc.ingest.domain.blob.Sha256;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class S3BlobStoreTest extends SeaweedFsTestBase {

    private static final String CHAVE = "raw/pull/nfe-nacional/producao/12345678000195/2026/11/12/";

    private S3Client s3;
    private String bucket;
    private S3BlobStore store;

    @BeforeEach
    void preparar() {
        s3 = novoCliente();
        bucket = novoBucket(s3);
        store = new S3BlobStore(s3, bucket);
    }

    @AfterEach
    void fechar() {
        s3.close();
    }

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void gravarDepoisLerDevolveMesmosBytes() {
        byte[] conteudo = bytes("<envelope/>");
        BlobRef ref = store.gravar(CHAVE + "a.xml", conteudo, Sha256.hex(conteudo));

        assertThat(ref).isEqualTo(new BlobRef(CHAVE + "a.xml", Sha256.hex(conteudo)));
        assertThat(store.ler(ref)).hasValue(conteudo);
    }

    @Test
    void objetoGuardaSha256NoMetadado() {
        byte[] conteudo = bytes("meta");
        store.gravar("k1", conteudo, Sha256.hex(conteudo));

        Map<String, String> metadata = s3.headObject(HeadObjectRequest.builder().bucket(bucket).key("k1").build())
                .metadata();
        assertThat(metadata).containsEntry("sha256", Sha256.hex(conteudo));
    }

    @Test
    void shaQueNaoBateNaoFazRequisicao() {
        S3Client quebrado = S3Client.builder()
                .endpointOverride(java.net.URI.create("http://127.0.0.1:1"))
                .region(software.amazon.awssdk.regions.Region.US_EAST_1)
                .credentialsProvider(software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider.create())
                .httpClientBuilder(software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient.builder())
                .build();
        try (quebrado) {
            S3BlobStore s = new S3BlobStore(quebrado, bucket);
            assertThatThrownBy(() -> s.gravar("k2", bytes("x"), Sha256.hex(bytes("y"))))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void lerInexistenteDevolveVazio() {
        assertThat(store.ler(new BlobRef("nao/existe", Sha256.hex(bytes("x"))))).isEmpty();
    }

    @Test
    void lerComShaDiferenteDevolveVazio() {
        byte[] conteudo = bytes("a");
        store.gravar("k3", conteudo, Sha256.hex(conteudo));

        assertThat(store.ler(new BlobRef("k3", Sha256.hex(bytes("outro"))))).isEmpty();
    }

    @Test
    void conteudoAlteradoForaDoSistemaDevolveVazioNaLeitura() {
        byte[] original = bytes("original");
        byte[] adulterado = bytes("adulterado");
        // grava direto, com metadado de outro hash
        s3.putObject(PutObjectRequest.builder().bucket(bucket).key("k4")
                .metadata(Map.of("sha256", Sha256.hex(original))).build(), RequestBody.fromBytes(adulterado));

        assertThat(store.ler(new BlobRef("k4", Sha256.hex(original)))).isEmpty();
    }

    @Test
    void regravarMesmoConteudoEhNoOp() {
        byte[] conteudo = bytes("igual");
        BlobRef primeira = store.gravar("k5", conteudo, Sha256.hex(conteudo));
        BlobRef segunda = store.gravar("k5", conteudo, Sha256.hex(conteudo));

        assertThat(segunda).isEqualTo(primeira);
        assertThat(store.ler(primeira)).hasValue(conteudo);
    }

    @Test
    void regravarConteudoDiferenteLancaConflito() {
        byte[] a = bytes("a");
        byte[] b = bytes("b");
        store.gravar("k6", a, Sha256.hex(a));

        assertThatThrownBy(() -> store.gravar("k6", b, Sha256.hex(b)))
                .isInstanceOf(br.com.doc.ingest.domain.blob.BlobConflitanteException.class)
                .hasMessageContaining("k6")
                .hasMessageNotContaining("b\"");
        assertThat(store.ler(new BlobRef("k6", Sha256.hex(a)))).hasValue(a);
    }

    private S3Client clientePortaFechada() {
        return S3Client.builder()
                .endpointOverride(java.net.URI.create("http://127.0.0.1:1"))
                .region(software.amazon.awssdk.regions.Region.US_EAST_1)
                .credentialsProvider(software.amazon.awssdk.auth.credentials.StaticCredentialsProvider.create(
                        software.amazon.awssdk.auth.credentials.AwsBasicCredentials.create(ACCESS_KEY, SECRET_KEY)))
                .httpClientBuilder(software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient.builder())
                .overrideConfiguration(c -> c.retryStrategy(
                        software.amazon.awssdk.core.retry.RetryMode.STANDARD).apiCallTimeout(
                        java.time.Duration.ofSeconds(5)))
                .build();
    }

    @Test
    void falhaDoServidorLancaENaoDevolveRef() {
        try (S3Client quebrado = clientePortaFechada()) {
            S3BlobStore s = new S3BlobStore(quebrado, bucket);
            byte[] c = bytes("conteudo");
            assertThatThrownBy(() -> s.gravar("k7", c, Sha256.hex(c)))
                    .isInstanceOf(BlobStoreIndisponivelException.class)
                    .hasMessageContaining("k7")
                    .hasCauseInstanceOf(software.amazon.awssdk.core.exception.SdkClientException.class);
        }
    }

    @Test
    void bucketInexistenteLanca() {
        S3BlobStore s = new S3BlobStore(s3, "bucket-que-nao-existe-" + java.util.UUID.randomUUID());
        byte[] c = bytes("conteudo");
        // o SeaweedFS cria o bucket implicitamente no PUT; só a leitura prova a falha de bucket ausente
        assertThatThrownBy(() -> s.ler(new BlobRef("k8", Sha256.hex(c))))
                .isInstanceOf(BlobStoreIndisponivelException.class);
    }

    @Test
    void excecaoNaoTrazSecretNemBytes() {
        try (S3Client quebrado = clientePortaFechada()) {
            S3BlobStore s = new S3BlobStore(quebrado, bucket);
            byte[] c = bytes("CONTEUDO-SENSIVEL");
            Throwable t = org.assertj.core.api.Assertions.catchThrowable(() -> s.gravar("k9", c, Sha256.hex(c)));
            assertThat(t).isInstanceOf(BlobStoreIndisponivelException.class);
            assertThat(t.getMessage()).doesNotContain(SECRET_KEY).doesNotContain("CONTEUDO-SENSIVEL");
            assertThat(t.toString()).doesNotContain(SECRET_KEY).doesNotContain("CONTEUDO-SENSIVEL");
            for (Throwable c2 = t.getCause(); c2 != null; c2 = c2.getCause()) {
                assertThat(c2.toString()).doesNotContain(SECRET_KEY).doesNotContain("CONTEUDO-SENSIVEL");
            }
        }
    }
}
