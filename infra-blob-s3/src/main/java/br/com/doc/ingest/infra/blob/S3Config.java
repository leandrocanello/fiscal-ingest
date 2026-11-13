package br.com.doc.ingest.infra.blob;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;

/**
 * Configuração do cliente S3 compatível. O endpoint é obrigatório (nunca há endpoint AWS padrão: inv. 14)
 * e a secret key nunca aparece em {@code toString}.
 */
public record S3Config(URI endpoint, String regiao, String bucket, String accessKey, char[] secretKey,
                       Duration timeoutConexao, Duration timeoutChamada) {

    static final Duration TIMEOUT_CONEXAO_PADRAO = Duration.ofSeconds(5);
    static final Duration TIMEOUT_CHAMADA_PADRAO = Duration.ofSeconds(30);

    public S3Config {
        Objects.requireNonNull(endpoint, "endpoint");
        Objects.requireNonNull(secretKey, "secretKey");
        if (endpoint.getUserInfo() != null) {
            throw new IllegalArgumentException("endpoint não pode conter credenciais na URL");
        }
        exigirTexto(regiao, "regiao");
        exigirTexto(bucket, "bucket");
        exigirTexto(accessKey, "accessKey");
        if (secretKey.length == 0) {
            throw new IllegalArgumentException("secretKey vazia");
        }
        secretKey = secretKey.clone();
        timeoutConexao = timeoutConexao == null ? TIMEOUT_CONEXAO_PADRAO : timeoutConexao;
        timeoutChamada = timeoutChamada == null ? TIMEOUT_CHAMADA_PADRAO : timeoutChamada;
    }

    private static void exigirTexto(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(campo + " obrigatório");
        }
    }

    @Override
    public char[] secretKey() {
        return secretKey.clone();
    }

    @Override
    public String toString() {
        return "S3Config[endpoint=" + endpoint.getScheme() + "://" + endpoint.getHost()
                + (endpoint.getPort() >= 0 ? ":" + endpoint.getPort() : "")
                + ", regiao=" + regiao + ", bucket=" + bucket + ", accessKey=" + accessKey
                + ", secretKey=***]";
    }
}
