package br.com.doc.ingest.certvault;

import br.com.doc.ingest.adapter.fake.BlobStoreEmMemoria;
import br.com.doc.ingest.adapter.nfe.cert.CarregadorPfx;
import br.com.doc.ingest.adapter.nfe.cert.CertificadoCarregado;
import br.com.doc.ingest.certvault.GeradorCertificadoDeTeste.CertificadoTitular;
import br.com.doc.ingest.domain.adapter.Credencial;
import br.com.doc.ingest.domain.blob.BlobRef;
import br.com.doc.ingest.domain.blob.BlobStore;
import br.com.doc.ingest.domain.blob.Sha256;
import br.com.doc.ingest.domain.cofre.CertificadoGuardado;
import br.com.doc.ingest.domain.cofre.FalhaAbertura;
import br.com.doc.ingest.domain.cofre.NovoCertificado;
import br.com.doc.ingest.domain.cofre.PedidoGuarda;
import br.com.doc.ingest.domain.cofre.RepositorioCertificados;
import br.com.doc.ingest.domain.cofre.ResultadoAbertura;
import br.com.doc.ingest.domain.cofre.ResultadoAbertura.Aberto;
import br.com.doc.ingest.domain.cofre.ResultadoAbertura.Indisponivel;
import br.com.doc.ingest.domain.cofre.ResultadoAbertura.NaoEncontrado;
import br.com.doc.ingest.domain.cofre.ResultadoAbertura.Vencido;
import br.com.doc.ingest.domain.cofre.ResultadoGuarda;
import br.com.doc.ingest.domain.model.Cnpj;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CofreLocal.abrir")
class CofreLocalAbrirTest {

    private static final String CNPJ = "11222333000181";
    private static final String OUTRO_CNPJ = "99888777000166";
    private static final Instant DE = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant ATE = Instant.parse("2027-01-01T00:00:00Z");
    private static final Instant AGORA = Instant.parse("2026-06-01T12:00:00Z");
    private static final char[] SENHA = "s3nh@-sintetica".toCharArray();
    private static final long TENANT = 7;
    private static final long TITULAR = 42;
    private static final long OUTRO_TITULAR = 43;

    private final GeradorCertificadoDeTeste gerador = new GeradorCertificadoDeTeste(DE, ATE);
    private final RelogioManual relogio = new RelogioManual(AGORA);
    private final ValidadorPfx validador = new ValidadorPfx(
            new ConfiancaIcp(Set.of(gerador.raiz()), List.of(gerador.intermediaria())),
            relogio, ValidadorPfx.LIMITE_PADRAO_BYTES);
    private final EnvelopeCifrado envelope = new EnvelopeCifrado();
    private final KekLocal keks = new KekLocal("kek-1", Map.of("kek-1", chave((byte) 1)));
    private final BlobStoreContador blobs = new BlobStoreContador(new BlobStoreEmMemoria());
    private final RepositorioAdulteravel repositorio = new RepositorioAdulteravel();
    private final CofreLocal cofre = cofre(keks);

    private final CertificadoTitular titular = gerador.titular(CNPJ);

    private static byte[] chave(byte valor) {
        byte[] chave = new byte[32];
        Arrays.fill(chave, valor);
        return chave;
    }

    private CofreLocal cofre(ProvedorKek provedor) {
        return new CofreLocal(validador, envelope, provedor, blobs, repositorio, relogio);
    }

    private CertificadoGuardado guardar(long titularId, String cnpj, byte[] pfx, char[] senha) {
        ResultadoGuarda resultado = cofre.guardar(
                new PedidoGuarda(TENANT, titularId, new Cnpj(cnpj), pfx.clone(), senha.clone(), "operador"));
        assertThat(resultado).isInstanceOf(ResultadoGuarda.Guardado.class);
        return ((ResultadoGuarda.Guardado) resultado).certificado();
    }

    private CertificadoGuardado guardar(byte[] pfx, char[] senha) {
        return guardar(TITULAR, CNPJ, pfx, senha);
    }

    private static Credencial credencial(ResultadoAbertura resultado) {
        assertThat(resultado).isInstanceOf(Aberto.class);
        return ((Aberto) resultado).credencial();
    }

    @Test
    @DisplayName("abre e devolve o mesmo pfx e a mesma senha, inclusive vazia e não ASCII")
    void abreEDevolveMesmoPfxESenha() {
        for (char[] senha : List.of(SENHA, new char[0])) {
            byte[] pfx = gerador.pfxLegado(gerador.titular(CNPJ), senha);
            CertificadoGuardado c = guardar(pfx, senha);

            Credencial credencial = credencial(cofre.abrir(TENANT, c.id()));

            assertThat(credencial.pfx()).isEqualTo(pfx);
            assertThat(credencial.senha()).isEqualTo(senha);
        }

        // O KeyStore PKCS12 do JDK recusa senha não ASCII, então o validador não guarda esse .pfx:
        // o envelope é gravado aqui, no formato do cofre, só para provar a volta UTF-8 → char[] (DA-49).
        char[] naoAscii = "çãé-ß-€-日本-😀".toCharArray();
        byte[] pfx = gerador.pfxModerno(titular, SENHA);
        CertificadoGuardado c = registrarSemValidar(pfx, new String(naoAscii).getBytes(StandardCharsets.UTF_8));

        Credencial credencial = credencial(cofre.abrir(TENANT, c.id()));

        assertThat(credencial.pfx()).isEqualTo(pfx);
        assertThat(credencial.senha()).isEqualTo(naoAscii);
    }

    @Test
    @DisplayName("senha decifrada que não é UTF-8 válido fica Indisponivel")
    void senhaQueNaoEhUtf8FicaIndisponivel() {
        CertificadoGuardado c = registrarSemValidar(gerador.pfxModerno(titular, SENHA), new byte[] {(byte) 0xC3});

        assertThat(cofre.abrir(TENANT, c.id())).isEqualTo(new Indisponivel(FalhaAbertura.ENVELOPE_INVALIDO));
    }

    /** Grava os envelopes no formato do cofre (AAD {@code tenant|titular|fingerprint|parte}) sem o validador. */
    private CertificadoGuardado registrarSemValidar(byte[] pfx, byte[] senhaUtf8) {
        String fingerprint = Sha256.hex(pfx);
        byte[] dek = envelope.novaDek();
        byte[] pfxCifrado = envelope.cifrar(pfx, dek, keks.atual(), aad(fingerprint, "PFX"));
        byte[] senhaCifrada = envelope.cifrar(senhaUtf8, dek, keks.atual(), aad(fingerprint, "SENHA"));
        return repositorio.registrarSeAusente(new NovoCertificado(TENANT, TITULAR, new Cnpj(CNPJ), fingerprint,
                blobs.gravar("teste/pfx-" + Sha256.hex(pfxCifrado), pfxCifrado, Sha256.hex(pfxCifrado)),
                blobs.gravar("teste/senha-" + Sha256.hex(senhaCifrada), senhaCifrada, Sha256.hex(senhaCifrada)),
                DE, ATE, AGORA, "operador"));
    }

    private static byte[] aad(String fingerprint, String parte) {
        return (TENANT + "|" + TITULAR + "|" + fingerprint + "|" + parte).getBytes(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("a credencial aberta carrega no CarregadorPfx do adapter com o mesmo fingerprint")
    void credencialAbreNoCarregadorDoAdapterComOMesmoFingerprint() {
        for (byte[] pfx : List.of(gerador.pfxModerno(titular, SENHA), gerador.pfxLegado(titular, SENHA))) {
            CertificadoGuardado c = guardar(pfx, SENHA);

            CertificadoCarregado carregado = new CarregadorPfx().carregar(credencial(cofre.abrir(TENANT, c.id())));

            assertThat(carregado.fingerprint()).isEqualTo(c.fingerprint()).isEqualTo(Sha256.hex(pfx));
            assertThat(carregado.cadeia()[0]).isEqualTo(titular.certificado());
        }
    }

    @Test
    @DisplayName("outro tenant recebe NaoEncontrado")
    void outroTenantRecebeNaoEncontrado() {
        CertificadoGuardado c = guardar(gerador.pfxModerno(titular, SENHA), SENHA);

        assertThat(cofre.abrir(TENANT + 1, c.id())).isEqualTo(new NaoEncontrado());
    }

    @Test
    @DisplayName("id inexistente recebe NaoEncontrado")
    void idInexistenteRecebeNaoEncontrado() {
        assertThat(cofre.abrir(TENANT, 999)).isEqualTo(new NaoEncontrado());
    }

    @Test
    @DisplayName("certificado vencido devolve Vencido sem ler o blob store")
    void vencidoNaoDecifra() {
        CertificadoGuardado c = guardar(gerador.pfxModerno(titular, SENHA), SENHA);
        relogio.avancar(Duration.between(AGORA, c.validoAte()).plusSeconds(1));
        int lidasAntes = blobs.lidas();

        assertThat(cofre.abrir(TENANT, c.id())).isEqualTo(new Vencido(c.validoAte()));
        assertThat(blobs.lidas()).isEqualTo(lidasAntes);
    }

    @Test
    @DisplayName("no último instante da validade ainda abre")
    void noLimiteDaValidadeAindaAbre() {
        CertificadoGuardado c = guardar(gerador.pfxModerno(titular, SENHA), SENHA);
        relogio.avancar(Duration.between(AGORA, c.validoAte()));

        assertThat(cofre.abrir(TENANT, c.id())).isInstanceOf(Aberto.class);
    }

    @Test
    @DisplayName("blob ausente deixa o certificado Indisponivel")
    void blobAusenteFicaIndisponivel() {
        CertificadoGuardado c = guardar(gerador.pfxModerno(titular, SENHA), SENHA);
        repositorio.adulterar(comBlobs(c, new BlobRef("cofre/7/nao-existe/pfx-x.env", Sha256.hex(new byte[] {1})),
                c.senhaCifrada()));

        assertThat(cofre.abrir(TENANT, c.id())).isEqualTo(new Indisponivel(FalhaAbertura.BLOB_AUSENTE));
    }

    @Test
    @DisplayName("blob de outro certificado não decifra: a AAD amarra tenant, titular, fingerprint e parte")
    void blobTrocadoEntreCertificadosFicaIndisponivel() {
        CertificadoGuardado a = guardar(gerador.pfxModerno(titular, SENHA), SENHA);
        CertificadoGuardado b = guardar(OUTRO_TITULAR, OUTRO_CNPJ,
                gerador.pfxModerno(gerador.titular(OUTRO_CNPJ), SENHA), SENHA);
        repositorio.adulterar(comBlobs(a, b.pfxCifrado(), b.senhaCifrada()));

        assertThat(cofre.abrir(TENANT, a.id())).isEqualTo(new Indisponivel(FalhaAbertura.ENVELOPE_INVALIDO));
    }

    @Test
    @DisplayName("pfx e senha trocados entre si não decifram")
    void pfxESenhaTrocadosFicaIndisponivel() {
        CertificadoGuardado c = guardar(gerador.pfxModerno(titular, SENHA), SENHA);
        repositorio.adulterar(comBlobs(c, c.senhaCifrada(), c.pfxCifrado()));

        assertThat(cofre.abrir(TENANT, c.id())).isEqualTo(new Indisponivel(FalhaAbertura.ENVELOPE_INVALIDO));
    }

    @Test
    @DisplayName("envelope cifrado com KEK que o provedor não conhece fica Indisponivel")
    void kekDesconhecidaFicaIndisponivel() {
        CertificadoGuardado c = guardar(gerador.pfxModerno(titular, SENHA), SENHA);
        CofreLocal semAKek = cofre(new KekLocal("kek-2", Map.of("kek-2", chave((byte) 2))));

        assertThat(semAKek.abrir(TENANT, c.id())).isEqualTo(new Indisponivel(FalhaAbertura.KEK_DESCONHECIDA));
    }

    @Test
    @DisplayName("com uma KEK nova como atual, o certificado guardado com a antiga ainda abre")
    void aposKekNovaAindaAbreOAntigo() {
        byte[] pfx = gerador.pfxModerno(titular, SENHA);
        CertificadoGuardado c = guardar(pfx, SENHA);
        CofreLocal comKekNova = cofre(new KekLocal("kek-2",
                Map.of("kek-1", chave((byte) 1), "kek-2", chave((byte) 2))));

        assertThat(credencial(comKekNova.abrir(TENANT, c.id())).pfx()).isEqualTo(pfx);
    }

    @Test
    @DisplayName("limpar a credencial de uma abertura não afeta outra abertura")
    void limparNaCredencialNaoAfetaOutraAbertura() {
        byte[] pfx = gerador.pfxModerno(titular, SENHA);
        CertificadoGuardado c = guardar(pfx, SENHA);

        Credencial primeira = credencial(cofre.abrir(TENANT, c.id()));
        primeira.limpar();
        Credencial segunda = credencial(cofre.abrir(TENANT, c.id()));

        assertThat(segunda.pfx()).isEqualTo(pfx);
        assertThat(segunda.senha()).isEqualTo(SENHA);
    }

    private static CertificadoGuardado comBlobs(CertificadoGuardado c, BlobRef pfx, BlobRef senha) {
        return new CertificadoGuardado(c.id(), c.tenantId(), c.titularEmpresaId(), c.cnpj(), c.fingerprint(),
                pfx, senha, c.validoDe(), c.validoAte(), c.enviadoEm(), c.enviadoPor());
    }

    /** Repositório em memória que permite trocar um registro, para simular blobs trocados no banco. */
    private static final class RepositorioAdulteravel implements RepositorioCertificados {
        private final RepositorioCertificadosEmMemoria delegado = new RepositorioCertificadosEmMemoria();
        private final Map<Long, CertificadoGuardado> adulterados = new ConcurrentHashMap<>();

        void adulterar(CertificadoGuardado c) {
            adulterados.put(c.id(), c);
        }

        @Override
        public CertificadoGuardado registrarSeAusente(NovoCertificado novo) {
            return delegado.registrarSeAusente(novo);
        }

        @Override
        public Optional<CertificadoGuardado> buscar(long tenantId, long certificadoId) {
            CertificadoGuardado adulterado = adulterados.get(certificadoId);
            if (adulterado != null && adulterado.tenantId() == tenantId) {
                return Optional.of(adulterado);
            }
            return delegado.buscar(tenantId, certificadoId);
        }

        @Override
        public java.util.List<CertificadoGuardado> doTitular(long tenantId, long titularEmpresaId) {
            return delegado.doTitular(tenantId, titularEmpresaId);
        }

        @Override
        public java.util.List<CertificadoGuardado> vencendoAte(java.time.Instant limite) {
            return delegado.vencendoAte(limite);
        }
    }

    /** Conta as leituras, para provar que um certificado vencido nem chega ao blob store. */
    private static final class BlobStoreContador implements BlobStore {
        private final BlobStore delegado;
        private final AtomicInteger lidas = new AtomicInteger();

        BlobStoreContador(BlobStore delegado) {
            this.delegado = delegado;
        }

        int lidas() {
            return lidas.get();
        }

        @Override
        public BlobRef gravar(String chave, byte[] conteudo, String sha256) {
            return delegado.gravar(chave, conteudo, sha256);
        }

        @Override
        public Optional<byte[]> ler(BlobRef ref) {
            lidas.incrementAndGet();
            return delegado.ler(ref);
        }
    }
}
