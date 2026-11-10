package br.com.doc.ingest.certvault;

import br.com.doc.ingest.adapter.fake.BlobStoreEmMemoria;
import br.com.doc.ingest.certvault.GeradorCertificadoDeTeste.CertificadoTitular;
import br.com.doc.ingest.domain.blob.BlobRef;
import br.com.doc.ingest.domain.blob.BlobStore;
import br.com.doc.ingest.domain.blob.Sha256;
import br.com.doc.ingest.domain.cofre.CertificadoGuardado;
import br.com.doc.ingest.domain.cofre.MotivoRecusa;
import br.com.doc.ingest.domain.cofre.PedidoGuarda;
import br.com.doc.ingest.domain.cofre.ResultadoGuarda;
import br.com.doc.ingest.domain.cofre.ResultadoGuarda.Guardado;
import br.com.doc.ingest.domain.cofre.ResultadoGuarda.Recusado;
import br.com.doc.ingest.domain.model.Cnpj;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("CofreLocal.guardar")
class CofreLocalGuardarTest {

    private static final String CNPJ = "11222333000181";
    private static final Instant DE = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant ATE = Instant.parse("2027-01-01T00:00:00Z");
    private static final Instant AGORA = Instant.parse("2026-06-01T12:00:00Z");
    private static final char[] SENHA = "s3nh@-sintetica".toCharArray();
    private static final long TENANT = 7;
    private static final long TITULAR = 42;

    private final GeradorCertificadoDeTeste gerador = new GeradorCertificadoDeTeste(DE, ATE);
    private final RelogioManual relogio = new RelogioManual(AGORA);
    private final ValidadorPfx validador = new ValidadorPfx(
            new ConfiancaIcp(Set.of(gerador.raiz()), List.of(gerador.intermediaria())),
            relogio, ValidadorPfx.LIMITE_PADRAO_BYTES);
    private final EnvelopeCifrado envelope = new EnvelopeCifrado();
    private final KekLocal keks = new KekLocal("kek-1", Map.of("kek-1", chave((byte) 1)));
    private final BlobStoreContador blobs = new BlobStoreContador(new BlobStoreEmMemoria());
    private final RepositorioCertificadosEmMemoria repositorio = new RepositorioCertificadosEmMemoria();
    private final CofreLocal cofre = new CofreLocal(validador, envelope, keks, blobs, repositorio, relogio);

    private final CertificadoTitular titular = gerador.titular(CNPJ);
    private final byte[] pfx = gerador.pfxModerno(titular, SENHA);

    private static byte[] chave(byte valor) {
        byte[] chave = new byte[32];
        Arrays.fill(chave, valor);
        return chave;
    }

    private PedidoGuarda pedido(long tenant, byte[] bytes) {
        return new PedidoGuarda(tenant, TITULAR, new Cnpj(CNPJ), bytes, SENHA.clone(), "operador@escritorio");
    }

    private static CertificadoGuardado guardado(ResultadoGuarda resultado) {
        assertThat(resultado).isInstanceOf(Guardado.class);
        return ((Guardado) resultado).certificado();
    }

    private static byte[] aad(long tenant, long titular, String fingerprint, String parte) {
        return (tenant + "|" + titular + "|" + fingerprint + "|" + parte).getBytes(StandardCharsets.UTF_8);
    }

    private byte[] decifrar(BlobRef ref, byte[] aad) {
        byte[] cifrado = blobs.ler(ref).orElseThrow();
        ResultadoDecifra resultado = envelope.decifrar(cifrado, aad, keks);
        assertThat(resultado).isInstanceOf(ResultadoDecifra.Decifrado.class);
        return ((ResultadoDecifra.Decifrado) resultado).conteudo();
    }

    @Test
    @DisplayName("guarda o pfx e a senha cifrados e devolve os metadados do certificado")
    void guardaOsDoisCifradosEDevolveOsMetadados() {
        CertificadoGuardado c = guardado(cofre.guardar(pedido(TENANT, pfx)));

        String fingerprint = Sha256.hex(pfx);
        assertThat(c.id()).isPositive();
        assertThat(c.tenantId()).isEqualTo(TENANT);
        assertThat(c.titularEmpresaId()).isEqualTo(TITULAR);
        assertThat(c.cnpj()).isEqualTo(new Cnpj(CNPJ));
        assertThat(c.fingerprint()).isEqualTo(fingerprint);
        assertThat(c.validoDe()).isEqualTo(titular.certificado().getNotBefore().toInstant());
        assertThat(c.validoAte()).isEqualTo(titular.certificado().getNotAfter().toInstant());
        assertThat(c.enviadoEm()).isEqualTo(AGORA);
        assertThat(c.enviadoPor()).isEqualTo("operador@escritorio");
        assertThat(repositorio.buscar(TENANT, c.id())).contains(c);

        assertThat(decifrar(c.pfxCifrado(), aad(TENANT, TITULAR, fingerprint, "PFX"))).isEqualTo(pfx);
        assertThat(decifrar(c.senhaCifrada(), aad(TENANT, TITULAR, fingerprint, "SENHA")))
                .isEqualTo(new String(SENHA).getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("a chave de cada blob segue cofre/{tenant}/{fingerprint}/{parte}-{sha256}.env com o hash do cifrado")
    void chaveDoBlobContemOSha256DoCifrado() {
        CertificadoGuardado c = guardado(cofre.guardar(pedido(TENANT, pfx)));

        String prefixo = "cofre/" + TENANT + "/" + Sha256.hex(pfx) + "/";
        for (var parte : List.of(Map.entry("pfx", c.pfxCifrado()), Map.entry("senha", c.senhaCifrada()))) {
            BlobRef ref = parte.getValue();
            byte[] cifrado = blobs.ler(ref).orElseThrow();
            assertThat(ref.sha256()).isEqualTo(Sha256.hex(cifrado));
            assertThat(ref.chave()).isEqualTo(prefixo + parte.getKey() + "-" + ref.sha256() + ".env");
        }
        assertThat(blobs.chavesGravadas()).containsExactlyInAnyOrder(c.pfxCifrado().chave(), c.senhaCifrada().chave());
    }

    @Test
    @DisplayName("se o blob store falha, nenhum metadado é registrado (grave primeiro)")
    void falhaNoBlobStoreNaoRegistraMetadado() {
        BlobStore quebrado = new BlobStore() {
            @Override
            public BlobRef gravar(String chave, byte[] conteudo, String sha256) {
                throw new IllegalStateException("blob store fora do ar");
            }

            @Override
            public Optional<byte[]> ler(BlobRef ref) {
                return Optional.empty();
            }
        };
        CofreLocal semBlob = new CofreLocal(validador, envelope, keks, quebrado, repositorio, relogio);

        assertThatThrownBy(() -> semBlob.guardar(pedido(TENANT, pfx))).isInstanceOf(IllegalStateException.class);

        assertThat(repositorio.buscar(TENANT, 1)).isEmpty();
        assertThat(guardado(cofre.guardar(pedido(TENANT, pfx))).id()).isEqualTo(1);
    }

    @Test
    @DisplayName("um pfx recusado não grava blob nem registro")
    void recusaNaoGravaNada() {
        ResultadoGuarda senhaErrada = cofre.guardar(
                new PedidoGuarda(TENANT, TITULAR, new Cnpj(CNPJ), pfx, "errada".toCharArray(), "operador"));
        ResultadoGuarda divergente = cofre.guardar(
                new PedidoGuarda(TENANT, TITULAR, new Cnpj("99888777000100"), pfx, SENHA.clone(), "operador"));

        assertThat(senhaErrada).isEqualTo(new Recusado(MotivoRecusa.SENHA_INCORRETA));
        assertThat(divergente).isEqualTo(new Recusado(MotivoRecusa.CNPJ_DIVERGENTE));
        assertThat(blobs.chavesGravadas()).isEmpty();
        assertThat(repositorio.buscar(TENANT, 1)).isEmpty();
    }

    @Test
    @DisplayName("reenviar o mesmo pfx devolve o registro já existente")
    void reenvioDoMesmoPfxNaoCriaOutroRegistro() {
        CertificadoGuardado primeiro = guardado(cofre.guardar(pedido(TENANT, pfx)));
        relogio.avancar(Duration.ofHours(1));

        CertificadoGuardado segundo = guardado(cofre.guardar(pedido(TENANT, pfx.clone())));

        assertThat(segundo).isEqualTo(primeiro);
        assertThat(repositorio.buscar(TENANT, primeiro.id() + 1)).isEmpty();
        assertThat(blobs.ler(segundo.pfxCifrado())).isPresent();
        assertThat(blobs.ler(segundo.senhaCifrada())).isPresent();
    }

    @Test
    @DisplayName("o mesmo pfx em dois tenants gera registros e cifrados distintos")
    void mesmoPfxEmDoisTenantsGeraRegistrosECifradosDistintos() {
        CertificadoGuardado a = guardado(cofre.guardar(pedido(TENANT, pfx)));
        CertificadoGuardado b = guardado(cofre.guardar(pedido(TENANT + 1, pfx)));

        assertThat(b.id()).isNotEqualTo(a.id());
        assertThat(b.tenantId()).isEqualTo(TENANT + 1);
        assertThat(b.pfxCifrado()).isNotEqualTo(a.pfxCifrado());
        assertThat(b.senhaCifrada()).isNotEqualTo(a.senhaCifrada());
        assertThat(b.pfxCifrado().chave()).startsWith("cofre/" + (TENANT + 1) + "/");
        assertThat(repositorio.buscar(TENANT, b.id())).isEmpty();
    }

    @Test
    @DisplayName("reenvios concorrentes do mesmo pfx recebem o mesmo id")
    void reenvioConcorrenteDevolveOMesmoId() throws Exception {
        List<Callable<Long>> tarefas = IntStream.range(0, 16)
                .<Callable<Long>>mapToObj(i -> () -> guardado(cofre.guardar(pedido(TENANT, pfx))).id())
                .toList();

        List<Long> ids = new ArrayList<>();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (Future<Long> f : executor.invokeAll(tarefas)) {
                ids.add(f.get());
            }
        }

        assertThat(ids).hasSize(16).containsOnly(ids.get(0));
        assertThat(repositorio.buscar(TENANT, ids.get(0) + 1)).isEmpty();
    }

    /** Conta as chaves gravadas, para provar o que o cofre escreveu. */
    private static final class BlobStoreContador implements BlobStore {
        private final BlobStore delegado;
        private final List<String> chaves = Collections.synchronizedList(new ArrayList<>());

        BlobStoreContador(BlobStore delegado) {
            this.delegado = delegado;
        }

        List<String> chavesGravadas() {
            return List.copyOf(chaves);
        }

        @Override
        public BlobRef gravar(String chave, byte[] conteudo, String sha256) {
            BlobRef ref = delegado.gravar(chave, conteudo, sha256);
            chaves.add(chave);
            return ref;
        }

        @Override
        public Optional<byte[]> ler(BlobRef ref) {
            return delegado.ler(ref);
        }
    }
}
