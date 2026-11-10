package br.com.doc.ingest.certvault;

import br.com.doc.ingest.adapter.fake.BlobStoreEmMemoria;
import br.com.doc.ingest.certvault.GeradorCertificadoDeTeste.CertificadoTitular;
import br.com.doc.ingest.domain.adapter.Credencial;
import br.com.doc.ingest.domain.blob.BlobRef;
import br.com.doc.ingest.domain.blob.BlobStore;
import br.com.doc.ingest.domain.cofre.CertificadoGuardado;
import br.com.doc.ingest.domain.cofre.FalhaAbertura;
import br.com.doc.ingest.domain.cofre.MotivoRecusa;
import br.com.doc.ingest.domain.cofre.PedidoGuarda;
import br.com.doc.ingest.domain.cofre.ResultadoAbertura;
import br.com.doc.ingest.domain.cofre.ResultadoGuarda;
import br.com.doc.ingest.domain.model.Cnpj;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/** Invariante 13: nenhum segredo sai do cofre — nem no que é guardado, nem em mensagem, toString ou disco. */
@DisplayName("CofreLocal não deixa segredo escapar")
class CofreSemSegredoTest {

    private static final String CNPJ = "11222333000181";
    private static final Instant DE = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant ATE = Instant.parse("2027-01-01T00:00:00Z");
    private static final Instant AGORA = Instant.parse("2026-06-01T12:00:00Z");
    private static final String SENHA_TEXTO = "s3nh@-sintetica-do-cofre";
    private static final long TENANT = 7;
    private static final long TITULAR = 42;
    /** Trechos do .pfx procurados nos blobs; 16 bytes aleatórios coincidirem por acaso é desprezível. */
    private static final int TAMANHO_TRECHO = 16;

    private final GeradorCertificadoDeTeste gerador = new GeradorCertificadoDeTeste(DE, ATE);
    private final RelogioManual relogio = new RelogioManual(AGORA);
    private final ValidadorPfx validador = new ValidadorPfx(
            new ConfiancaIcp(Set.of(gerador.raiz()), List.of(gerador.intermediaria())),
            relogio, ValidadorPfx.LIMITE_PADRAO_BYTES);
    private final byte[] chaveKek = chave((byte) 0x5A);
    private final KekLocal keks = new KekLocal("kek-1", Map.of("kek-1", chaveKek));
    private final BlobStoreGravador blobs = new BlobStoreGravador();
    private final RepositorioCertificadosEmMemoria repositorio = new RepositorioCertificadosEmMemoria();
    private final CofreLocal cofre = new CofreLocal(validador, new EnvelopeCifrado(), keks, blobs, repositorio,
            relogio);

    private final CertificadoTitular titular = gerador.titular(CNPJ);
    private final char[] senha = SENHA_TEXTO.toCharArray();
    private final byte[] pfx = gerador.pfxModerno(titular, senha);

    private static byte[] chave(byte valor) {
        byte[] chave = new byte[32];
        Arrays.fill(chave, valor);
        return chave;
    }

    private PedidoGuarda pedido(byte[] bytes, char[] senhaPedido, String cnpj) {
        return new PedidoGuarda(TENANT, TITULAR, new Cnpj(cnpj), bytes, senhaPedido, "operador@escritorio");
    }

    private CertificadoGuardado guardar() {
        ResultadoGuarda resultado = cofre.guardar(pedido(pfx, senha, CNPJ));
        assertThat(resultado).isInstanceOf(ResultadoGuarda.Guardado.class);
        return ((ResultadoGuarda.Guardado) resultado).certificado();
    }

    /** Representações textuais e binárias da senha e do .pfx que não podem aparecer em lugar nenhum. */
    private List<byte[]> segredosBinarios() {
        List<byte[]> segredos = new ArrayList<>();
        segredos.add(SENHA_TEXTO.getBytes(StandardCharsets.UTF_8));
        segredos.add(SENHA_TEXTO.getBytes(StandardCharsets.UTF_16BE));
        segredos.add(SENHA_TEXTO.getBytes(StandardCharsets.UTF_16LE));
        for (int inicio = 0; inicio + TAMANHO_TRECHO <= pfx.length; inicio += TAMANHO_TRECHO) {
            segredos.add(Arrays.copyOfRange(pfx, inicio, inicio + TAMANHO_TRECHO));
        }
        return segredos;
    }

    private List<String> segredosTextuais() {
        return List.of(SENHA_TEXTO,
                Base64.getEncoder().encodeToString(pfx).substring(0, 24),
                HexFormat.of().formatHex(pfx, 0, TAMANHO_TRECHO),
                HexFormat.of().formatHex(chaveKek),
                Base64.getEncoder().encodeToString(chaveKek),
                Arrays.toString(chaveKek));
    }

    private void assertSemSegredo(String texto) {
        for (String segredo : segredosTextuais()) {
            assertThat(texto).doesNotContain(segredo);
        }
    }

    private static boolean contem(byte[] onde, byte[] trecho) {
        outer:
        for (int i = 0; i + trecho.length <= onde.length; i++) {
            for (int j = 0; j < trecho.length; j++) {
                if (onde[i + j] != trecho[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }

    @Test
    @DisplayName("nada guardado (blobs e campos do registro) contém o pfx, trechos dele ou a senha")
    void nadaGuardadoContemPfxOuSenha() {
        CertificadoGuardado c = guardar();

        assertThat(blobs.gravados()).hasSize(2);
        for (byte[] blob : blobs.gravados().values()) {
            for (byte[] segredo : segredosBinarios()) {
                assertThat(contem(blob, segredo)).as("blob contém segredo em claro").isFalse();
            }
        }
        for (String chave : blobs.gravados().keySet()) {
            assertSemSegredo(chave);
        }
        String campos = String.join("|", String.valueOf(c.id()), String.valueOf(c.tenantId()),
                String.valueOf(c.titularEmpresaId()), c.cnpj().toString(), c.fingerprint(),
                c.pfxCifrado().toString(), c.senhaCifrada().toString(), c.validoDe().toString(),
                c.validoAte().toString(), c.enviadoEm().toString(), c.enviadoPor());
        assertSemSegredo(campos);
        assertSemSegredo(c.toString());
    }

    @Test
    @DisplayName("motivos, resultados e toString não mostram segredo")
    void motivosEToStringNaoMostramSegredo() {
        List<Object> objetos = new ArrayList<>();
        for (MotivoRecusa motivo : MotivoRecusa.values()) {
            objetos.add(new ResultadoGuarda.Recusado(motivo));
        }
        for (FalhaAbertura falha : FalhaAbertura.values()) {
            objetos.add(new ResultadoAbertura.Indisponivel(falha));
        }
        // recusas reais, com a senha e o .pfx em jogo
        objetos.add(cofre.guardar(pedido(pfx, "outra-senha".toCharArray(), CNPJ)));
        objetos.add(cofre.guardar(pedido(pfx, senha, "99888777000100")));
        objetos.add(cofre.guardar(pedido(new byte[] {1, 2, 3}, senha, CNPJ)));
        objetos.add(keks.atual());
        objetos.add(pedido(pfx, senha, CNPJ));
        CertificadoGuardado c = guardar();
        objetos.add(new ResultadoGuarda.Guardado(c));
        ResultadoAbertura aberto = cofre.abrir(TENANT, c.id());
        assertThat(aberto).isInstanceOf(ResultadoAbertura.Aberto.class);
        objetos.add(aberto);
        objetos.add(new ResultadoDecifra.Decifrado(SENHA_TEXTO.getBytes(StandardCharsets.UTF_8)));

        for (Object objeto : objetos) {
            assertSemSegredo(objeto.toString());
        }
    }

    @Test
    @DisplayName("falhas lançadas não encadeiam causa e não carregam segredo")
    void falhasNaoEncadeiamCausaComSegredo() {
        List<Throwable> falhas = List.of(
                catchThrowable(() -> new PedidoGuarda(0, TITULAR, new Cnpj(CNPJ), pfx, senha, "operador")),
                catchThrowable(() -> new PedidoGuarda(TENANT, TITULAR, new Cnpj(CNPJ), pfx, senha, " ")),
                catchThrowable(() -> new EnvelopeCifrado().cifrar(pfx, new byte[3], keks.atual(), new byte[0])),
                catchThrowable(() -> new Kek("kek-x", Arrays.copyOf(chaveKek, 31))),
                catchThrowable(() -> new KekLocal("inexistente", Map.of("kek-1", chaveKek))));

        for (Throwable falha : falhas) {
            assertThat(falha).isNotNull();
            assertThat(falha.getCause()).as("causa encadeada em %s", falha.getClass()).isNull();
            assertThat(falha.getSuppressed()).isEmpty();
            assertSemSegredo(String.valueOf(falha.getMessage()));
            assertSemSegredo(falha.toString());
        }
    }

    @Test
    @DisplayName("guardar e abrir não escrevem nada no diretório de trabalho nem no temporário")
    void nadaEhEscritoEmDisco() {
        File trabalho = new File(System.getProperty("user.dir"));
        File temporario = new File(System.getProperty("java.io.tmpdir"));
        Set<String> antesTrabalho = listar(trabalho);
        Set<String> antesTemporario = listar(temporario);

        CertificadoGuardado c = guardar();
        ResultadoAbertura aberto = cofre.abrir(TENANT, c.id());
        assertThat(aberto).isInstanceOf(ResultadoAbertura.Aberto.class);
        ((ResultadoAbertura.Aberto) aberto).credencial().limpar();

        assertThat(listar(trabalho)).isEqualTo(antesTrabalho);
        assertThat(listar(temporario)).isEqualTo(antesTemporario);
    }

    private static Set<String> listar(File diretorio) {
        String[] nomes = diretorio.list();
        return nomes == null ? Set.of() : new TreeSet<>(Arrays.asList(nomes));
    }

    @Test
    @DisplayName("a credencial aberta não aparece no toString de quem a carrega")
    void credencialNaoExpoeSegredo() {
        assertSemSegredo(new Credencial(pfx, senha).toString());
    }

    /** Guarda uma cópia de tudo o que o cofre gravou, para varrer em busca de segredo. */
    private static final class BlobStoreGravador implements BlobStore {
        private final BlobStoreEmMemoria delegado = new BlobStoreEmMemoria();
        private final Map<String, byte[]> gravados = new ConcurrentHashMap<>();

        Map<String, byte[]> gravados() {
            return gravados;
        }

        @Override
        public BlobRef gravar(String chave, byte[] conteudo, String sha256) {
            gravados.put(chave, conteudo.clone());
            return delegado.gravar(chave, conteudo, sha256);
        }

        @Override
        public Optional<byte[]> ler(BlobRef ref) {
            return delegado.ler(ref);
        }
    }
}
