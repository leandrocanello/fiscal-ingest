package br.com.doc.ingest.adapter.nfe.cert;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.doc.ingest.domain.adapter.Credencial;
import br.com.doc.ingest.domain.blob.Sha256;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import javax.net.ssl.SSLContext;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Tuple;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.lifecycle.BeforeContainer;

/**
 * Propriedades do {@link CacheSslContext} sob sequências aleatórias de obter / avançar relógio / invalidar:
 * o tamanho nunca passa da capacidade e nunca se devolve contexto com idade >= TTL.
 */
class CacheSslContextPropriedadesTest {

    private static final Duration TTL = Duration.ofMinutes(30);
    private static final int QTD_PFX = 5;

    private static CertificadoCarregado certificado;
    private static KeyStore confianca;

    sealed interface Operacao permits Obter, Avancar, Invalidar {
    }

    record Obter(int pfx) implements Operacao {
    }

    record Avancar(long segundos) implements Operacao {
    }

    record Invalidar(int pfx) implements Operacao {
    }

    @BeforeContainer
    static void gerar() throws Exception {
        char[] senha = "senha-sintetica".toCharArray();
        byte[] pfx = GeradorPfxDeTeste.moderno(senha, Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2027-01-01T00:00:00Z"));
        certificado = new CarregadorPfx().carregar(new Credencial(pfx, senha));
        confianca = KeyStore.getInstance("PKCS12");
        confianca.load(null, null);
    }

    @Property(tries = 300)
    void tamanhoNuncaPassaDaCapacidadeENuncaDevolveEntradaExpirada(
            @ForAll @IntRange(min = 1, max = 4) int capacidade,
            @ForAll("operacoes") List<Operacao> operacoes) {
        RelogioManual relogio = new RelogioManual(Instant.parse("2026-10-27T08:00:00Z"));
        Map<SSLContext, Instant> criadoEm = new IdentityHashMap<>();
        CarregadorPfx carregador = new CarregadorPfx() {
            @Override
            public CertificadoCarregado carregar(Credencial credencial) {
                return certificado;
            }
        };
        FabricaSslContext fabrica = new FabricaSslContext() {
            @Override
            public SSLContext criar(CertificadoCarregado c, KeyStore k) {
                try {
                    SSLContext contexto = SSLContext.getInstance("TLS");
                    criadoEm.put(contexto, relogio.instant());
                    return contexto;
                } catch (NoSuchAlgorithmException e) {
                    throw new IllegalStateException(e);
                }
            }
        };
        CacheSslContext cache = new CacheSslContext(carregador, fabrica, confianca, relogio, capacidade, TTL);

        for (Operacao operacao : operacoes) {
            switch (operacao) {
                case Obter o -> {
                    SSLContext contexto = cache.obter(new Credencial(pfx(o.pfx()), "x".toCharArray()));
                    Duration idade = Duration.between(criadoEm.get(contexto), relogio.instant());
                    assertThat(idade).isLessThan(TTL);
                }
                case Avancar a -> relogio.avancar(Duration.ofSeconds(a.segundos()));
                case Invalidar i -> cache.invalidar(Sha256.hex(pfx(i.pfx())));
            }
            assertThat(cache.tamanho()).isLessThanOrEqualTo(capacidade);
        }
    }

    @Provide
    Arbitrary<List<Operacao>> operacoes() {
        Arbitrary<Integer> indice = Arbitraries.integers().between(0, QTD_PFX - 1);
        Arbitrary<Operacao> obter = indice.map(Obter::new);
        long ttl = TTL.toSeconds();
        Arbitrary<Long> segundos = Arbitraries.oneOf(
                Arbitraries.longs().between(0, ttl * 2),
                Arbitraries.of(1L, ttl - 1, ttl, ttl + 1)); // bordas do TTL
        Arbitrary<Operacao> avancar = segundos.map(Avancar::new);
        Arbitrary<Operacao> invalidar = indice.map(Invalidar::new);
        return Arbitraries.frequencyOf(
                        Tuple.of(6, obter),
                        Tuple.of(3, avancar),
                        Tuple.of(1, invalidar))
                .list().ofMaxSize(60);
    }

    /** Bytes sintéticos distintos: o cache só usa o hash; o carregador do teste os ignora. */
    private static byte[] pfx(int indice) {
        return new byte[] {(byte) (indice + 1)};
    }
}
