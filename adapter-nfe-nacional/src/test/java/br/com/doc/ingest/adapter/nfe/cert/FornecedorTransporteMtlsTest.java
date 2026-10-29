package br.com.doc.ingest.adapter.nfe.cert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.doc.ingest.adapter.nfe.transporte.TransporteSoap;
import br.com.doc.ingest.domain.adapter.Credencial;
import java.security.KeyStore;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import javax.net.ssl.SSLContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** O fornecedor de transporte mTLS usa o {@link CacheSslContext} e não vaza a senha. */
class FornecedorTransporteMtlsTest {

    private static final char[] SENHA = "SEGREDO-XYZ".toCharArray();
    private static final Instant DE = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant ATE = Instant.parse("2027-01-01T00:00:00Z");

    private static byte[] pfx;
    private static KeyStore confianca;

    private final CacheEspiao cache = new CacheEspiao();
    private final FornecedorTransporteMtls fornecedor =
            new FornecedorTransporteMtls(cache, Duration.ofSeconds(5), 1024 * 1024);

    @BeforeAll
    static void gerar() throws Exception {
        pfx = GeradorPfxDeTeste.moderno(SENHA, DE, ATE);
        confianca = KeyStore.getInstance("PKCS12");
        confianca.load(null, null);
        confianca.setCertificateEntry("ac", new CarregadorPfx().carregar(new Credencial(pfx, SENHA)).cadeia()[0]);
    }

    @Test
    void autenticarComPfxSinteticoUsaCacheSslContext() {
        TransporteSoap primeiro = fornecedor.para(new Credencial(pfx, SENHA));
        TransporteSoap segundo = fornecedor.para(new Credencial(pfx, SENHA));

        assertThat(primeiro).isNotNull();
        assertThat(segundo).isNotNull();
        assertThat(cache.devolvidos).hasSize(2);
        assertThat(cache.devolvidos.get(1)).isSameAs(cache.devolvidos.get(0));
        assertThat(cache.tamanho()).isEqualTo(1);
    }

    @Test
    void pfxInvalidoEhCertificadoInvalidoSemSenhaNaMensagem() {
        Credencial invalida = new Credencial(new byte[] {1, 2, 3}, SENHA);

        assertThatThrownBy(() -> fornecedor.para(invalida))
                .isInstanceOf(CertificadoInvalidoException.class)
                .satisfies(e -> assertThat(String.valueOf(e.getMessage())).doesNotContain("SEGREDO-XYZ"))
                .hasNoCause();
    }

    /** Cache real que registra cada contexto devolvido. */
    private static final class CacheEspiao extends CacheSslContext {
        final List<SSLContext> devolvidos = new ArrayList<>();

        CacheEspiao() {
            super(new CarregadorPfx(), new FabricaSslContext(), confianca,
                    new RelogioManual(Instant.parse("2026-10-29T08:00:00Z")), 4, Duration.ofMinutes(30));
        }

        @Override
        public SSLContext obter(Credencial credencial) {
            SSLContext contexto = super.obter(credencial);
            devolvidos.add(contexto);
            return contexto;
        }
    }
}
