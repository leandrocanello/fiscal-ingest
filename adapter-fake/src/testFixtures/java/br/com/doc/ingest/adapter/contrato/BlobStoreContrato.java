package br.com.doc.ingest.adapter.contrato;

import br.com.doc.ingest.domain.blob.BlobConflitanteException;
import br.com.doc.ingest.domain.blob.BlobRef;
import br.com.doc.ingest.domain.blob.BlobStore;
import br.com.doc.ingest.domain.blob.Sha256;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Suíte de contrato da porta {@code BlobStore} (invariantes 5 e 7): vale para QUALQUER implementação.
 * A subclasse devolve um store novo e vazio (isolado) em {@link #novoBlobStore()}.
 */
public abstract class BlobStoreContrato {

    private static final String CHAVE = "raw/pull/t1/12345678000199/2026/10/23/x.envelope.xml";

    protected abstract BlobStore novoBlobStore();

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void gravarDepoisLerDevolveMesmosBytes() {
        BlobStore store = novoBlobStore();
        byte[] conteudo = bytes("conteudo");
        String sha = Sha256.hex(conteudo);

        BlobRef ref = store.gravar(CHAVE, conteudo, sha);

        assertThat(ref).isEqualTo(new BlobRef(CHAVE, sha));
        assertThat(store.ler(ref)).hasValue(conteudo);
    }

    @Test
    void lerChaveInexistenteDevolveVazio() {
        BlobStore store = novoBlobStore();

        assertThat(store.ler(new BlobRef(CHAVE, Sha256.hex(new byte[0])))).isEmpty();
    }

    @Test
    void lerComShaDiferenteDoGravadoDevolveVazio() {
        BlobStore store = novoBlobStore();
        byte[] conteudo = bytes("a");
        store.gravar(CHAVE, conteudo, Sha256.hex(conteudo));

        assertThat(store.ler(new BlobRef(CHAVE, Sha256.hex(bytes("b"))))).isEmpty();
    }

    @Test
    void gravarDuasVezesMesmoConteudoEhIdempotente() {
        BlobStore store = novoBlobStore();
        byte[] conteudo = bytes("conteudo");
        String sha = Sha256.hex(conteudo);

        BlobRef primeira = store.gravar(CHAVE, conteudo, sha);
        BlobRef segunda = store.gravar(CHAVE, conteudo.clone(), sha);

        assertThat(segunda).isEqualTo(primeira);
        assertThat(store.ler(primeira)).hasValue(conteudo);
    }

    @Test
    void mesmaChaveConteudoDiferenteRejeitada() {
        BlobStore store = novoBlobStore();
        byte[] original = bytes("original");
        byte[] outro = bytes("outro");
        String shaOriginal = Sha256.hex(original);
        String shaOutro = Sha256.hex(outro);
        store.gravar(CHAVE, original, shaOriginal);

        assertThatThrownBy(() -> store.gravar(CHAVE, outro, shaOutro))
                .isInstanceOf(BlobConflitanteException.class)
                .hasMessageContaining(CHAVE)
                .hasMessageContaining(shaOriginal)
                .hasMessageContaining(shaOutro)
                .hasMessageNotContaining("original")
                .hasMessageNotContaining("outro");
        assertThat(store.ler(new BlobRef(CHAVE, shaOriginal))).hasValue(original);
    }

    @Test
    void shaInformadoQueNaoBateComBytesRejeitadoENadaGravado() {
        BlobStore store = novoBlobStore();
        byte[] conteudo = bytes("conteudo");
        String shaErrado = Sha256.hex(bytes("outra coisa"));

        assertThatThrownBy(() -> store.gravar(CHAVE, conteudo, shaErrado))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(store.ler(new BlobRef(CHAVE, shaErrado))).isEmpty();
        assertThat(store.ler(new BlobRef(CHAVE, Sha256.hex(conteudo)))).isEmpty();
    }

    @Test
    void alterarArrayDeEntradaOuSaidaNaoMudaOBlob() {
        BlobStore store = novoBlobStore();
        byte[] entrada = bytes("conteudo");
        byte[] esperado = entrada.clone();
        BlobRef ref = store.gravar(CHAVE, entrada, Sha256.hex(entrada));

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

        BlobRef ref = store.gravar(CHAVE, vazio, Sha256.hex(vazio));

        assertThat(store.ler(ref)).hasValue(vazio);
    }

    @Test
    void chavesDoLayoutPullSaoAceitas() {
        BlobStore store = novoBlobStore();
        byte[] conteudo = bytes("<x/>");
        String sha = Sha256.hex(conteudo);
        List<String> chaves = List.of(
                "raw/pull/NFE_AN/PRODUCAO/12345678000199/2026/10/23/" + sha + ".envelope.xml",
                "raw/pull/NFE_AN/HOMOLOGACAO/12345678000199/nsu/000000000000042-" + sha + ".xml");

        for (String chave : chaves) {
            BlobRef ref = store.gravar(chave, conteudo, sha);
            assertThat(store.ler(ref)).hasValue(conteudo);
        }
    }

    @Test
    void gravacoesConcorrentesNaMesmaChaveSoUmaVence() throws Exception {
        BlobStore store = novoBlobStore();
        int n = 8;
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch largada = new CountDownLatch(1);
        List<Future<Object>> futuros = new ArrayList<>();
        List<byte[]> conteudos = new ArrayList<>();
        try {
            for (int i = 0; i < n; i++) {
                byte[] c = bytes("conteudo-" + i);
                conteudos.add(c);
                futuros.add(pool.submit(() -> {
                    largada.await();
                    try {
                        return store.gravar(CHAVE, c, Sha256.hex(c));
                    } catch (BlobConflitanteException e) {
                        return e;
                    }
                }));
            }
            largada.countDown();
            List<BlobRef> vencedoras = new ArrayList<>();
            int conflitos = 0;
            for (Future<Object> f : futuros) {
                Object r = f.get();
                if (r instanceof BlobRef ref) {
                    vencedoras.add(ref);
                } else if (r instanceof BlobConflitanteException) {
                    conflitos++;
                }
            }
            assertThat(vencedoras).hasSize(1);
            assertThat(conflitos).isEqualTo(n - 1);
            byte[] lido = store.ler(vencedoras.get(0)).orElseThrow();
            assertThat(Sha256.hex(lido)).isEqualTo(vencedoras.get(0).sha256());
        } finally {
            pool.shutdownNow();
        }
    }
}
