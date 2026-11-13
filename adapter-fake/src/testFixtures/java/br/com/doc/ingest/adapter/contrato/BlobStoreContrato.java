package br.com.doc.ingest.adapter.contrato;

import br.com.doc.ingest.domain.blob.BlobConflitanteException;
import br.com.doc.ingest.domain.blob.BlobRef;
import br.com.doc.ingest.domain.blob.BlobStore;
import br.com.doc.ingest.domain.blob.Sha256;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Suíte abstrata de contrato da porta {@link BlobStore}. Cada teste usa uma chave própria, para que
 * implementações com armazenamento compartilhado (S3) possam rodar a suíte sem limpar o bucket.
 */
public abstract class BlobStoreContrato {

    protected abstract BlobStore novoBlobStore();

    private static String chaveNova() {
        return "raw/pull/t1/12345678000199/2026/10/23/" + UUID.randomUUID() + ".envelope.xml";
    }

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void gravarDepoisLerDevolveMesmosBytes() {
        BlobStore store = novoBlobStore();
        String chave = chaveNova();
        byte[] conteudo = bytes("conteudo");
        String sha = Sha256.hex(conteudo);

        BlobRef ref = store.gravar(chave, conteudo, sha);

        assertThat(ref).isEqualTo(new BlobRef(chave, sha));
        assertThat(store.ler(ref)).hasValue(conteudo);
    }

    @Test
    void lerChaveInexistenteDevolveVazio() {
        BlobStore store = novoBlobStore();

        assertThat(store.ler(new BlobRef(chaveNova(), Sha256.hex(new byte[0])))).isEmpty();
    }

    @Test
    void lerComShaDiferenteDoGravadoDevolveVazio() {
        BlobStore store = novoBlobStore();
        String chave = chaveNova();
        byte[] conteudo = bytes("a");
        store.gravar(chave, conteudo, Sha256.hex(conteudo));

        assertThat(store.ler(new BlobRef(chave, Sha256.hex(bytes("b"))))).isEmpty();
    }

    @Test
    void gravarDuasVezesMesmoConteudoEhIdempotente() {
        BlobStore store = novoBlobStore();
        String chave = chaveNova();
        byte[] conteudo = bytes("conteudo");
        String sha = Sha256.hex(conteudo);

        BlobRef primeira = store.gravar(chave, conteudo, sha);
        BlobRef segunda = store.gravar(chave, conteudo.clone(), sha);

        assertThat(segunda).isEqualTo(primeira);
        assertThat(store.ler(primeira)).hasValue(conteudo);
    }

    @Test
    void mesmaChaveConteudoDiferenteRejeitada() {
        BlobStore store = novoBlobStore();
        String chave = chaveNova();
        byte[] original = bytes("original");
        byte[] outro = bytes("outro");
        String shaOriginal = Sha256.hex(original);
        String shaOutro = Sha256.hex(outro);
        store.gravar(chave, original, shaOriginal);

        assertThatThrownBy(() -> store.gravar(chave, outro, shaOutro))
                .isInstanceOf(BlobConflitanteException.class)
                .hasMessageContaining(chave)
                .hasMessageContaining(shaOriginal)
                .hasMessageContaining(shaOutro)
                .hasMessageNotContaining("original")
                .hasMessageNotContaining("outro");
        assertThat(store.ler(new BlobRef(chave, shaOriginal))).hasValue(original);
    }

    @Test
    void shaInformadoQueNaoBateComBytesRejeitadoENadaGravado() {
        BlobStore store = novoBlobStore();
        String chave = chaveNova();
        byte[] conteudo = bytes("conteudo");
        String shaErrado = Sha256.hex(bytes("outra coisa"));

        assertThatThrownBy(() -> store.gravar(chave, conteudo, shaErrado))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(store.ler(new BlobRef(chave, shaErrado))).isEmpty();
        assertThat(store.ler(new BlobRef(chave, Sha256.hex(conteudo)))).isEmpty();
    }

    @Test
    void alterarArrayDeEntradaOuSaidaNaoMudaOBlob() {
        BlobStore store = novoBlobStore();
        byte[] entrada = bytes("conteudo");
        byte[] esperado = entrada.clone();
        BlobRef ref = store.gravar(chaveNova(), entrada, Sha256.hex(entrada));

        entrada[0] = 'X';
        byte[] lido = store.ler(ref).orElseThrow();
        assertThat(lido).isEqualTo(esperado);

        lido[0] = 'Y';
        assertThat(store.ler(ref)).hasValue(esperado);
    }

    @Test
    void conteudoVazioEhGravavel() {
        BlobStore store = novoBlobStore();
        byte[] vazio = new byte[0];

        BlobRef ref = store.gravar(chaveNova(), vazio, Sha256.hex(vazio));

        assertThat(store.ler(ref)).hasValueSatisfying(b -> assertThat(b).isEmpty());
    }

    @Test
    void chavesDoLayoutPullSaoAceitas() {
        BlobStore store = novoBlobStore();
        String id = UUID.randomUUID().toString();
        byte[] conteudo = bytes("x" + id);
        String sha = Sha256.hex(conteudo);
        List<String> chaves = List.of(
                "raw/pull/nfe-an/PRODUCAO/12345678000199/2026/10/23/" + sha + ".envelope.xml",
                "raw/pull/nfe-an/PRODUCAO/12345678000199/nsu/000000000000042-" + sha + ".xml",
                "raw/pull/nfe-an/HOMOLOGACAO/12345678000199/evento/2026/10/23/" + sha + ".retorno.xml");

        for (String chave : chaves) {
            String chaveUnica = id + "/" + chave;
            BlobRef ref = store.gravar(chaveUnica, conteudo, sha);
            assertThat(store.ler(ref)).hasValue(conteudo);
        }
    }

    @Test
    void gravacoesConcorrentesNaMesmaChaveSoUmaVence() throws Exception {
        BlobStore store = novoBlobStore();
        String chave = chaveNova();
        int n = 8;
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch largada = new CountDownLatch(1);
        List<byte[]> conteudos = new ArrayList<>();
        List<Future<BlobRef>> futuros = new ArrayList<>();
        try {
            for (int i = 0; i < n; i++) {
                byte[] c = bytes("conteudo-" + i);
                conteudos.add(c);
                futuros.add(pool.submit(() -> {
                    largada.await();
                    return store.gravar(chave, c, Sha256.hex(c));
                }));
            }
            largada.countDown();

            int conflitos = 0;
            BlobRef vencedora = null;
            byte[] conteudoVencedor = null;
            for (int i = 0; i < n; i++) {
                try {
                    BlobRef ref = futuros.get(i).get();
                    assertThat(vencedora).as("só uma gravação pode vencer").isNull();
                    vencedora = ref;
                    conteudoVencedor = conteudos.get(i);
                } catch (ExecutionException e) {
                    assertThat(e.getCause()).isInstanceOf(BlobConflitanteException.class);
                    conflitos++;
                }
            }

            assertThat(vencedora).isNotNull();
            assertThat(conflitos).isEqualTo(n - 1);
            assertThat(store.ler(vencedora)).hasValue(conteudoVencedor);
        } finally {
            pool.shutdownNow();
        }
    }
}
