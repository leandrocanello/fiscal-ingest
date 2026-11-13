package br.com.doc.ingest.adapter.nfe.smoke;

import br.com.doc.ingest.domain.model.Ambiente;
import br.com.doc.ingest.domain.model.Cnpj;
import java.nio.file.Path;
import java.util.Map;

/**
 * Configuração do smoke manual, lida só de variáveis de ambiente (E2-S17). O ambiente é fixo em
 * homologação: não existe variável para produção (inv. 14). Erros nomeiam a variável e nunca o
 * valor; senha e CNPJ saem mascarados no texto (inv. 13).
 */
public final class ConfiguracaoSmoke {

    private final Path pfx;
    private final String senha;
    private final Cnpj cnpj;
    private final String urlDist;
    private final String urlEvento;
    private final String chave;
    private final boolean manifestar;
    private final Path dir;
    private final Path truststore;

    private ConfiguracaoSmoke(
            Path pfx,
            String senha,
            Cnpj cnpj,
            String urlDist,
            String urlEvento,
            String chave,
            boolean manifestar,
            Path dir,
            Path truststore) {
        this.pfx = pfx;
        this.senha = senha;
        this.cnpj = cnpj;
        this.urlDist = urlDist;
        this.urlEvento = urlEvento;
        this.chave = chave;
        this.manifestar = manifestar;
        this.dir = dir;
        this.truststore = truststore;
    }

    public static ConfiguracaoSmoke de(Map<String, String> env, Path home) {
        String cnpjTexto = obrigatoria(env, "FISCAL_SMOKE_CNPJ");
        if (!Cnpj.valido(cnpjTexto)) {
            throw new IllegalStateException("Variável FISCAL_SMOKE_CNPJ inválida");
        }
        String dir = opcional(env, "FISCAL_SMOKE_DIR");
        String truststore = opcional(env, "FISCAL_TRUSTSTORE");
        return new ConfiguracaoSmoke(
                Path.of(obrigatoria(env, "FISCAL_SMOKE_PFX")),
                obrigatoria(env, "FISCAL_SMOKE_SENHA"),
                new Cnpj(cnpjTexto),
                https(obrigatoria(env, "FISCAL_SMOKE_URL_DIST"), "FISCAL_SMOKE_URL_DIST"),
                urlOpcional(env, "FISCAL_SMOKE_URL_EVENTO"),
                opcional(env, "FISCAL_SMOKE_CHAVE"),
                "true".equalsIgnoreCase(opcional(env, "FISCAL_SMOKE_MANIFESTAR")),
                dir != null ? Path.of(dir) : home.resolve("fiscal-smoke"),
                truststore != null ? Path.of(truststore) : null);
    }

    private static String obrigatoria(Map<String, String> env, String nome) {
        String valor = opcional(env, nome);
        if (valor == null) {
            throw new IllegalStateException("Variável " + nome + " ausente");
        }
        return valor;
    }

    private static String opcional(Map<String, String> env, String nome) {
        String valor = env.get(nome);
        return valor == null || valor.isBlank() ? null : valor;
    }

    private static String urlOpcional(Map<String, String> env, String nome) {
        String valor = opcional(env, nome);
        return valor == null ? null : https(valor, nome);
    }

    private static String https(String valor, String nome) {
        if (!valor.startsWith("https://")) {
            throw new IllegalStateException("Variável " + nome + " deve ser uma URL https");
        }
        return valor;
    }

    public Path pfx() {
        return pfx;
    }

    /** Um {@code char[]} novo a cada chamada; quem recebe zera depois do uso. */
    public char[] senha() {
        return senha.toCharArray();
    }

    public Cnpj cnpj() {
        return cnpj;
    }

    public Ambiente ambiente() {
        return Ambiente.HOMOLOGACAO;
    }

    public String urlDist() {
        return urlDist;
    }

    public String urlEvento() {
        return urlEvento;
    }

    public String chave() {
        return chave;
    }

    public boolean manifestar() {
        return manifestar;
    }

    public Path dir() {
        return dir;
    }

    public Path truststore() {
        return truststore;
    }

    @Override
    public String toString() {
        return "ConfiguracaoSmoke[ambiente=HOMOLOGACAO, cnpj=" + cnpj.mascarado()
                + ", senha=***, pfx=" + pfx.getFileName() + ", manifestar=" + manifestar + "]";
    }
}
