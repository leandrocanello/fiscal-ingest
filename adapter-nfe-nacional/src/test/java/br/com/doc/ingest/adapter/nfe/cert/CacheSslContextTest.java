package br.com.doc.ingest.adapter.nfe.cert;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.doc.ingest.domain.adapter.Credencial;
import br.com.doc.ingest.domain.blob.Sha256;
import java.security.KeyStore;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.SSLContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Cache de {@link SSLContext} por fingerprint do .pfx, com LRU e TTL sob relógio manual. */
class CacheSslContextTest {

    private static final char[] SENHA = "senha-sintetica".toCharArray();
    private static final Instant DE = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant ATE = Instant.parse("2027-01-01T00:00:00Z");
    private static final Duration TTL = Duration.ofMinutes(30);

    private static byte[] pfxA;
    private static byte[] pfxB;
    private static byte[] pfxC;
    private static KeyStore confianca;

    private final RelogioManual relogio = new RelogioManual(Instant.parse("2026-10-27T08:00:00Z"));
    private final CarregadorContador carregador = new CarregadorContador();

    @BeforeAll
    static void gerar() throws Exception {
        pfxA = GeradorPfxDeTeste.moderno(SENHA, DE, ATE);
        pfxB = GeradorPfxDeTeste.moderno(SENHA, DE, ATE);
        pfxC = GeradorPfxDeTeste.moderno(SENHA, DE, ATE);
        confianca = KeyStore.getInstance("PKCS12");
        confianca.load(null, null);
        confianca.setCertificateEntry("ac", new CarregadorPfx().carregar(credencial(pfxA)).cadeia()[0]);
    }

    @Test
    void mesmaCredencialDevolveMesmaInstancia() {
        CacheSslContext cache = cache(4);

        SSLContext primeiro = cache.obter(credencial(pfxA));
        SSLContext segundo = cache.obter(credencial(pfxA));

        assertThat(segundo).isSameAs(primeiro);
        assertThat(carregador.chamadas.get()).isEqualTo(1);
    }

    @Test
    void expiraAposTtl() {
        CacheSslContext cache = cache(4);
        SSLContext primeiro = cache.obter(credencial(pfxA));

        relogio.avancar(TTL.minusSeconds(1));
        assertThat(cache.obter(credencial(pfxA))).isSameAs(primeiro);

        relogio.avancar(Duration.ofSeconds(1));
        assertThat(cache.obter(credencial(pfxA))).isNotSameAs(primeiro);
        assertThat(carregador.chamadas.get()).isEqualTo(2);
    }

    @Test
    void lruRemoveOMenosUsadoNaCapacidade() {
        CacheSslContext cache = cache(2);
        SSLContext a = cache.obter(credencial(pfxA));
        SSLContext b = cache.obter(credencial(pfxB));
        cache.obter(credencial(pfxA)); // A passa a ser o mais recente; B é o menos usado
        cache.obter(credencial(pfxC)); // estoura a capacidade e remove B

        assertThat(cache.obter(credencial(pfxA))).isSameAs(a);
        assertThat(cache.obter(credencial(pfxB))).isNotSameAs(b);
        assertThat(cache.tamanho()).isEqualTo(2);
    }

    @Test
    void invalidarPorFingerprintForcaRecarga() {
        CacheSslContext cache = cache(4);
        SSLContext primeiro = cache.obter(credencial(pfxA));

        cache.invalidar(Sha256.hex(pfxA));

        assertThat(cache.obter(credencial(pfxA))).isNotSameAs(primeiro);
        assertThat(carregador.chamadas.get()).isEqualTo(2);
    }

    @Test
    void pfxDiferenteGeraContextoDiferente() {
        CacheSslContext cache = cache(4);

        SSLContext antigo = cache.obter(credencial(pfxA));
        SSLContext renovado = cache.obter(credencial(pfxB));

        assertThat(renovado).isNotSameAs(antigo);
        assertThat(carregador.chamadas.get()).isEqualTo(2);
    }

    @Test
    void naoAlteraACredencialRecebida() {
        CacheSslContext cache = cache(4);
        Credencial credencial = credencial(pfxA);

        cache.obter(credencial);
        cache.obter(credencial);

        assertThat(credencial.pfx()).isEqualTo(pfxA);
        assertThat(credencial.senha()).isEqualTo(SENHA);
    }

    private CacheSslContext cache(int capacidade) {
        return new CacheSslContext(carregador, new FabricaSslContext(), confianca, relogio, capacidade, TTL);
    }

    private static Credencial credencial(byte[] pfx) {
        return new Credencial(pfx, SENHA);
    }

    /** Conta quantas vezes o .pfx foi de fato aberto. */
    private static final class CarregadorContador extends CarregadorPfx {
        final AtomicInteger chamadas = new AtomicInteger();

        @Override
        public CertificadoCarregado carregar(Credencial credencial) {
            chamadas.incrementAndGet();
            return super.carregar(credencial);
        }
    }
}
