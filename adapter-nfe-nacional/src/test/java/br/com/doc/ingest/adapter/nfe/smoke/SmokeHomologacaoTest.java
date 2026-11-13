package br.com.doc.ingest.adapter.nfe.smoke;

import br.com.doc.ingest.adapter.nfe.NfeNacionalAdapter;
import br.com.doc.ingest.adapter.nfe.cert.CacheSslContext;
import br.com.doc.ingest.adapter.nfe.cert.CarregadorPfx;
import br.com.doc.ingest.adapter.nfe.cert.FabricaSslContext;
import br.com.doc.ingest.adapter.nfe.cert.FornecedorTransporteMtls;
import br.com.doc.ingest.adapter.nfe.cert.TruststoreIcpBrasil;
import br.com.doc.ingest.adapter.nfe.soap.TipoAmbiente;
import br.com.doc.ingest.domain.adapter.ContextoConsulta;
import br.com.doc.ingest.domain.adapter.Credencial;
import br.com.doc.ingest.domain.model.FonteId;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Smoke manual em homologação (E2-S12). Fora da task {@code test}; nunca roda no CI (inv. 14).
 * Tudo vem de variáveis de ambiente; ver {@code docs/SMOKE-HOMOLOGACAO.md}.
 */
@Tag("homologacao")
@EnabledIfSystemProperty(named = "smoke.homologacao", matches = "true")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SmokeHomologacaoTest {

    private static ConfiguracaoSmoke config;
    private static RoteiroSmoke roteiro;

    @BeforeAll
    static void montar() throws IOException {
        Map<String, String> env = System.getenv();
        config = ConfiguracaoSmoke.de(env, Path.of(System.getProperty("user.home")));
        FonteId fonte = new FonteId("NFE_AN");
        Clock relogio = Clock.systemDefaultZone();

        byte[] pfx = Files.readAllBytes(config.pfx());
        char[] senha = config.senha();
        Credencial credencial = new Credencial(pfx, senha);
        Arrays.fill(pfx, (byte) 0);
        Arrays.fill(senha, '\0');

        CacheSslContext cache = new CacheSslContext(new CarregadorPfx(), new FabricaSslContext(),
                TruststoreIcpBrasil.carregar(env), relogio, 4, Duration.ofHours(1));
        FornecedorTransporteMtls fornecedor =
                new FornecedorTransporteMtls(cache, Duration.ofSeconds(60), 10L * 1024 * 1024);
        URI urlEvento = config.urlEvento() == null ? null : URI.create(config.urlEvento());
        NfeNacionalAdapter adapter = new NfeNacionalAdapter(fonte, TipoAmbiente.HOMOLOGACAO,
                URI.create(config.urlDist()), new BlobStoreEmDiscoSmoke(config.dir(), relogio), relogio,
                fornecedor, urlEvento);
        adapter.autenticar(credencial);

        try {
            roteiro = new RoteiroSmoke(adapter, new CursorLocal(config.dir(), config.cnpj()),
                    new ContextoConsulta(fonte, config.cnpj()), System.out, 20);
        } catch (RuntimeException e) {
            throw new UncheckedIOException(new IOException("falha ao montar o roteiro do smoke", e));
        }
    }

    @Test
    @Order(1)
    void distNsu() {
        roteiro.distNsu();
    }

    @Test
    @Order(2)
    void consChNFe() {
        org.junit.jupiter.api.Assumptions.assumeTrue(config.chave() != null, "sem FISCAL_SMOKE_CHAVE");
        roteiro.consultarChave(config.chave());
    }

    @Test
    @Order(3)
    void ciencia() {
        org.junit.jupiter.api.Assumptions.assumeTrue(config.chave() != null, "sem FISCAL_SMOKE_CHAVE");
        roteiro.manifestarCiencia(config.chave(), config.manifestar());
    }

    @Test
    @Order(4)
    void distNsuDepoisDaCiencia() {
        org.junit.jupiter.api.Assumptions.assumeTrue(config.manifestar(), "sem FISCAL_SMOKE_MANIFESTAR");
        roteiro.distNsu();
    }
}
