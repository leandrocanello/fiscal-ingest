package br.com.doc.ingest.infra.blob;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class S3ConfigTest {

    private static final URI ENDPOINT = URI.create("http://localhost:8333");

    private static S3Config config(URI endpoint, String bucket, String accessKey, char[] secret) {
        return new S3Config(endpoint, "us-east-1", bucket, accessKey, secret, null, null);
    }

    @Test
    void exigeEndpointBucketECredenciais() {
        assertThatThrownBy(() -> config(null, "b", "ak", "sk".toCharArray())).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> config(ENDPOINT, " ", "ak", "sk".toCharArray())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> config(ENDPOINT, "b", "", "sk".toCharArray())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> config(ENDPOINT, "b", "ak", new char[0])).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> config(ENDPOINT, "b", "ak", null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void timeoutsTemPadraoQuandoNaoInformados() {
        S3Config c = config(ENDPOINT, "b", "ak", "sk".toCharArray());
        assertThat(c.timeoutConexao()).isEqualTo(Duration.ofSeconds(5));
        assertThat(c.timeoutChamada()).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void endpointComCredencialNaUrlEhRecusado() {
        URI comSenha = URI.create("http://usuario:senha-vazada@localhost:8333");
        assertThatThrownBy(() -> config(comSenha, "b", "ak", "sk".toCharArray()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining("senha-vazada")
                .hasMessageNotContaining("usuario");
    }

    @Test
    void toStringNaoExpoeSecretKey() {
        S3Config c = config(ENDPOINT, "b", "ak", "segredo-super".toCharArray());
        assertThat(c.toString()).doesNotContain("segredo-super").contains("b");
    }
}
