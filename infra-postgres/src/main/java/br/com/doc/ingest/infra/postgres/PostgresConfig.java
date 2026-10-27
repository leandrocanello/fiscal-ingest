package br.com.doc.ingest.infra.postgres;

import java.util.Objects;

/** Parâmetros de conexão. A senha nunca aparece em {@code toString} (invariante 13). */
public record PostgresConfig(String url, String usuario, char[] senha) {

    public PostgresConfig {
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(usuario, "usuario");
        Objects.requireNonNull(senha, "senha");
    }

    @Override
    public String toString() {
        return "PostgresConfig[url=" + url + ", usuario=" + usuario + ", senha=****]";
    }
}
