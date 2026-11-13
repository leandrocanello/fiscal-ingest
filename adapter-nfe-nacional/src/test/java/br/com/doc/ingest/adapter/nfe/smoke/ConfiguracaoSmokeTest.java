package br.com.doc.ingest.adapter.nfe.smoke;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.doc.ingest.domain.model.Ambiente;
import br.com.doc.ingest.domain.model.Cnpj;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConfiguracaoSmokeTest {

    private static final String CNPJ = "11222333000181";
    private static final Path HOME = Path.of("/home/fulano");

    private static Map<String, String> envMinimo() {
        Map<String, String> env = new HashMap<>();
        env.put("FISCAL_SMOKE_PFX", "/segredo/cert.pfx");
        env.put("FISCAL_SMOKE_SENHA", "s3nh4-super-secreta");
        env.put("FISCAL_SMOKE_CNPJ", CNPJ);
        env.put("FISCAL_SMOKE_URL_DIST", "https://homolog.example.test/dist");
        return env;
    }

    @Test
    void leOsObrigatoriosEAplicaPadroesDosOpcionais() {
        ConfiguracaoSmoke c = ConfiguracaoSmoke.de(envMinimo(), HOME);

        assertThat(c.pfx()).isEqualTo(Path.of("/segredo/cert.pfx"));
        assertThat(c.cnpj()).isEqualTo(new Cnpj(CNPJ));
        assertThat(c.urlDist()).isEqualTo("https://homolog.example.test/dist");
        assertThat(c.urlEvento()).isNull();
        assertThat(c.chave()).isNull();
        assertThat(c.manifestar()).isFalse();
        assertThat(c.dir()).isEqualTo(HOME.resolve("fiscal-smoke"));
        assertThat(c.truststore()).isNull();
    }

    @Test
    void leOpcionais() {
        Map<String, String> env = envMinimo();
        env.put("FISCAL_SMOKE_URL_EVENTO", "https://homolog.example.test/evento");
        env.put("FISCAL_SMOKE_CHAVE", "123");
        env.put("FISCAL_SMOKE_MANIFESTAR", "true");
        env.put("FISCAL_SMOKE_DIR", "/tmp/saida");
        env.put("FISCAL_TRUSTSTORE", "/tmp/ts");

        ConfiguracaoSmoke c = ConfiguracaoSmoke.de(env, HOME);

        assertThat(c.urlEvento()).isEqualTo("https://homolog.example.test/evento");
        assertThat(c.chave()).isEqualTo("123");
        assertThat(c.manifestar()).isTrue();
        assertThat(c.dir()).isEqualTo(Path.of("/tmp/saida"));
        assertThat(c.truststore()).isEqualTo(Path.of("/tmp/ts"));
    }

    @Test
    void variavelAusenteNomeiaAVariavelNuncaOValor() {
        for (String nome : new String[] {
            "FISCAL_SMOKE_PFX", "FISCAL_SMOKE_SENHA", "FISCAL_SMOKE_CNPJ", "FISCAL_SMOKE_URL_DIST"
        }) {
            Map<String, String> env = envMinimo();
            env.remove(nome);
            assertThatThrownBy(() -> ConfiguracaoSmoke.de(env, HOME))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(nome)
                    .hasMessageNotContaining("s3nh4-super-secreta")
                    .hasMessageNotContaining(CNPJ);
        }
    }

    @Test
    void erroNomeiaAVariavelNuncaOValor() {
        Map<String, String> env = envMinimo();
        env.put("FISCAL_SMOKE_CNPJ", "99999999999999");
        assertThatThrownBy(() -> ConfiguracaoSmoke.de(env, HOME))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FISCAL_SMOKE_CNPJ")
                .hasMessageNotContaining("99999999999999");
    }

    @Test
    void recusaUrlQueNaoEHttps() {
        Map<String, String> env = envMinimo();
        env.put("FISCAL_SMOKE_URL_DIST", "http://homolog.example.test/dist");
        assertThatThrownBy(() -> ConfiguracaoSmoke.de(env, HOME))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FISCAL_SMOKE_URL_DIST");

        Map<String, String> env2 = envMinimo();
        env2.put("FISCAL_SMOKE_URL_EVENTO", "ftp://x");
        assertThatThrownBy(() -> ConfiguracaoSmoke.de(env2, HOME))
                .hasMessageContaining("FISCAL_SMOKE_URL_EVENTO");
    }

    @Test
    void recusaAmbienteDeProducao() {
        assertThat(ConfiguracaoSmoke.de(envMinimo(), HOME).ambiente()).isEqualTo(Ambiente.HOMOLOGACAO);
        Map<String, String> env = envMinimo();
        env.put("FISCAL_SMOKE_AMBIENTE", "PRODUCAO");
        // nao existe variavel de ambiente: ignorada, segue HOMOLOGACAO
        assertThat(ConfiguracaoSmoke.de(env, HOME).ambiente()).isEqualTo(Ambiente.HOMOLOGACAO);
    }

    @Test
    void toStringMascaraSenhaECnpj() {
        String texto = ConfiguracaoSmoke.de(envMinimo(), HOME).toString();
        assertThat(texto).doesNotContain("s3nh4-super-secreta").doesNotContain(CNPJ);
        assertThat(texto).contains("***");
    }

    @Test
    void senhaEUmCharArrayNovoACadaChamada() {
        ConfiguracaoSmoke c = ConfiguracaoSmoke.de(envMinimo(), HOME);
        char[] a = c.senha();
        char[] b = c.senha();
        assertThat(a).isEqualTo("s3nh4-super-secreta".toCharArray());
        assertThat(a).isNotSameAs(b);
        java.util.Arrays.fill(a, '\0');
        assertThat(c.senha()).isEqualTo("s3nh4-super-secreta".toCharArray());
    }
}
